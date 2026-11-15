package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppSettingsEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationSummaryEntity
import com.example.data.local.ConversationWithSummary
import com.example.data.local.MemoryCategory
import com.example.data.local.MemoryEntity
import com.example.data.local.MessageEntity
import com.example.data.local.RitDatabase
import com.example.data.local.UserEntity
import com.example.data.local.VoiceLanguage
import com.example.data.local.VoicePreferenceEntity
import com.example.data.remote.CloudSyncRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class RitRepository(
    private val context: Context,
    private val db: RitDatabase,
    private val cloudSync: CloudSyncRepository
) {
    private val userDao = db.userDao()
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val memoryDao = db.memoryDao()
    private val voiceDao = db.voicePreferenceDao()
    private val summaryDao = db.conversationSummaryDao()
    private val settingsDao = db.appSettingsDao()

    val settingsFlow: Flow<AppSettingsEntity> = settingsDao.observeSettings()
        .map { it ?: AppSettingsEntity() }

    val userFlow: Flow<UserEntity?> = userDao.observeCurrentUser()

    val voicesFlow: Flow<List<VoicePreferenceEntity>> = voiceDao.observeAllVoices()

    val memoriesFlow: Flow<List<MemoryEntity>> = memoryDao.observeAllMemories()

    val pendingSensitiveMemoriesFlow: Flow<List<MemoryEntity>> =
        memoryDao.observePendingSensitiveMemories()

    fun searchConversations(
        query: String,
        startMs: Long?,
        endMs: Long?
    ): Flow<List<ConversationWithSummary>> =
        conversationDao.searchConversations(query.trim(), startMs, endMs)

    fun observeConversationWithSummary(conversationId: String): Flow<ConversationWithSummary?> =
        conversationDao.observeConversationWithSummary(conversationId)

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.observeMessages(conversationId)

    suspend fun initializeDefaults() {
        RitDatabase.seedDefaultsIfNeeded(db)
    }

    suspend fun getSettings(): AppSettingsEntity {
        return settingsDao.getSettings() ?: AppSettingsEntity().also { settingsDao.upsert(it) }
    }

    suspend fun getCurrentUser(): UserEntity? = userDao.getCurrentUser()

    suspend fun updateSelectedVoice(voiceName: String) {
        val now = System.currentTimeMillis()
        voiceDao.selectVoice(voiceName, now)
        val current = getSettings()
        val updated = current.copy(selectedVoiceName = voiceName, updatedAt = now)
        settingsDao.upsert(updated)
        syncSettingsIfEnabled(updated)
    }

    suspend fun updateSelectedLanguage(language: VoiceLanguage) {
        val now = System.currentTimeMillis()
        val current = getSettings()
        val updated = current.copy(selectedLanguage = language.displayName, updatedAt = now)
        settingsDao.upsert(updated)
        syncSettingsIfEnabled(updated)
    }

    suspend fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        val now = System.currentTimeMillis()
        val current = getSettings()
        val updated = transform(current).copy(updatedAt = now)
        settingsDao.upsert(updated)
        syncSettingsIfEnabled(updated)
    }

    suspend fun onGoogleUserSignedIn(firebaseUser: FirebaseUser) {
        val now = System.currentTimeMillis()
        val existing = userDao.getCurrentUser()
        val displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
            ?: existing?.displayName
            ?: "Friend"
        userDao.upsertUser(
            UserEntity(
                id = existing?.id ?: "local_user",
                displayName = displayName,
                email = firebaseUser.email,
                firebaseUid = firebaseUser.uid,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        // Automatically enable cloud backup & attempt restore if user signs in
        val updatedSettings = getSettings().copy(cloudBackupEnabled = true, updatedAt = now)
        settingsDao.upsert(updatedSettings)
        runCatching { restoreFromCloud() }
            .onFailure { Log.w(TAG, "Cloud restore skipped or failed on sign-in: ${it.message}") }
        runCatching { syncAllToCloud() }
            .onFailure { Log.w(TAG, "Initial cloud sync warning: ${it.message}") }
    }

    suspend fun onGoogleUserSignedOut() {
        val now = System.currentTimeMillis()
        val existing = userDao.getCurrentUser()
        if (existing != null) {
            userDao.upsertUser(
                existing.copy(firebaseUid = null, email = null, updatedAt = now)
            )
        }
        val current = getSettings()
        settingsDao.upsert(current.copy(cloudBackupEnabled = false, updatedAt = now))
    }

    suspend fun createConversation(language: String, voiceName: String, title: String): ConversationEntity {
        val now = System.currentTimeMillis()
        val user = getCurrentUser()
        val conv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            userId = user?.firebaseUid ?: user?.id ?: "local_user",
            title = title,
            startedAt = now,
            endedAt = null,
            language = language,
            voiceName = voiceName,
            messageCount = 0,
            isSynced = false,
            updatedAt = now
        )
        conversationDao.upsert(conv)
        return conv
    }

    suspend fun appendMessage(
        conversationId: String,
        role: String,
        transcript: String,
        durationMs: Long = 0L,
        audioFilePath: String? = null
    ) {
        val cleanTranscript = transcript.trim()
        if (cleanTranscript.isBlank()) return
        val now = System.currentTimeMillis()
        val msg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            transcript = cleanTranscript,
            timestamp = now,
            durationMs = durationMs,
            audioFilePath = audioFilePath,
            isSynced = false
        )
        messageDao.insert(msg)
        val conv = conversationDao.getById(conversationId)
        if (conv != null) {
            val nextTitle = if (conv.messageCount == 0 && role == "USER") {
                cleanTranscript.take(48).trim().ifBlank { conv.title }
            } else {
                conv.title
            }
            conversationDao.update(
                conv.copy(
                    title = nextTitle,
                    messageCount = conv.messageCount + 1,
                    updatedAt = now,
                    isSynced = false
                )
            )
        }
    }

    suspend fun finishConversation(
        conversationId: String,
        summary: ConversationSummaryEntity? = null,
        generatedTitle: String? = null
    ) {
        val now = System.currentTimeMillis()
        val conv = conversationDao.getById(conversationId) ?: return
        val updatedConv = conv.copy(
            title = generatedTitle?.takeIf { it.isNotBlank() } ?: conv.title,
            endedAt = now,
            updatedAt = now,
            isSynced = false
        )
        conversationDao.update(updatedConv)
        if (summary != null) {
            summaryDao.upsert(summary)
        }
        syncConversationIfEnabled(conversationId)
    }

    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun getRecentSummaries(limit: Int = 5): List<ConversationSummaryEntity> =
        summaryDao.getRecentSummaries(limit)

    suspend fun getConfirmedMemories(): List<MemoryEntity> = memoryDao.getConfirmedMemories()

    suspend fun searchConfirmedMemories(query: String, limit: Int = 15): List<MemoryEntity> {
        val q = query.trim()
        return if (q.isBlank()) {
            memoryDao.getConfirmedMemories().take(limit)
        } else {
            val matched = memoryDao.searchMemories(q, limit)
            if (matched.isNotEmpty()) matched else memoryDao.getConfirmedMemories().take(limit)
        }
    }

    suspend fun saveOrUpdateMemory(
        content: String,
        category: MemoryCategory,
        importance: Int = 3,
        isSensitive: Boolean = false,
        userConfirmed: Boolean = true,
        sourceConversationId: String? = null
    ): MemoryEntity {
        val clean = content.trim()
        val now = System.currentTimeMillis()
        val user = getCurrentUser()
        val existingAll = memoryDao.getAll()

        // Deduplicate closely matching memories or IDENTITY name updates
        val duplicate = existingAll.firstOrNull {
            it.content.equals(clean, ignoreCase = true) ||
                (category == MemoryCategory.IDENTITY &&
                    it.category == MemoryCategory.IDENTITY.name &&
                    clean.contains("name is", ignoreCase = true) &&
                    it.content.contains("name is", ignoreCase = true))
        }

        val entity = if (duplicate != null) {
            duplicate.copy(
                content = clean,
                category = category.name,
                importance = maxOf(duplicate.importance, importance.coerceIn(1, 5)),
                isSensitive = isSensitive,
                userConfirmed = userConfirmed,
                sourceConversationId = sourceConversationId ?: duplicate.sourceConversationId,
                updatedAt = now,
                isSynced = false
            )
        } else {
            MemoryEntity(
                id = UUID.randomUUID().toString(),
                userId = user?.firebaseUid ?: user?.id ?: "local_user",
                category = category.name,
                content = clean,
                importance = importance.coerceIn(1, 5),
                isSensitive = isSensitive,
                userConfirmed = userConfirmed,
                sourceConversationId = sourceConversationId,
                createdAt = now,
                updatedAt = now,
                isSynced = false
            )
        }

        memoryDao.upsert(entity)

        // If this is the user's name, also update local user profile display name
        if (category == MemoryCategory.IDENTITY && userConfirmed) {
            extractNameFromMemory(clean)?.let { extractedName ->
                val cur = userDao.getCurrentUser()
                if (cur != null) {
                    userDao.upsertUser(cur.copy(displayName = extractedName, updatedAt = now))
                }
            }
        }

        if (entity.userConfirmed && isCloudSyncActive()) {
            runCatching {
                cloudSync.upsertMemory(entity)
                memoryDao.upsert(entity.copy(isSynced = true))
            }.onFailure {
                Log.w(TAG, "Cloud memory upsert failed: ${it.message}")
            }
        }

        return entity
    }

    suspend fun confirmSensitiveMemory(memory: MemoryEntity) {
        val confirmed = memory.copy(
            userConfirmed = true,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        memoryDao.upsert(confirmed)
        if (isCloudSyncActive()) {
            runCatching {
                cloudSync.upsertMemory(confirmed)
                memoryDao.upsert(confirmed.copy(isSynced = true))
            }
        }
    }

    suspend fun updateMemoryContent(memory: MemoryEntity, newContent: String, newCategory: MemoryCategory) {
        val updated = memory.copy(
            content = newContent.trim(),
            category = newCategory.name,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        memoryDao.upsert(updated)
        if (updated.userConfirmed && isCloudSyncActive()) {
            runCatching {
                cloudSync.upsertMemory(updated)
                memoryDao.upsert(updated.copy(isSynced = true))
            }
        }
    }

    suspend fun deleteMemory(memoryId: String) {
        memoryDao.deleteById(memoryId)
        if (isCloudSyncActive()) {
            runCatching { cloudSync.deleteMemory(memoryId) }
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        val msgs = messageDao.getMessagesForConversation(conversationId)
        msgs.forEach { msg ->
            msg.audioFilePath?.let { path -> runCatching { File(path).delete() } }
        }
        conversationDao.deleteById(conversationId)
        if (isCloudSyncActive()) {
            runCatching { cloudSync.deleteConversation(conversationId) }
        }
    }

    suspend fun deleteAllConversations() {
        messageDao.getAll().forEach { msg ->
            msg.audioFilePath?.let { path -> runCatching { File(path).delete() } }
        }
        summaryDao.deleteAll()
        messageDao.deleteAll()
        conversationDao.deleteAll()
        if (isCloudSyncActive()) {
            // Re-sync empty conversation state by removing remote conversations
            runCatching {
                val bundle = cloudSync.restoreFromCloud()
                bundle.conversations.forEach { cloudSync.deleteConversation(it.id) }
            }
        }
    }

    suspend fun clearAllData(alsoDeleteCloud: Boolean) {
        messageDao.getAll().forEach { msg ->
            msg.audioFilePath?.let { path -> runCatching { File(path).delete() } }
        }
        summaryDao.deleteAll()
        messageDao.deleteAll()
        conversationDao.deleteAll()
        memoryDao.deleteAll()
        if (alsoDeleteCloud && Firebase.auth.currentUser != null) {
            runCatching { cloudSync.deleteAllCloudData() }
        }
        val now = System.currentTimeMillis()
        settingsDao.upsert(AppSettingsEntity(updatedAt = now))
        RitDatabase.seedDefaultsIfNeeded(db)
    }

    suspend fun syncAllToCloud() {
        if (!isCloudSyncReadyForManualSync()) {
            throw IllegalStateException("Please sign in with Google first to sync to the cloud.")
        }
        val settings = getSettings()
        val user = getCurrentUser()
        val memories = memoryDao.getConfirmedMemories()
        val conversations = conversationDao.getAll()
        val summariesMap = summaryDao.getAll().associateBy { it.conversationId }
        val messagesMap = conversations.associate { it.id to messageDao.getMessagesForConversation(it.id) }

        cloudSync.backupAllToCloud(
            user = user,
            settings = settings,
            memories = memories,
            conversations = conversations,
            summaries = summariesMap,
            messagesByConversation = messagesMap
        )

        val now = System.currentTimeMillis()
        memories.forEach { memoryDao.upsert(it.copy(isSynced = true)) }
        conversations.forEach { conversationDao.update(it.copy(isSynced = true)) }
        settingsDao.upsert(settings.copy(lastSyncedAt = now, updatedAt = now))
    }

    suspend fun restoreFromCloud(): Int {
        if (Firebase.auth.currentUser == null) {
            throw IllegalStateException("Sign in with Google to restore your cloud backup.")
        }
        val bundle = cloudSync.restoreFromCloud()
        val now = System.currentTimeMillis()
        bundle.profile?.let { prof ->
            val currentSettings = getSettings()
            val updated = currentSettings.copy(
                selectedVoiceName = prof.selectedVoiceName.ifBlank { currentSettings.selectedVoiceName },
                selectedLanguage = prof.selectedLanguage.ifBlank { currentSettings.selectedLanguage },
                cloudBackupEnabled = true,
                allowAutoMemoryExtraction = prof.allowAutoMemoryExtraction,
                askBeforeSensitiveMemory = prof.askBeforeSensitiveMemory,
                lastSyncedAt = now,
                updatedAt = now
            )
            settingsDao.upsert(updated)
            voiceDao.selectVoice(updated.selectedVoiceName, now)
        }

        if (bundle.memories.isNotEmpty()) {
            memoryDao.upsertAll(bundle.memories)
        }
        bundle.conversations.forEach { conversationDao.upsert(it) }
        bundle.summaries.forEach { summaryDao.upsert(it) }
        if (bundle.messages.isNotEmpty()) {
            messageDao.insertAll(bundle.messages)
        }
        return bundle.memories.size + bundle.conversations.size
    }

    suspend fun buildExportJson(): String {
        val user = userDao.getCurrentUser()
        val settings = getSettings()
        val memories = memoryDao.getAll()
        val conversations = conversationDao.getAll()
        val summariesMap = summaryDao.getAll().associateBy { it.conversationId }

        val root = JSONObject().apply {
            put("app", "RIT AI")
            put("exportedAt", System.currentTimeMillis())
            put("user", JSONObject().apply {
                put("displayName", user?.displayName ?: "Friend")
                put("email", user?.email ?: JSONObject.NULL)
            })
            put("settings", JSONObject().apply {
                put("selectedVoiceName", settings.selectedVoiceName)
                put("selectedLanguage", settings.selectedLanguage)
                put("cloudBackupEnabled", settings.cloudBackupEnabled)
                put("storeAudioRecordings", settings.storeAudioRecordings)
            })
            put("memories", JSONArray().apply {
                memories.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("category", m.category)
                        put("content", m.content)
                        put("importance", m.importance)
                        put("isSensitive", m.isSensitive)
                        put("userConfirmed", m.userConfirmed)
                        put("createdAt", m.createdAt)
                    })
                }
            })
            put("conversations", JSONArray().apply {
                conversations.forEach { c ->
                    val msgs = messageDao.getMessagesForConversation(c.id)
                    val sum = summariesMap[c.id]
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("title", c.title)
                        put("startedAt", c.startedAt)
                        put("endedAt", c.endedAt ?: JSONObject.NULL)
                        put("language", c.language)
                        put("voiceName", c.voiceName)
                        put("summary", sum?.summaryText ?: JSONObject.NULL)
                        put("topics", sum?.keyTopicsCsv ?: JSONObject.NULL)
                        put("mood", sum?.moodTag ?: JSONObject.NULL)
                        put("messages", JSONArray().apply {
                            msgs.forEach { msg ->
                                put(JSONObject().apply {
                                    put("role", msg.role)
                                    put("transcript", msg.transcript)
                                    put("timestamp", msg.timestamp)
                                })
                            }
                        })
                    })
                }
            })
        }
        return root.toString(2)
    }

    private suspend fun isCloudSyncActive(): Boolean {
        return Firebase.auth.currentUser != null && getSettings().cloudBackupEnabled
    }

    private fun isCloudSyncReadyForManualSync(): Boolean {
        return Firebase.auth.currentUser != null
    }

    private suspend fun syncSettingsIfEnabled(settings: AppSettingsEntity) {
        if (Firebase.auth.currentUser != null && settings.cloudBackupEnabled) {
            val user = getCurrentUser()
            val fbUser = Firebase.auth.currentUser
            runCatching {
                cloudSync.upsertUserProfile(
                    displayName = fbUser?.displayName ?: user?.displayName ?: "Friend",
                    email = fbUser?.email ?: user?.email,
                    settings = settings
                )
            }.onFailure {
                Log.w(TAG, "Cloud settings sync skipped: ${it.message}")
            }
        }
    }

    private suspend fun syncConversationIfEnabled(conversationId: String) {
        if (!isCloudSyncActive()) return
        val conv = conversationDao.getById(conversationId) ?: return
        val summary = summaryDao.getByConversationId(conversationId)
        val msgs = messageDao.getMessagesForConversation(conversationId)
        runCatching {
            cloudSync.upsertConversationWithMessages(conv, summary, msgs)
            conversationDao.update(conv.copy(isSynced = true))
            val now = System.currentTimeMillis()
            settingsDao.upsert(getSettings().copy(lastSyncedAt = now, updatedAt = now))
        }.onFailure {
            Log.w(TAG, "Cloud conversation sync failed: ${it.message}")
        }
    }

    private fun extractNameFromMemory(text: String): String? {
        val regex = Regex("""(?:user(?:'s)?|my)\s+name\s+is\s+([A-Za-z][A-Za-z\s]{1,28})""", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()?.split(" ")?.firstOrNull()
    }

    companion object {
        private const val TAG = "RitRepository"
    }
}
