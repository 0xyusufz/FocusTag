package com.focustag.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.model.SessionStatus
import com.focustag.app.data.repository.SessionHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SyncConcurrencyTest {

    private lateinit var context: Context
    private val userId = "test_user_concurrency"
    private lateinit var repo: SessionHistoryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs for test user
        context.getSharedPreferences("focus_history_$userId", Context.MODE_PRIVATE).edit().clear().commit()
        repo = SessionHistoryRepository(context, userId)
    }

    @Test
    fun testRaceConditionPrevention() = runBlocking {
        // 1. Setup V1 (Dirty)
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
        
        // Verify dirty
        assertTrue(repo.getDirtySessions().any { it.sessionId == sessionId })

        // 2. Simulate Worker reading V1
        val snapshotV1 = repo.getDirtySessions().first { it.sessionId == sessionId }
        assertEquals(SessionStatus.IN_PROGRESS, snapshotV1.status)

        // 3. Concurrent Local Mutation to V2
        repo.completeSession(sessionId, SessionStatus.COMPLETED, System.currentTimeMillis() + 1000)
        
        // Verify local is now COMPLETED and still dirty
        val localV2 = repo.getSessions().first { it.sessionId == sessionId }
        assertEquals(SessionStatus.COMPLETED, localV2.status)
        assertTrue(localV2.syncDirty)

        // 4. Worker finishes "uploading" V1 and calls markSessionSynced(snapshotV1)
        repo.markSessionSynced(snapshotV1)

        // 5. Final Assertion: Local V2 MUST REMAIN DIRTY
        val finalLocal = repo.getSessions().first { it.sessionId == sessionId }
        assertEquals("Local session must remain COMPLETED", SessionStatus.COMPLETED, finalLocal.status)
        assertTrue("Race Fix Failure: syncDirty must remain true because V2 was not synced", finalLocal.syncDirty)
    }

    @Test
    fun testNormalSyncSuccess() = runBlocking {
        // 1. Setup V1 (Dirty)
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

        // 2. Worker reads V1
        val snapshotV1 = repo.getDirtySessions().first { it.sessionId == sessionId }

        // 3. Worker marks V1 synced (no concurrent mutation)
        repo.markSessionSynced(snapshotV1)

        // 4. Assertion: Clean
        val finalLocal = repo.getSessions().first { it.sessionId == sessionId }
        assertFalse("syncDirty should be false after successful sync", finalLocal.syncDirty)
    }

    @Test
    fun testInterruptedSessionHasEndAt() = runBlocking {
        // 1. Setup IN_PROGRESS
        val sessionId = UUID.randomUUID().toString()
        val v1 = FocusSessionRecord(
            sessionId = sessionId,
            userId = userId,
            tagId = "tag_interrupt",
            startAt = System.currentTimeMillis(),
            status = SessionStatus.IN_PROGRESS,
            syncDirty = true
        )
        repo.createSession(v1)

        // 2. Interrupt
        val interruptTime = System.currentTimeMillis() + 5000
        repo.interruptSession(sessionId, interruptTime)

        // 3. Verify
        val record = repo.getSessions().first { it.sessionId == sessionId }
        assertEquals(SessionStatus.INTERRUPTED, record.status)
        assertNotNull("Interrupted session must have endAt", record.endAt)
        assertEquals("endAt must match the provided timestamp", interruptTime, record.endAt)
    }
}
