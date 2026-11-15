package com.example.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ConversationWithSummary(
    @Embedded val conversation: ConversationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "conversationId"
    )
    val summary: ConversationSummaryEntity?
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    fun observeCurrentUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}

@Dao
interface ConversationDao {
    @Transaction
    @Query("SELECT * FROM conversations ORDER BY startedAt DESC")
    fun observeConversationsWithSummaries(): Flow<List<ConversationWithSummary>>

    @Transaction
    @Query(
        """
        SELECT DISTINCT c.* FROM conversations c
        LEFT JOIN conversation_summaries s ON c.id = s.conversationId
        LEFT JOIN messages m ON c.id = m.conversationId
        WHERE (:query = '' OR c.title LIKE '%' || :query || '%' 
               OR COALESCE(s.summaryText, '') LIKE '%' || :query || '%'
               OR COALESCE(s.keyTopicsCsv, '') LIKE '%' || :query || '%'
               OR COALESCE(m.transcript, '') LIKE '%' || :query || '%')
          AND (:startMs IS NULL OR c.startedAt >= :startMs)
          AND (:endMs IS NULL OR c.startedAt <= :endMs)
        ORDER BY c.startedAt DESC
        """
    )
    fun searchConversations(query: String, startMs: Long?, endMs: Long?): Flow<List<ConversationWithSummary>>

    @Transaction
    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    fun observeConversationWithSummary(conversationId: String): Flow<ConversationWithSummary?>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getById(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY startedAt DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteById(conversationId: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY userConfirmed ASC, importance DESC, updatedAt DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE userConfirmed = 1 ORDER BY importance DESC, updatedAt DESC")
    suspend fun getConfirmedMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE userConfirmed = 0 ORDER BY updatedAt DESC")
    fun observePendingSensitiveMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE userConfirmed = 1 AND content LIKE '%' || :query || '%' ORDER BY importance DESC, updatedAt DESC LIMIT :limit")
    suspend fun searchMemories(query: String, limit: Int = 15): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAll(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(memories: List<MemoryEntity>)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}

@Dao
interface VoicePreferenceDao {
    @Query("SELECT * FROM voice_preferences ORDER BY isSelected DESC, displayName ASC")
    fun observeAllVoices(): Flow<List<VoicePreferenceEntity>>

    @Query("SELECT * FROM voice_preferences")
    suspend fun getAll(): List<VoicePreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(voices: List<VoicePreferenceEntity>)

    @Query("UPDATE voice_preferences SET isSelected = (voiceName = :selectedVoiceName), updatedAt = :updatedAt")
    suspend fun selectVoice(selectedVoiceName: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface ConversationSummaryDao {
    @Query("SELECT * FROM conversation_summaries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentSummaries(limit: Int = 6): List<ConversationSummaryEntity>

    @Query("SELECT * FROM conversation_summaries WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getByConversationId(conversationId: String): ConversationSummaryEntity?

    @Query("SELECT * FROM conversation_summaries ORDER BY createdAt DESC")
    suspend fun getAll(): List<ConversationSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: ConversationSummaryEntity)

    @Query("DELETE FROM conversation_summaries")
    suspend fun deleteAll()
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 'default' LIMIT 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 'default' LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettingsEntity)
}
