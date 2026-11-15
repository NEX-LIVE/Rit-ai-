@file:OptIn(PublicPreviewAPI::class)

package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.data.local.VoiceLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Plays authentic Google Gemini native voice previews via Gemini Live API
 * and caches generated PCM audio locally for instant replay.
 */
class GeminiVoicePreviewPlayer(private val context: Context) {

    private val _playingVoiceName = MutableStateFlow<String?>(null)
    val playingVoiceName: StateFlow<String?> = _playingVoiceName.asStateFlow()

    private val _previewError = MutableStateFlow<String?>(null)
    val previewError: StateFlow<String?> = _previewError.asStateFlow()

    private var activeTrack: AudioTrack? = null
    private var activeJob: Job? = null

    fun clearError() {
        _previewError.value = null
    }

    fun stopPreview() {
        activeJob?.cancel()
        activeJob = null
        runCatching {
            activeTrack?.stop()
            activeTrack?.release()
        }
        activeTrack = null
        _playingVoiceName.value = null
    }

    suspend fun playPreview(voiceName: String, language: VoiceLanguage) {
        stopPreview()
        _previewError.value = null
        _playingVoiceName.value = voiceName

        try {
            val pcmBytes = withContext(Dispatchers.IO) {
                val cacheDir = File(context.cacheDir, "gemini_voice_previews").apply { mkdirs() }
                val cacheFile = File(cacheDir, "${voiceName.lowercase()}_${language.name.lowercase()}.pcm")
                if (cacheFile.exists() && cacheFile.length() > 1024L) {
                    cacheFile.readBytes()
                } else {
                    val fetched = fetchGeminiVoiceSample(voiceName, language)
                    if (fetched.isNotEmpty()) {
                        cacheFile.writeBytes(fetched)
                    }
                    fetched
                }
            }

            if (pcmBytes.isEmpty()) {
                _previewError.value = "Voice preview returned no audio for $voiceName."
                _playingVoiceName.value = null
                return
            }

            withContext(Dispatchers.IO) {
                playPcm24k(pcmBytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini voice preview failed for $voiceName", e)
            _previewError.value = e.localizedMessage ?: "Failed to load Gemini preview for $voiceName."
        } finally {
            if (_playingVoiceName.value == voiceName) {
                _playingVoiceName.value = null
            }
        }
    }

    private suspend fun fetchGeminiVoiceSample(
        voiceName: String,
        language: VoiceLanguage
    ): ByteArray {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = LIVE_MODEL_NAME,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice(voiceName))
            },
            systemInstruction = content {
                text("You are Rit, a warm female voice assistant. Speak naturally in ${language.displayName}. Output only one short friendly greeting sentence.")
            }
        )

        val buffer = ByteArrayOutputStream()
        var session: LiveSession? = null
        try {
            withTimeout(25_000L) {
                session = model.connect()
                val prompt = when (language) {
                    VoiceLanguage.HINDI -> "नमस्ते! मैं रित हूँ, आपकी आवाज़ में आपकी दोस्त। बताइए आज क्या बात करनी है?"
                    VoiceLanguage.ENGLISH -> "Hey! I'm Rit, and this is how my voice sounds. Ready whenever you want to talk!"
                    VoiceLanguage.HINGLISH -> "Hey! Main Rit hoon, aur meri awaaz kuch aisi sunai deti hai. Bolo, aaj kya baat karni hai?"
                }
                session!!.send("Please say warmly and naturally: \"$prompt\"", turnComplete = true)
                session!!.receive().collect { message ->
                    currentCoroutineContext().ensureActive()
                    if (message is LiveServerContent) {
                        message.content?.parts.orEmpty().filterIsInstance<InlineDataPart>().forEach { part ->
                            if (part.mimeType.startsWith("audio/")) {
                                buffer.write(part.inlineData)
                            }
                        }
                        if (message.turnComplete) {
                            session?.stopReceiving()
                        }
                    }
                }
            }
        } finally {
            runCatching { session?.close() }
        }
        return buffer.toByteArray()
    }

    private suspend fun playPcm24k(pcmBytes: ByteArray) {
        val sampleRate = 24_000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf * 2, 16_384))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeTrack = track
        try {
            track.play()
            var offset = 0
            val chunkSize = 4096
            while (offset < pcmBytes.size) {
                currentCoroutineContext().ensureActive()
                val toWrite = minOf(chunkSize, pcmBytes.size - offset)
                val written = track.write(pcmBytes, offset, toWrite)
                if (written <= 0) break
                offset += written
            }
            track.stop()
        } finally {
            runCatching { track.release() }
            if (activeTrack === track) activeTrack = null
        }
    }

    companion object {
        private const val TAG = "GeminiVoicePreview"
        const val LIVE_MODEL_NAME = "gemini-2.5-flash-native-audio-preview-12-2025"
    }
}
