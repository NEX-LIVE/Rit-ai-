package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.voice.GeminiVoiceCatalog

@Database(
    entities = [
        UserEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        VoicePreferenceEntity::class,
        ConversationSummaryEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RitDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun voicePreferenceDao(): VoicePreferenceDao
    abstract fun conversationSummaryDao(): ConversationSummaryDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: RitDatabase? = null

        fun getInstance(context: Context): RitDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RitDatabase::class.java,
                    "rit_ai_brain.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        suspend fun seedDefaultsIfNeeded(db: RitDatabase) {
            val now = System.currentTimeMillis()
            val settingsDao = db.appSettingsDao()
            val existingSettings = settingsDao.getSettings()
            if (existingSettings == null) {
                settingsDao.upsert(AppSettingsEntity(updatedAt = now))
            }

            val userDao = db.userDao()
            if (userDao.getCurrentUser() == null) {
                userDao.upsertUser(
                    UserEntity(
                        id = "local_user",
                        displayName = "Friend",
                        email = null,
                        firebaseUid = null,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            val voiceDao = db.voicePreferenceDao()
            val selectedName = existingSettings?.selectedVoiceName ?: GeminiVoiceCatalog.DEFAULT_VOICE_NAME
            val existingVoices = voiceDao.getAll()
            if (existingVoices.size < GeminiVoiceCatalog.officialFemaleVoices.size) {
                val entities = GeminiVoiceCatalog.officialFemaleVoices.map { voice ->
                    VoicePreferenceEntity(
                        voiceName = voice.voiceName,
                        displayName = voice.displayName,
                        toneDescription = voice.toneLabel,
                        personalityFit = voice.personalityFit,
                        gender = "FEMALE",
                        isPreviewAvailable = true,
                        isSelected = voice.voiceName == selectedName,
                        updatedAt = now
                    )
                }
                voiceDao.upsertAll(entities)
            }
        }
    }
}
