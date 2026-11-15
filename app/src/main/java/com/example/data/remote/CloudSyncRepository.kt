package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.AppSettingsEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationSummaryEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.MessageEntity
import com.example.data.local.UserEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class RestoredCloudBundle(
    val profile: UserProfile?,
    val memories: List<MemoryEntity>,
    val conversations: List<ConversationEntity>,
    val summaries: List<ConversationSummaryEntity>,
    val messages: List<MessageEntity>
)

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeCloudMemories(): Flow<List<CloudMemory>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/memories"
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudMemory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertUserProfile(
        displayName: String,
        email: String?,
        settings: AppSettingsEntity
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", displayName.ifBlank { "Friend" }.take(120))
                    if (!email.isNullOrBlank()) put("email", email.take(200))
                    put("selectedVoiceName", settings.selectedVoiceName.take(40))
                    put("selectedLanguage", settings.selectedLanguage)
                    put("cloudBackupEnabled", settings.cloudBackupEnabled)
                    put("allowAutoMemoryExtraction", settings.allowAutoMemoryExtraction)
                    put("askBeforeSensitiveMemory", settings.askBeforeSensitiveMemory)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("displayName", displayName.ifBlank { "Friend" }.take(120))
                    if (!email.isNullOrBlank()) put("email", email.take(200))
                    put("selectedVoiceName", settings.selectedVoiceName.take(40))
                    put("selectedLanguage", settings.selectedLanguage)
                    put("cloudBackupEnabled", settings.cloudBackupEnabled)
                    put("allowAutoMemoryExtraction", settings.allowAutoMemoryExtraction)
                    put("askBeforeSensitiveMemory", settings.askBeforeSensitiveMemory)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun upsertMemory(memory: MemoryEntity) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document(memory.id)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("category", memory.category)
                    put("content", memory.content.take(2000))
                    put("importance", memory.importance.coerceIn(1, 5).toLong())
                    put("isSensitive", memory.isSensitive)
                    put("userConfirmed", memory.userConfirmed)
                    if (!memory.sourceConversationId.isNullOrBlank()) {
                        put("sourceConversationId", memory.sourceConversationId.take(128))
                    }
                    put("localCreatedAtMs", memory.createdAt.coerceAtLeast(0L))
                    put("localUpdatedAtMs", memory.updatedAt.coerceAtLeast(0L))
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("category", memory.category)
                    put("content", memory.content.take(2000))
                    put("importance", memory.importance.coerceIn(1, 5).toLong())
                    put("isSensitive", memory.isSensitive)
                    put("userConfirmed", memory.userConfirmed)
                    if (!memory.sourceConversationId.isNullOrBlank()) {
                        put("sourceConversationId", memory.sourceConversationId.take(128))
                    }
                    put("localUpdatedAtMs", memory.updatedAt.coerceAtLeast(0L))
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteMemory(memoryId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun upsertConversationWithMessages(
        conversation: ConversationEntity,
        summary: ConversationSummaryEntity?,
        messages: List<MessageEntity>
    ) {
        val uid = requireUserId()
        val convRef = db.collection("users").document(uid)
            .collection("conversations").document(conversation.id)
        try {
            val existingConv = convRef.get().await()
            if (!existingConv.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("title", conversation.title.ifBlank { "Voice Conversation" }.take(200))
                    put("startedAtMs", conversation.startedAt.coerceAtLeast(0L))
                    conversation.endedAt?.let { put("endedAtMs", it.coerceAtLeast(0L)) }
                    put("language", conversation.language)
                    put("voiceName", conversation.voiceName.take(40))
                    put("messageCount", conversation.messageCount.coerceAtLeast(0).toLong())
                    summary?.summaryText?.takeIf { it.isNotBlank() }?.let {
                        put("summaryText", it.take(4000))
                    }
                    summary?.keyTopicsCsv?.takeIf { it.isNotBlank() }?.let {
                        put("keyTopics", it.take(500))
                    }
                    summary?.moodTag?.takeIf { it.isNotBlank() }?.let {
                        put("moodTag", it.take(60))
                    }
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                convRef.set(payload).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("title", conversation.title.ifBlank { "Voice Conversation" }.take(200))
                    conversation.endedAt?.let { put("endedAtMs", it.coerceAtLeast(0L)) }
                    put("language", conversation.language)
                    put("voiceName", conversation.voiceName.take(40))
                    put("messageCount", conversation.messageCount.coerceAtLeast(0).toLong())
                    summary?.summaryText?.takeIf { it.isNotBlank() }?.let {
                        put("summaryText", it.take(4000))
                    }
                    summary?.keyTopicsCsv?.takeIf { it.isNotBlank() }?.let {
                        put("keyTopics", it.take(500))
                    }
                    summary?.moodTag?.takeIf { it.isNotBlank() }?.let {
                        put("moodTag", it.take(60))
                    }
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                convRef.update(updates).await()
            }

            if (messages.isNotEmpty()) {
                val existingMsgIds = convRef.collection("messages").get().await()
                    .documents.map { it.id }.toSet()
                val unsynced = messages.filter { it.id !in existingMsgIds && it.transcript.isNotBlank() }
                unsynced.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { msg ->
                        val msgRef = convRef.collection("messages").document(msg.id)
                        val msgPayload = mapOf(
                            "userId" to uid,
                            "conversationId" to conversation.id,
                            "role" to if (msg.role == "USER") "USER" else "RIT",
                            "transcript" to msg.transcript.take(8000),
                            "timestampMs" to msg.timestamp.coerceAtLeast(0L),
                            "durationMs" to msg.durationMs.coerceAtLeast(0L),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        batch.set(msgRef, msgPayload)
                    }
                    batch.commit().await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, convRef.path)
            throw e
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        val uid = requireUserId()
        val convRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
        try {
            val msgs = convRef.collection("messages").get().await()
            msgs.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            convRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path)
            throw e
        }
    }

    suspend fun backupAllToCloud(
        user: UserEntity?,
        settings: AppSettingsEntity,
        memories: List<MemoryEntity>,
        conversations: List<ConversationEntity>,
        summaries: Map<String, ConversationSummaryEntity>,
        messagesByConversation: Map<String, List<MessageEntity>>
    ) {
        val uid = requireUserId()
        val fbUser = auth.currentUser
        upsertUserProfile(
            displayName = fbUser?.displayName ?: user?.displayName ?: "Friend",
            email = fbUser?.email ?: user?.email,
            settings = settings
        )
        memories.forEach { memory ->
            upsertMemory(memory)
        }
        conversations.forEach { conversation ->
            upsertConversationWithMessages(
                conversation = conversation,
                summary = summaries[conversation.id],
                messages = messagesByConversation[conversation.id].orEmpty()
            )
        }
    }

    suspend fun restoreFromCloud(): RestoredCloudBundle {
        val uid = requireUserId()
        val userDocRef = db.collection("users").document(uid)
        try {
            val profileSnap = userDocRef.get().await()
            val profile = profileSnap.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )

            val memSnaps = userDocRef.collection("memories").get().await()
            val cloudMemories = memSnaps.toObjects(
                CloudMemory::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val memories = cloudMemories.map { cm ->
                MemoryEntity(
                    id = cm.id,
                    userId = uid,
                    category = cm.category,
                    content = cm.content,
                    importance = cm.importance.toInt().coerceIn(1, 5),
                    isSensitive = cm.isSensitive,
                    userConfirmed = cm.userConfirmed,
                    sourceConversationId = cm.sourceConversationId,
                    createdAt = cm.localCreatedAtMs,
                    updatedAt = cm.localUpdatedAtMs,
                    isSynced = true
                )
            }

            val convSnaps = userDocRef.collection("conversations").get().await()
            val cloudConvs = convSnaps.toObjects(
                CloudConversation::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )

            val conversations = mutableListOf<ConversationEntity>()
            val summaries = mutableListOf<ConversationSummaryEntity>()
            val messages = mutableListOf<MessageEntity>()

            cloudConvs.forEach { cc ->
                conversations.add(
                    ConversationEntity(
                        id = cc.id,
                        userId = uid,
                        title = cc.title,
                        startedAt = cc.startedAtMs,
                        endedAt = cc.endedAtMs,
                        language = cc.language,
                        voiceName = cc.voiceName,
                        messageCount = cc.messageCount.toInt(),
                        isSynced = true,
                        updatedAt = cc.startedAtMs
                    )
                )
                if (!cc.summaryText.isNullOrBlank()) {
                    summaries.add(
                        ConversationSummaryEntity(
                            id = "sum_${cc.id}",
                            conversationId = cc.id,
                            summaryText = cc.summaryText,
                            keyTopicsCsv = cc.keyTopics.orEmpty(),
                            moodTag = cc.moodTag ?: "Warm",
                            createdAt = cc.endedAtMs ?: cc.startedAtMs
                        )
                    )
                }

                val msgSnaps = userDocRef.collection("conversations")
                    .document(cc.id)
                    .collection("messages")
                    .get()
                    .await()
                val cloudMsgs = msgSnaps.toObjects(
                    CloudMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                cloudMsgs.forEach { cm ->
                    messages.add(
                        MessageEntity(
                            id = cm.id,
                            conversationId = cc.id,
                            role = cm.role,
                            transcript = cm.transcript,
                            timestamp = cm.timestampMs,
                            durationMs = cm.durationMs,
                            audioFilePath = null,
                            isSynced = true
                        )
                    )
                }
            }

            return RestoredCloudBundle(
                profile = profile,
                memories = memories,
                conversations = conversations,
                summaries = summaries,
                messages = messages
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userDocRef.path)
            throw e
        }
    }

    suspend fun deleteAllCloudData() {
        val uid = requireUserId()
        val userDocRef = db.collection("users").document(uid)
        try {
            val memories = userDocRef.collection("memories").get().await()
            memories.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            val conversations = userDocRef.collection("conversations").get().await()
            conversations.documents.forEach { convDoc ->
                deleteConversation(convDoc.id)
            }
            userDocRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userDocRef.path)
            throw e
        }
    }
}
