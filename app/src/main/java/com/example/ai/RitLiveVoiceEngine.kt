@file:OptIn(PublicPreviewAPI::class)

package com.example.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import com.example.data.local.MemoryCategory
import com.example.data.local.MemoryEntity
import com.example.data.local.VoiceLanguage
import com.example.data.repository.RitRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

enum class RitOrbState {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    DISCONNECTED
}

data class VoiceSessionStatus(
    val orbState: RitOrbState = RitOrbState.IDLE,
    val isCallActive: Boolean = false,
    val isMuted: Boolean = false,
    val activeConversationId: String? = null,
    val statusLine: String = "",
    val lastMemorySavedNotice: String? = null,
    val errorMessage: String? = null
)

class RitLiveVoiceEngine(
    private val context: Context,
    private val repository: RitRepository,
    private val memoryAnalyzer: MemoryAnalyzerService
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _sessionStatus = MutableStateFlow(VoiceSessionStatus())
    val sessionStatus: StateFlow<VoiceSessionStatus> = _sessionStatus.asStateFlow()

    private var liveSession: LiveSession? = null
    private var conversationJob: Job? = null
    private var silenceTimerJob: Job? = null
    private var activeConversationId: String? = null

    private val userTurnBuffer = StringBuilder()
    private val ritTurnBuffer = StringBuilder()
    private val userPcmLock = Any()
    private val ritPcmLock = Any()
    private val userPcmBuffer = ByteArrayOutputStream()
    private val ritPcmBuffer = ByteArrayOutputStream()
    private var storeAudioForActiveCall: Boolean = false
    private var lastTurnSpeaker: String? = null
    private var turnStartedAtMs: Long = 0L
    private var askBeforeSensitive: Boolean = true

    fun clearError() {
        _sessionStatus.value = _sessionStatus.value.copy(errorMessage = null)
    }

    fun clearMemoryNotice() {
        _sessionStatus.value = _sessionStatus.value.copy(lastMemorySavedNotice = null)
    }

    fun startVoiceCall(continueFromConversationId: String? = null) {
        if (_sessionStatus.value.isCallActive || _sessionStatus.value.orbState == RitOrbState.CONNECTING) {
            return
        }

        conversationJob?.cancel()
        conversationJob = engineScope.launch {
            var createdConversationId: String? = null
            try {
                _sessionStatus.value = VoiceSessionStatus(
                    orbState = RitOrbState.CONNECTING,
                    isCallActive = false,
                    isMuted = false,
                    statusLine = "Connecting to Rit..."
                )

                val settings = repository.getSettings()
                askBeforeSensitive = settings.askBeforeSensitiveMemory
                storeAudioForActiveCall = settings.storeAudioRecordings
                clearPcmBuffers()
                val language = VoiceLanguage.fromName(settings.selectedLanguage)
                val user = repository.getCurrentUser()
                val memories = repository.getConfirmedMemories()
                val recentSummaries = repository.getRecentSummaries(5)
                val continuedMessages = if (!continueFromConversationId.isNullOrBlank()) {
                    repository.getMessagesForConversation(continueFromConversationId)
                } else {
                    emptyList()
                }

                val conversation = repository.createConversation(
                    language = language.displayName,
                    voiceName = settings.selectedVoiceName,
                    title = if (continuedMessages.isNotEmpty()) "Continued Voice Chat" else "Voice Chat with Rit"
                )
                createdConversationId = conversation.id
                activeConversationId = conversation.id

                val systemPrompt = RitPersonality.buildSystemPrompt(
                    userName = user?.displayName,
                    language = language,
                    voiceName = settings.selectedVoiceName,
                    memories = memories,
                    recentSummaries = recentSummaries,
                    continuedMessages = continuedMessages,
                    askBeforeSensitiveMemory = settings.askBeforeSensitiveMemory
                )

                val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                    modelName = GeminiVoicePreviewPlayer.LIVE_MODEL_NAME,
                    generationConfig = liveGenerationConfig {
                        responseModality = ResponseModality.AUDIO
                        speechConfig = SpeechConfig(voice = Voice(settings.selectedVoiceName))
                        inputAudioTranscription = AudioTranscriptionConfig()
                        outputAudioTranscription = AudioTranscriptionConfig()
                    },
                    tools = listOf(buildMemoryTools()),
                    systemInstruction = content { text(systemPrompt) }
                )

                val session = withTimeout(25_000L) {
                    liveModel.connect()
                }
                liveSession = session

                // Configure communication audio mode for hardware AEC
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isMicrophoneMute = false

                _sessionStatus.value = VoiceSessionStatus(
                    orbState = RitOrbState.LISTENING,
                    isCallActive = true,
                    isMuted = false,
                    activeConversationId = conversation.id,
                    statusLine = "Listening in ${language.displayName}..."
                )

                if (settings.storeAudioRecordings) {
                    runRecordedLiveConversation(
                        session = session,
                        conversationId = conversation.id,
                        languageLabel = language.displayName,
                        bargeInEnabled = settings.bargeInEnabled
                    )
                } else {
                    val audioConfig = liveAudioConversationConfig {
                        enableInterruptions = settings.bargeInEnabled
                        initializationHandler = { audioRecordBuilder, _ ->
                            audioRecordBuilder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                        }
                        functionCallHandler = { call ->
                            handleLiveFunctionCall(call, conversation.id)
                        }
                        transcriptHandler = { inputTranscript, outputTranscript ->
                            handleTranscriptions(
                                conversationId = conversation.id,
                                inputText = inputTranscript?.text,
                                outputText = outputTranscript?.text,
                                languageLabel = language.displayName
                            )
                        }
                        goAwayHandler = { goAway ->
                            Log.w(TAG, "Gemini Live GoAway received: timeLeft=${goAway.timeLeft}")
                            _sessionStatus.value = _sessionStatus.value.copy(
                                statusLine = "Refreshing live voice session..."
                            )
                        }
                    }

                    // Suspends until stopAudioConversation() or connection close
                    session.startAudioConversation(audioConfig)
                }

                finalizeConversation(conversation.id)
                _sessionStatus.value = VoiceSessionStatus(
                    orbState = RitOrbState.IDLE,
                    isCallActive = false,
                    isMuted = false,
                    statusLine = ""
                )
            } catch (e: CancellationException) {
                createdConversationId?.let { finalizeConversation(it) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Live voice session error", e)
                createdConversationId?.let { finalizeConversation(it) }
                _sessionStatus.value = VoiceSessionStatus(
                    orbState = RitOrbState.DISCONNECTED,
                    isCallActive = false,
                    isMuted = false,
                    statusLine = "Connection paused",
                    errorMessage = mapFriendlyError(e)
                )
            } finally {
                cleanupAudioSession()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runRecordedLiveConversation(
        session: LiveSession,
        conversationId: String,
        languageLabel: String,
        bargeInEnabled: Boolean
    ) {
        val sampleRateIn = 16000
        val sampleRateOut = 24000
        val minInBuf = AudioRecord.getMinBufferSize(
            sampleRateIn,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)
        val minOutBuf = AudioTrack.getMinBufferSize(
            sampleRateOut,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4800)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRateIn,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minInBuf * 2
        )
        val aec = if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply { enabled = true }
            }.getOrNull()
        } else {
            null
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateOut)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minOutBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            audioRecord.startRecording()
            audioTrack.play()

            coroutineScope {
                val micJob = launch(Dispatchers.IO) {
                    val readBuf = ByteArray(minInBuf)
                    while (isActive && !session.isClosed()) {
                        val read = audioRecord.read(readBuf, 0, readBuf.size)
                        if (read > 0) {
                            val chunk = if (_sessionStatus.value.isMuted) {
                                ByteArray(read)
                            } else {
                                readBuf.copyOf(read)
                            }
                            if (!_sessionStatus.value.isMuted) {
                                synchronized(userPcmLock) {
                                    userPcmBuffer.write(chunk)
                                }
                            }
                            runCatching {
                                session.sendAudioRealtime(InlineData(chunk, "audio/pcm;rate=16000"))
                            }.onFailure {
                                if (session.isClosed()) return@launch
                            }
                        }
                    }
                }

                try {
                    session.receive().collect { serverMsg ->
                        when (serverMsg) {
                            is LiveServerContent -> {
                                if (serverMsg.interrupted && bargeInEnabled) {
                                    runCatching {
                                        audioTrack.pause()
                                        audioTrack.flush()
                                        audioTrack.play()
                                    }
                                }
                                serverMsg.content?.parts?.forEach { part ->
                                    if (part is InlineDataPart && part.mimeType.startsWith("audio/pcm")) {
                                        val pcm = part.inlineData
                                        if (pcm.isNotEmpty()) {
                                            synchronized(ritPcmLock) {
                                                ritPcmBuffer.write(pcm)
                                            }
                                            audioTrack.write(pcm, 0, pcm.size)
                                        }
                                    }
                                }
                                handleTranscriptions(
                                    conversationId = conversationId,
                                    inputText = serverMsg.inputTranscription?.text,
                                    outputText = serverMsg.outputTranscription?.text,
                                    languageLabel = languageLabel
                                )
                            }
                            is LiveServerToolCall -> {
                                val responses = serverMsg.functionCalls.map { call ->
                                    handleLiveFunctionCall(call, conversationId)
                                }
                                if (responses.isNotEmpty()) {
                                    session.sendFunctionResponse(responses)
                                }
                            }
                        }
                    }
                } finally {
                    micJob.cancel()
                }
            }
        } finally {
            runCatching { aec?.release() }
            runCatching {
                audioRecord.stop()
                audioRecord.release()
            }
            runCatching {
                audioTrack.stop()
                audioTrack.release()
            }
        }
    }

    fun toggleMute() {
        val current = _sessionStatus.value
        if (!current.isCallActive) return
        val nextMuted = !current.isMuted
        audioManager.isMicrophoneMute = nextMuted
        _sessionStatus.value = current.copy(
            isMuted = nextMuted,
            statusLine = if (nextMuted) "Microphone muted" else "Listening..."
        )
    }

    fun endVoiceCall() {
        val convId = activeConversationId
        silenceTimerJob?.cancel()
        runCatching { liveSession?.stopAudioConversation() }
        runCatching { liveSession?.stopReceiving() }
        engineScope.launch {
            runCatching { liveSession?.close() }
            liveSession = null
            cleanupAudioSession()
            if (convId != null) {
                finalizeConversation(convId)
            }
            _sessionStatus.value = VoiceSessionStatus(
                orbState = RitOrbState.IDLE,
                isCallActive = false,
                isMuted = false,
                statusLine = ""
            )
        }
        conversationJob?.cancel()
        conversationJob = null
    }

    private fun cleanupAudioSession() {
        runCatching {
            audioManager.isMicrophoneMute = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun clearPcmBuffers() {
        synchronized(userPcmLock) { userPcmBuffer.reset() }
        synchronized(ritPcmLock) { ritPcmBuffer.reset() }
    }

    private fun handleTranscriptions(
        conversationId: String,
        inputText: String?,
        outputText: String?,
        languageLabel: String
    ) {
        val cleanIn = inputText?.takeIf { it.isNotBlank() }
        val cleanOut = outputText?.takeIf { it.isNotBlank() }

        if (cleanIn != null) {
            if (lastTurnSpeaker == "RIT" && ritTurnBuffer.isNotEmpty()) {
                flushBuffer("RIT", conversationId)
            }
            if (lastTurnSpeaker != "USER") {
                turnStartedAtMs = System.currentTimeMillis()
                lastTurnSpeaker = "USER"
            }
            userTurnBuffer.append(cleanIn)
            _sessionStatus.value = _sessionStatus.value.copy(
                orbState = RitOrbState.LISTENING,
                statusLine = "Listening in $languageLabel..."
            )
            scheduleThinkingTransition(conversationId)
        }

        if (cleanOut != null) {
            silenceTimerJob?.cancel()
            if (lastTurnSpeaker == "USER" && userTurnBuffer.isNotEmpty()) {
                flushBuffer("USER", conversationId)
            }
            if (lastTurnSpeaker != "RIT") {
                turnStartedAtMs = System.currentTimeMillis()
                lastTurnSpeaker = "RIT"
            }
            ritTurnBuffer.append(cleanOut)
            _sessionStatus.value = _sessionStatus.value.copy(
                orbState = RitOrbState.SPEAKING,
                statusLine = "Rit is speaking..."
            )
            scheduleListeningTransition(conversationId)
        }
    }

    private fun scheduleThinkingTransition(conversationId: String) {
        silenceTimerJob?.cancel()
        silenceTimerJob = engineScope.launch {
            delay(900L)
            if (_sessionStatus.value.isCallActive && lastTurnSpeaker == "USER") {
                flushBuffer("USER", conversationId)
                _sessionStatus.value = _sessionStatus.value.copy(
                    orbState = RitOrbState.THINKING,
                    statusLine = "Rit is thinking..."
                )
            }
        }
    }

    private fun scheduleListeningTransition(conversationId: String) {
        silenceTimerJob?.cancel()
        silenceTimerJob = engineScope.launch {
            delay(1400L)
            if (_sessionStatus.value.isCallActive && lastTurnSpeaker == "RIT") {
                flushBuffer("RIT", conversationId)
                _sessionStatus.value = _sessionStatus.value.copy(
                    orbState = if (_sessionStatus.value.isMuted) RitOrbState.IDLE else RitOrbState.LISTENING,
                    statusLine = if (_sessionStatus.value.isMuted) "Microphone muted" else "Listening..."
                )
            }
        }
    }

    private fun flushBuffer(role: String, conversationId: String) {
        val text = if (role == "USER") {
            val s = userTurnBuffer.toString().trim()
            userTurnBuffer.clear()
            s
        } else {
            val s = ritTurnBuffer.toString().trim()
            ritTurnBuffer.clear()
            s
        }
        val audioPath = saveTurnWavIfOptedIn(role, conversationId)
        if (text.isBlank()) {
            audioPath?.let { runCatching { File(it).delete() } }
            return
        }
        val duration = (System.currentTimeMillis() - turnStartedAtMs).coerceAtLeast(0L)
        engineScope.launch {
            repository.appendMessage(
                conversationId = conversationId,
                role = role,
                transcript = text,
                durationMs = duration,
                audioFilePath = audioPath
            )
        }
    }

    private fun saveTurnWavIfOptedIn(role: String, conversationId: String): String? {
        val pcmBytes: ByteArray
        val sampleRate: Int
        if (role == "USER") {
            synchronized(userPcmLock) {
                pcmBytes = userPcmBuffer.toByteArray()
                userPcmBuffer.reset()
            }
            sampleRate = 16000
        } else {
            synchronized(ritPcmLock) {
                pcmBytes = ritPcmBuffer.toByteArray()
                ritPcmBuffer.reset()
            }
            sampleRate = 24000
        }
        if (!storeAudioForActiveCall || pcmBytes.isEmpty()) return null

        return runCatching {
            val dir = File(context.filesDir, "rit_audio/$conversationId")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "${role.lowercase()}_${UUID.randomUUID()}.wav")
            writeWavFile(outFile, pcmBytes, sampleRate)
            outFile.absolutePath
        }.getOrNull()
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmData.size
        val totalChunkLen = 36 + totalDataLen

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalChunkLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM subchunk size
            putShort(1.toShort()) // AudioFormat = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
        }.array()

        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(pcmData)
        }
    }

    private suspend fun finalizeConversation(conversationId: String) {
        if (activeConversationId != conversationId) return
        activeConversationId = null
        silenceTimerJob?.cancel()

        val remainingUser = userTurnBuffer.toString().trim().also { userTurnBuffer.clear() }
        val userAudioPath = saveTurnWavIfOptedIn("USER", conversationId)
        if (remainingUser.isNotBlank()) {
            repository.appendMessage(conversationId, "USER", remainingUser, audioFilePath = userAudioPath)
        } else {
            userAudioPath?.let { runCatching { File(it).delete() } }
        }
        val remainingRit = ritTurnBuffer.toString().trim().also { ritTurnBuffer.clear() }
        val ritAudioPath = saveTurnWavIfOptedIn("RIT", conversationId)
        if (remainingRit.isNotBlank()) {
            repository.appendMessage(conversationId, "RIT", remainingRit, audioFilePath = ritAudioPath)
        } else {
            ritAudioPath?.let { runCatching { File(it).delete() } }
        }
        lastTurnSpeaker = null

        val messages = repository.getMessagesForConversation(conversationId)
        if (messages.isEmpty()) {
            repository.deleteConversation(conversationId)
            return
        }

        val settings = repository.getSettings()
        val existingMemories = repository.getConfirmedMemories().map { it.content }
        val result = memoryAnalyzer.summarizeAndExtractMemories(
            conversationId = conversationId,
            messages = messages,
            existingMemoriesSummary = existingMemories,
            allowAutoExtract = settings.allowAutoMemoryExtraction,
            askBeforeSensitive = settings.askBeforeSensitiveMemory
        )
        repository.finishConversation(
            conversationId = conversationId,
            summary = result?.second,
            generatedTitle = result?.first
        )
    }

    private fun handleLiveFunctionCall(
        call: FunctionCallPart,
        conversationId: String
    ): FunctionResponsePart {
        return when (call.name) {
            "save_memory" -> {
                val contentStr = (call.args["content"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
                val categoryRaw = (call.args["category"] as? JsonPrimitive)?.contentOrNull
                val importance = (call.args["importance"] as? JsonPrimitive)?.intOrNull ?: 3
                val isSensitive = (call.args["is_sensitive"] as? JsonPrimitive)?.booleanOrNull ?: false
                val userConfirmed = (call.args["user_confirmed"] as? JsonPrimitive)?.booleanOrNull ?: true

                if (contentStr.isBlank()) {
                    return FunctionResponsePart(
                        name = call.name,
                        response = buildJsonObject { put("status", "ignored_empty") },
                        id = call.id
                    )
                }

                val confirmedFlag = if (isSensitive && askBeforeSensitive) userConfirmed else true
                runBlocking(Dispatchers.IO) {
                    repository.saveOrUpdateMemory(
                        content = contentStr,
                        category = MemoryCategory.fromRaw(categoryRaw),
                        importance = importance,
                        isSensitive = isSensitive,
                        userConfirmed = confirmedFlag,
                        sourceConversationId = conversationId
                    )
                }

                _sessionStatus.value = _sessionStatus.value.copy(
                    lastMemorySavedNotice = if (confirmedFlag) {
                        "Saved to Rit's Brain: $contentStr"
                    } else {
                        "Pending your approval in Rit's Brain: $contentStr"
                    }
                )

                FunctionResponsePart(
                    name = call.name,
                    response = buildJsonObject {
                        put("status", if (confirmedFlag) "saved" else "awaiting_user_confirmation")
                        put("memory", contentStr)
                    },
                    id = call.id
                )
            }

            "recall_memories" -> {
                val query = (call.args["query"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val recalled: List<MemoryEntity> = runBlocking(Dispatchers.IO) {
                    repository.searchConfirmedMemories(query, limit = 15)
                }
                val itemsArray = JsonArray(recalled.map { mem ->
                    JsonObject(
                        mapOf(
                            "category" to JsonPrimitive(mem.category),
                            "content" to JsonPrimitive(mem.content)
                        )
                    )
                })
                FunctionResponsePart(
                    name = call.name,
                    response = buildJsonObject {
                        put("count", recalled.size)
                        put("memories", itemsArray)
                    },
                    id = call.id
                )
            }

            else -> FunctionResponsePart(
                name = call.name,
                response = buildJsonObject { put("error", "Unknown tool ${call.name}") },
                id = call.id
            )
        }
    }

    private fun buildMemoryTools(): Tool {
        val saveMemoryDecl = FunctionDeclaration(
            name = "save_memory",
            description = "Save a long-term personal memory about the user into Rit's Brain so it persists across sessions and restarts.",
            parameters = mapOf(
                "content" to Schema.string(description = "Clear third-person fact, e.g. 'User's name is Rajan' or 'User loves black coffee'"),
                "category" to Schema.enumeration(
                    values = MemoryCategory.entries.map { it.name },
                    description = "Memory category"
                ),
                "importance" to Schema.integer(description = "Importance 1 (low) to 5 (critical)"),
                "is_sensitive" to Schema.boolean(description = "True if sensitive PII, financial, health, address, or secret"),
                "user_confirmed" to Schema.boolean(description = "True if harmless or user verbally confirmed saving sensitive info")
            ),
            optionalParameters = listOf("importance", "is_sensitive", "user_confirmed")
        )

        val recallMemoriesDecl = FunctionDeclaration(
            name = "recall_memories",
            description = "Search Rit's persistent long-term memory database for facts, preferences, dates, or past projects.",
            parameters = mapOf(
                "query" to Schema.string(description = "Keyword or topic to search in Rit's Brain")
            )
        )

        return Tool.functionDeclarations(listOf(saveMemoryDecl, recallMemoriesDecl))
    }

    private fun mapFriendlyError(e: Exception): String {
        val msg = e.localizedMessage.orEmpty()
        return when {
            msg.contains("403", ignoreCase = true) || msg.contains("AppCheck", ignoreCase = true) ->
                "Voice connection blocked by App Check or quota. Check network & try again."
            msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("network", ignoreCase = true) ->
                "No internet connection. Rit needs an active connection for Gemini Live voice."
            msg.contains("429", ignoreCase = true) || msg.contains("quota", ignoreCase = true) ->
                "Gemini API rate limit reached. Please wait a moment and try again."
            else -> msg.ifBlank { "Voice connection failed. Please try reconnecting." }
        }
    }

    companion object {
        private const val TAG = "RitLiveVoiceEngine"
    }
}
