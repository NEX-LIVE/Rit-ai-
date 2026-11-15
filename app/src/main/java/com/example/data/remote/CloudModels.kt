package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val selectedVoiceName: String = "Aoede",
    val selectedLanguage: String = "Hinglish",
    val cloudBackupEnabled: Boolean = false,
    val allowAutoMemoryExtraction: Boolean = true,
    val askBeforeSensitiveMemory: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudMemory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "FACT",
    val content: String = "",
    val importance: Long = 3L,
    val isSensitive: Boolean = false,
    val userConfirmed: Boolean = true,
    val sourceConversationId: String? = null,
    val localCreatedAtMs: Long = 0L,
    val localUpdatedAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudConversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val startedAtMs: Long = 0L,
    val endedAtMs: Long? = null,
    val language: String = "Hinglish",
    val voiceName: String = "Aoede",
    val messageCount: Long = 0L,
    val summaryText: String? = null,
    val keyTopics: String? = null,
    val moodTag: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "USER",
    val transcript: String = "",
    val timestampMs: Long = 0L,
    val durationMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
