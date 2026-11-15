package com.example.ai

import android.util.Log
import com.example.data.local.ConversationSummaryEntity
import com.example.data.local.MemoryCategory
import com.example.data.local.MessageEntity
import com.example.data.repository.RitRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

class MemoryAnalyzerService(
    private val repository: RitRepository
) {
    private val analyzerModel by lazy {
        val memoryItemSchema = Schema.obj(
            properties = mapOf(
                "content" to Schema.string(description = "Concise third-person memory statement, e.g. 'User's name is Rajan' or 'User prefers oat milk latte'"),
                "category" to Schema.enumeration(
                    values = MemoryCategory.entries.map { it.name },
                    description = "Category of the memory"
                ),
                "importance" to Schema.integer(description = "Importance score from 1 to 5"),
                "isSensitive" to Schema.boolean(description = "True if personal health, financial, password, address, or deeply private secret")
            )
        )

        val responseSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short 2-5 word title for this voice conversation"),
                "summary" to Schema.string(description = "2-3 sentence summary of what was discussed and any emotional tone"),
                "keyTopics" to Schema.array(
                    items = Schema.string(),
                    description = "Up to 4 short topic tags"
                ),
                "moodTag" to Schema.string(description = "One-word mood label like Warm, Playful, Reflective, Excited, Calm"),
                "newMemories" to Schema.array(
                    items = memoryItemSchema,
                    description = "Only truly new, durable facts/preferences/dates/projects/instructions shared by the user"
                )
            )
        )

        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                this.responseSchema = responseSchema
            }
        )
    }

    suspend fun summarizeAndExtractMemories(
        conversationId: String,
        messages: List<MessageEntity>,
        existingMemoriesSummary: List<String>,
        allowAutoExtract: Boolean,
        askBeforeSensitive: Boolean
    ): Pair<String, ConversationSummaryEntity>? {
        if (messages.isEmpty()) return null

        val transcriptBlock = messages.joinToString("\n") { msg ->
            val speaker = if (msg.role == "USER") "User" else "Rit"
            "$speaker: ${msg.transcript}"
        }.take(12_000)

        val existingList = if (existingMemoriesSummary.isEmpty()) {
            "None"
        } else {
            existingMemoriesSummary.take(25).joinToString("; ")
        }

        val prompt = """
Analyze this voice conversation between a user and Rit (their female AI companion).
1. Create a short title (2-5 words), a concise 2-3 sentence summary, up to 4 topic tags, and a 1-word mood tag.
2. Extract ONLY genuinely useful, durable long-term facts, user name, preferences, likes/dislikes, important dates/events, ongoing projects, or explicit instructions that are NOT already covered by Existing Memories.
If no new durable memories exist, return an empty list for newMemories.

Existing Memories: $existingList

Transcript:
$transcriptBlock
""".trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                analyzerModel.generateContent(prompt)
            }
            val rawJson = response.text ?: return fallbackSummary(conversationId, messages)
            val json = JSONObject(rawJson)

            val title = json.optString("title", "Voice Chat with Rit").ifBlank { "Voice Chat with Rit" }
            val summaryText = json.optString("summary", "").ifBlank {
                "Voice conversation with ${messages.size} turns."
            }
            val topicsArr = json.optJSONArray("keyTopics")
            val topicsList = buildList {
                if (topicsArr != null) {
                    for (i in 0 until topicsArr.length()) {
                        topicsArr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            val moodTag = json.optString("moodTag", "Warm").ifBlank { "Warm" }

            if (allowAutoExtract) {
                val memsArr = json.optJSONArray("newMemories")
                if (memsArr != null) {
                    for (i in 0 until memsArr.length()) {
                        val obj = memsArr.optJSONObject(i) ?: continue
                        val content = obj.optString("content", "").trim()
                        if (content.isBlank()) continue
                        val category = MemoryCategory.fromRaw(obj.optString("category"))
                        val importance = obj.optInt("importance", 3).coerceIn(1, 5)
                        val isSensitive = obj.optBoolean("isSensitive", false)
                        val userConfirmed = !(isSensitive && askBeforeSensitive)

                        repository.saveOrUpdateMemory(
                            content = content,
                            category = category,
                            importance = importance,
                            isSensitive = isSensitive,
                            userConfirmed = userConfirmed,
                            sourceConversationId = conversationId
                        )
                    }
                }
            }

            val summaryEntity = ConversationSummaryEntity(
                id = "sum_${UUID.randomUUID()}",
                conversationId = conversationId,
                summaryText = summaryText,
                keyTopicsCsv = topicsList.joinToString(", "),
                moodTag = moodTag,
                createdAt = System.currentTimeMillis()
            )
            title to summaryEntity
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze conversation summary/memories", e)
            fallbackSummary(conversationId, messages)
        }
    }

    private fun fallbackSummary(
        conversationId: String,
        messages: List<MessageEntity>
    ): Pair<String, ConversationSummaryEntity> {
        val firstUser = messages.firstOrNull { it.role == "USER" }?.transcript.orEmpty()
        val title = firstUser.take(40).ifBlank { "Voice Chat with Rit" }
        val summaryText = "Voice conversation (${messages.size} spoken turns)."
        return title to ConversationSummaryEntity(
            id = "sum_${UUID.randomUUID()}",
            conversationId = conversationId,
            summaryText = summaryText,
            keyTopicsCsv = "Voice Call",
            moodTag = "Warm",
            createdAt = System.currentTimeMillis()
        )
    }

    companion object {
        private const val TAG = "MemoryAnalyzerService"
    }
}
