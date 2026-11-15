package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MemoryCategory(val label: String) {
    IDENTITY("Identity"),
    PREFERENCE("Preference"),
    LIKE_DISLIKE("Likes & Dislikes"),
    FACT("Important Fact"),
    DATE_EVENT("Dates & Events"),
    PROJECT("Projects & Work"),
    INSTRUCTION("Instructions");

    companion object {
        fun fromRaw(raw: String?): MemoryCategory {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.name == normalized } ?: FACT
        }
    }
}

enum class VoiceLanguage(val displayName: String, val promptHint: String) {
    HINGLISH(
        displayName = "Hinglish",
        promptHint = "Speak naturally in warm, conversational Hinglish (a natural everyday blend of Hindi and English just like close friends in India speak), matching the user's language mix effortlessly."
    ),
    HINDI(
        displayName = "Hindi",
        promptHint = "Speak naturally in warm, expressive conversational Hindi with natural pauses and everyday phrasing."
    ),
    ENGLISH(
        displayName = "English",
        promptHint = "Speak naturally in warm, expressive conversational English with realistic pacing and natural emotional cadence."
    );

    companion object {
        fun fromName(raw: String?): VoiceLanguage {
            return entries.firstOrNull {
                it.displayName.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
            } ?: HINGLISH
        }
    }
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val email: String?,
    val firebaseUid: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["startedAt"]), Index(value = ["userId"])]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long?,
    val language: String,
    val voiceName: String,
    val messageCount: Int,
    val isSynced: Boolean = false,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"]), Index(value = ["timestamp"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "USER" or "RIT"
    val transcript: String,
    val timestamp: Long,
    val durationMs: Long = 0L,
    val audioFilePath: String? = null, // Only populated if user explicitly opts in
    val isSynced: Boolean = false
)

@Entity(
    tableName = "memories",
    indices = [Index(value = ["category"]), Index(value = ["updatedAt"])]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val category: String, // MemoryCategory.name
    val content: String,
    val importance: Int = 3, // 1..5
    val isSensitive: Boolean = false,
    val userConfirmed: Boolean = true,
    val sourceConversationId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRecalledAt: Long? = null,
    val isSynced: Boolean = false
)

@Entity(tableName = "voice_preferences")
data class VoicePreferenceEntity(
    @PrimaryKey val voiceName: String,
    val displayName: String,
    val toneDescription: String,
    val personalityFit: String,
    val gender: String = "FEMALE",
    val isPreviewAvailable: Boolean = true,
    val isSelected: Boolean = false,
    val updatedAt: Long
)

@Entity(
    tableName = "conversation_summaries",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"], unique = true)]
)
data class ConversationSummaryEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val summaryText: String,
    val keyTopicsCsv: String,
    val moodTag: String,
    val createdAt: Long
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: String = DEFAULT_ID,
    val selectedVoiceName: String = "Aoede",
    val selectedLanguage: String = VoiceLanguage.HINGLISH.displayName,
    val cloudBackupEnabled: Boolean = false,
    val storeAudioRecordings: Boolean = false, // Disabled by default per privacy policy
    val allowAutoMemoryExtraction: Boolean = true,
    val askBeforeSensitiveMemory: Boolean = true,
    val bargeInEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val lastSyncedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}
