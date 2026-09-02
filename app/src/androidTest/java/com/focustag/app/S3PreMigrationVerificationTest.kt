package com.focustag.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.model.SessionStatus
import com.focustag.app.data.repository.AppInventoryRepository
import com.focustag.app.data.repository.AppPolicyRepository
import com.focustag.app.data.repository.EnforcementRepository
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.data.repository.SupabaseHistoryRepository
import com.focustag.app.data.supabase.SupabaseModule
import com.focustag.app.domain.EnforcementCoordinator
import com.focustag.app.domain.NoOpEnforcementStrategy
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class S3PreMigrationVerificationTest {

    private lateinit var context: Context
    private val userId = "test_user_s3_verify"
    private lateinit var historyRepo: SessionHistoryRepository
    private lateinit var enforcementRepo: EnforcementRepository
    private lateinit var coordinator: EnforcementCoordinator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs
        context.getSharedPreferences("focus_history_$userId", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("focus_prefs_$userId", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("device_focus_state", Context.MODE_PRIVATE).edit().clear().commit()
        
        historyRepo = SessionHistoryRepository(context, userId)
        enforcementRepo = EnforcementRepository(context, userId)
        
        coordinator = EnforcementCoordinator(
            userId = userId,
            inventoryRepository = AppInventoryRepository(context),
            policyRepository = AppPolicyRepository(context, userId),
            enforcementRepository = enforcementRepo,
            sessionHistoryRepository = historyRepo,
            strategy = NoOpEnforcementStrategy()
        )
    }

    @Test
    fun testCompletedSessionLifecycle() = runBlocking {
        // 1. Start session
        coordinator.startEnforcement("test_tag")
        val snapshot = enforcementRepo.getSnapshot()
        assertNotNull(snapshot)
        val sessionId = snapshot!!.sessionId
        
        val initialRecord = historyRepo.getSessions().first { it.sessionId == sessionId }
        assertEquals(SessionStatus.IN_PROGRESS, initialRecord.status)
        assertNull(initialRecord.endAt)

        // 2. Complete session
        val beforeStop = System.currentTimeMillis()
        coordinator.stopEnforcement()
        val afterStop = System.currentTimeMillis()

        // 3. Verify
        val finalRecord = historyRepo.getSessions().first { it.sessionId == sessionId }
        assertEquals(SessionStatus.COMPLETED, finalRecord.status)
        assertNotNull("COMPLETED session must have endAt", finalRecord.endAt)
        assertTrue("endAt must be >= startAt", finalRecord.endAt!! >= finalRecord.startAt)
        assertTrue("endAt should be within stop window", finalRecord.endAt!! in beforeStop..afterStop)
    }

    @Test
    fun testInterruptedSessionLifecycle() = runBlocking {
        // 1. Setup IN_PROGRESS
        val sessionId = UUID.randomUUID().toString()
        val startAt = System.currentTimeMillis()
        val v1 = FocusSessionRecord(
            sessionId = sessionId,
            userId = userId,
            tagId = "tag_interrupt",
            startAt = startAt,
            status = SessionStatus.IN_PROGRESS,
            syncDirty = true
        )
        historyRepo.createSession(v1)

        // 2. Interrupt with known time
        val knownEndAt = startAt + 10000
        historyRepo.interruptSession(sessionId, knownEndAt)

        // 3. Verify
        val record = historyRepo.getSessions().first { it.sessionId == sessionId }
        assertEquals(SessionStatus.INTERRUPTED, record.status)
        assertEquals("INTERRUPTED session must have the provided endAt", knownEndAt, record.endAt)
    }

    @Test
    fun testOrphanRecoveryPath() = runBlocking {
        // 1. Simulate an orphaned session (Snapshot exists in prefs, but Hub/Coordinator thinks it's starting fresh)
        // We do this by manually saving a snapshot and setting ownership in enforcementRepo
        val sessionId = UUID.randomUUID().toString()
        val startAt = System.currentTimeMillis() - 60000
        
        // Setup local history record
        historyRepo.createSession(FocusSessionRecord(
            sessionId = sessionId,
            userId = userId,
            tagId = "orphan_tag",
            startAt = startAt,
            status = SessionStatus.IN_PROGRESS,
            syncDirty = true
        ))
        
        // Setup enforcement state
        enforcementRepo.saveSnapshot(com.focustag.app.data.model.EnforcementSnapshot(
            sessionId = sessionId,
            userId = userId,
            timestamp = startAt,
            policies = emptyList()
        ))
        enforcementRepo.setDeviceEnforcementOwnerId(userId)

        // 2. Trigger orphan handling
        val beforeReconcile = System.currentTimeMillis()
        coordinator.checkAndHandleOrphans()
        val afterReconcile = System.currentTimeMillis()

        // 3. Verify
        val record = historyRepo.getSessions().first { it.sessionId == sessionId }
        assertEquals("Orphan must be INTERRUPTED", SessionStatus.INTERRUPTED, record.status)
        assertNotNull("Orphan must have endAt", record.endAt)
        assertTrue("endAt must be recent", record.endAt!! in beforeReconcile..afterReconcile)
        assertTrue("Orphan must be marked dirty", record.syncDirty)
        
        // Verify enforcement state cleared
        assertNull(enforcementRepo.getSnapshot())
        assertNull(enforcementRepo.getDeviceEnforcementOwnerId())
    }

    @Test
    fun testCloudPayloadEndAtVerification() = runBlocking {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val emailArg = args.getString("user_a_email") ?: return@runBlocking // Skip if no cloud credentials
        val passArg = args.getString("user_a_password") ?: return@runBlocking
        
        val client = SupabaseModule.client
        client.auth.signInWith(Email) {
            this.email = emailArg
            this.password = passArg
        }
        val cloudUserId = client.auth.currentSessionOrNull()?.user?.id ?: return@runBlocking
        
        val remoteRepo = SupabaseHistoryRepository()
        
        // 1. Verify COMPLETED payload
        val completedSessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val completedRecord = FocusSessionRecord(
            sessionId = completedSessionId,
            userId = cloudUserId,
            tagId = "cloud_verify_comp",
            startAt = now - 10000,
            endAt = now,
            status = SessionStatus.COMPLETED,
            syncDirty = true
        )
        
        remoteRepo.upsertSession(completedRecord).getOrThrow()
        
        val fetchedComp = remoteRepo.fetchSessions(cloudUserId).getOrThrow().first { it.sessionId == completedSessionId }
        assertEquals(SessionStatus.COMPLETED, fetchedComp.status)
        assertNotNull("Cloud COMPLETED session must have endAt", fetchedComp.endAt)
        assertEquals(now, fetchedComp.endAt)

        // 2. Verify INTERRUPTED payload
        val interruptedSessionId = UUID.randomUUID().toString()
        val interruptedRecord = FocusSessionRecord(
            sessionId = interruptedSessionId,
            userId = cloudUserId,
            tagId = "cloud_verify_int",
            startAt = now - 5000,
            endAt = now + 1000,
            status = SessionStatus.INTERRUPTED,
            syncDirty = true
        )
        
        remoteRepo.upsertSession(interruptedRecord).getOrThrow()
        
        val fetchedInt = remoteRepo.fetchSessions(cloudUserId).getOrThrow().first { it.sessionId == interruptedSessionId }
        assertEquals(SessionStatus.INTERRUPTED, fetchedInt.status)
        assertNotNull("Cloud INTERRUPTED session must have endAt", fetchedInt.endAt)
        assertEquals(now + 1000, fetchedInt.endAt)
    }
}
