package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.AppSettingsEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationSummaryEntity
import com.example.data.local.MemoryCategory
import com.example.data.local.MemoryEntity
import com.example.data.local.MessageEntity
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun owner_upsertAndRestoreProfileMemoryConversation_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore, auth)

        val settings = AppSettingsEntity(
            selectedVoiceName = "Aoede",
            selectedLanguage = "Hinglish",
            cloudBackupEnabled = true
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertUserProfile("Rajan", ALICE_EMAIL, settings)
        }

        val memId = "mem_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val memory = MemoryEntity(
            id = memId,
            userId = uid,
            category = MemoryCategory.IDENTITY.name,
            content = "User's name is Rajan",
            importance = 5,
            isSensitive = false,
            userConfirmed = true,
            createdAt = now,
            updatedAt = now
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertMemory(memory)
        }

        val convId = "conv_${UUID.randomUUID()}"
        val conv = ConversationEntity(
            id = convId,
            userId = uid,
            title = "Morning Check-in",
            startedAt = now,
            endedAt = now + 5000L,
            language = "Hinglish",
            voiceName = "Aoede",
            messageCount = 2,
            updatedAt = now
        )
        val summary = ConversationSummaryEntity(
            id = "sum_$convId",
            conversationId = convId,
            summaryText = "Rajan introduced himself to Rit.",
            keyTopicsCsv = "Introduction,Name",
            moodTag = "Warm",
            createdAt = now
        )
        val msg = MessageEntity(
            id = "msg_${UUID.randomUUID()}",
            conversationId = convId,
            role = "USER",
            transcript = "Rit, remember that my name is Rajan.",
            timestamp = now + 1000L,
            durationMs = 1500L
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertConversationWithMessages(conv, summary, listOf(msg))
        }

        val bundle = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.restoreFromCloud()
        }
        assertEquals("Rajan", bundle.profile?.displayName)
        assertTrue(bundle.memories.any { it.id == memId && it.content == "User's name is Rajan" })
        assertTrue(bundle.conversations.any { it.id == convId })
        assertTrue(bundle.messages.any { it.conversationId == convId })
    }

    @Test
    fun crossUserAccess_readingAnotherUsersMemory_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore, auth)
        val memId = "mem_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertMemory(
                MemoryEntity(
                    id = memId,
                    userId = aliceUid,
                    category = MemoryCategory.FACT.name,
                    content = "Alice prefers chai",
                    importance = 3,
                    isSensitive = false,
                    userConfirmed = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("memories").document(memId).get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's memory")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun unauthenticatedCaller_observeMemories_failsWithIllegalStateOrPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = CloudSyncRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeCloudMemories().first()
            }
            fail("Expected failure for unauthenticated caller")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertTrue(fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED || e is IllegalStateException)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_rit@test.com"
        const val BOB_EMAIL = "bob_rit@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
