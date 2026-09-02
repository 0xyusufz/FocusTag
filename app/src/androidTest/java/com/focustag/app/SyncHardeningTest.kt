package com.focustag.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.model.InterceptionEvent
import com.focustag.app.data.model.SessionStatus
import com.focustag.app.data.repository.SessionHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SyncHardeningTest {

    private lateinit var context: Context
    private val userId = "test_user_hardening"
    private lateinit var repo: SessionHistoryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("focus_history_$userId", Context.MODE_PRIVATE).edit().clear().commit()
        repo = SessionHistoryRepository(context, userId)
    }

    @Test
    fun testPermanentFailureMarkingAndExclusion() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val record = FocusSessionRecord(
            sessionId = sessionId,
            userId = userId,
            tagId = "tag1",
            startAt = System.currentTimeMillis(),
            status = SessionStatus.COMPLETED,
            endAt = System.currentTimeMillis() + 1000,
            syncDirty = true
        )
        repo.createSession(record)
        
        // Initially dirty
        assertTrue(repo.getDirtySessions().any { it.sessionId == sessionId })

        // Mark as failed permanently
        repo.markSessionFailedPermanently(record)

        // Verify it is still in history but NO LONGER dirty (excluded from sync)
        val sessions = repo.getSessions()
        val stored = sessions.first { it.sessionId == sessionId }
        assertTrue("Should still be dirty internally", stored.syncDirty)
        assertTrue("Should be marked as failed permanently", stored.syncFailedPermanently)
        
        assertFalse("Should be excluded from getDirtySessions", 
            repo.getDirtySessions().any { it.sessionId == sessionId })
    }

    @Test
    fun testEventPermanentFailureExclusion() = runBlocking {
        val eventId = UUID.randomUUID().toString()
        val event = InterceptionEvent(
            eventId = eventId,
            sessionId = "session1",
            userId = userId,
            packageName = "com.test",
            timestamp = System.currentTimeMillis(),
            syncDirty = true
        )
        // Need to use emit which goes through flow, or I'll just wait for collector.
        // Actually persistEvent is private, but I can use emit and wait a bit.
        repo.emitInterceptionEvent("session1", "com.test")
        
        // Wait for persistence (collector is async)
        var dirty = repo.getDirtyEvents()
        var retryCount = 0
        while (dirty.isEmpty() && retryCount < 10) {
            kotlinx.coroutines.delay(100)
            dirty = repo.getDirtyEvents()
            retryCount++
        }
        
        val emittedEvent = dirty.first()
        repo.markEventFailedPermanently(emittedEvent)
        
        assertFalse("Permanently failed event should be excluded from dirty list",
            repo.getDirtyEvents().any { it.eventId == emittedEvent.eventId })
    }

    @Test
    fun testLocalUpdateResetsPermanentFailure() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val record = FocusSessionRecord(
            sessionId = sessionId,
            userId = userId,
            tagId = "tag1",
            startAt = System.currentTimeMillis(),
            status = SessionStatus.IN_PROGRESS,
            syncDirty = true
        )
        repo.createSession(record)
        
        val snapshot = repo.getDirtySessions().first()
        repo.markSessionFailedPermanently(snapshot)
        
        // Verify failed
        assertTrue(repo.getSessions().first().syncFailedPermanently)
        
        // Mutate locally (e.g. complete session)
        repo.completeSession(sessionId, SessionStatus.COMPLETED, System.currentTimeMillis() + 1000)
        
        // Verify failed flag is RESET
        val updated = repo.getSessions().first()
        assertFalse("Local update should reset permanent failure flag", updated.syncFailedPermanently)
        assertTrue("Should be dirty again", updated.syncDirty)
    }

    @Test
    fun testValueAwareFailedMarking() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val v1 = FocusSessionRecord(
            sessionId = sessionId,
            userId = userId,
            tagId = "tag1",
            startAt = System.currentTimeMillis(),
            status = SessionStatus.IN_PROGRESS,
            syncDirty = true
        )
        repo.createSession(v1)
        
        val snapshotV1 = repo.getDirtySessions().first()
        
        // Local mutation to V2
        repo.completeSession(sessionId, SessionStatus.COMPLETED, System.currentTimeMillis() + 1000)
        
        // Attempt to mark V1 as failed
        repo.markSessionFailedPermanently(snapshotV1)
        
        // Assertion: Local V2 MUST NOT be marked failed
        val current = repo.getSessions().first()
        assertFalse("Race fix: V2 should not be marked failed because it matched V1", 
            current.syncFailedPermanently)
    }
}
