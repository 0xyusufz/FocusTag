package com.focustag.app.domain

import android.util.Log
import com.focustag.app.data.model.EnforcementLedger
import com.focustag.app.data.model.EnforcementResult
import com.focustag.app.data.model.EnforcementSnapshot
import com.focustag.app.data.model.EnforcementStatus
import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.model.SessionStatus
import com.focustag.app.data.repository.AppInventoryRepository
import com.focustag.app.data.repository.AppPolicyRepository
import com.focustag.app.data.repository.EnforcementRepository
import com.focustag.app.data.repository.SessionHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

private const val TAG = "EnforcementCoord"

class EnforcementCoordinator(
    private val userId: String,
    private val inventoryRepository: AppInventoryRepository,
    private val policyRepository: AppPolicyRepository,
    private val enforcementRepository: EnforcementRepository,
    private val sessionHistoryRepository: SessionHistoryRepository,
    private val strategy: EnforcementStrategy
) {

    companion object {
        private val globalMutex = Mutex()
    }

    private val _status = MutableStateFlow(enforcementRepository.getStatus())
    val status = _status.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus(isAccessibilityReady: Boolean = true) {
        val current = enforcementRepository.getStatus()
        val isDo = enforcementRepository.isDeviceOwner()
        
        val snapshot = enforcementRepository.getSnapshot()
        val owner = enforcementRepository.getDeviceEnforcementOwnerId()
        val isSessionActive = owner == userId && snapshot != null

        val next = when {
            isSessionActive -> {
                if (isAccessibilityReady) EnforcementStatus.ENFORCEMENT_ACTIVE 
                else EnforcementStatus.ENFORCEMENT_DEGRADED
            }
            !isAccessibilityReady && !isDo -> EnforcementStatus.NOT_DEVICE_OWNER
            else -> EnforcementStatus.DEVICE_OWNER_READY
        }
        
        if (next != current) {
            updateStatus(next)
        }
    }

    suspend fun startEnforcement(tagId: String? = null) = globalMutex.withLock {
        Log.d(TAG, "Starting enforcement for $userId")
        
        // Check for device owner conflict
        val currentOwner = enforcementRepository.getDeviceEnforcementOwnerId()
        if (currentOwner != null && currentOwner != userId) {
            Log.e(TAG, "Device enforcement already owned by $currentOwner")
            updateStatus(EnforcementStatus.ENFORCEMENT_FAILED)
            return@withLock
        }

        // 1. Resolve Phase 3 policy
        val apps = inventoryRepository.getInstalledApps()
        val userBlockedSet = policyRepository.getBlockedApps()
        val resolvedPolicies = PolicyEngine.resolveList(apps, userBlockedSet)

        // 2. Create immutable snapshot
        val snapshot = EnforcementSnapshot(
            sessionId = UUID.randomUUID().toString(),
            userId = userId,
            timestamp = System.currentTimeMillis(),
            policies = resolvedPolicies
        )

        // 3. Persist snapshot
        enforcementRepository.saveSnapshot(snapshot)
        enforcementRepository.setDeviceEnforcementOwnerId(userId)
        
        // 4. Create history record
        try {
            val record = FocusSessionRecord(
                sessionId = snapshot.sessionId,
                userId = userId,
                tagId = tagId,
                startAt = snapshot.timestamp,
                status = SessionStatus.IN_PROGRESS
            )
            sessionHistoryRepository.createSession(record)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session history record: ${e.message}")
        }

        // 5. Invoke Strategy
        val result = strategy.apply(snapshot)
        
        // 6. Update Status and Ledger
        handleEnforcementResult(result)
    }

    suspend fun stopEnforcement() = globalMutex.withLock {
        Log.d(TAG, "Stopping enforcement for $userId")

        // Check for device ownership
        val currentOwner = enforcementRepository.getDeviceEnforcementOwnerId()
        if (currentOwner != userId) {
            Log.w(TAG, "Cannot stop: Device enforcement is owned by $currentOwner")
            return@withLock
        }

        val snapshot = enforcementRepository.getSnapshot()
        val ledger = enforcementRepository.getLedger()
        
        val result = strategy.release(ledger)
        if (result is EnforcementResult.Success || result is EnforcementResult.Simulated) {
            // Commit history record
            try {
                snapshot?.let {
                    sessionHistoryRepository.completeSession(
                        sessionId = it.sessionId,
                        status = SessionStatus.COMPLETED,
                        endAt = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to commit session history: ${e.message}")
            }

            enforcementRepository.saveLedger(EnforcementLedger())
            enforcementRepository.saveSnapshot(null)
            enforcementRepository.setDeviceEnforcementOwnerId(null)
            updateStatus(EnforcementStatus.IDLE)
        } else {
            updateStatus(EnforcementStatus.CLEANUP_PENDING)
        }
    }

    suspend fun reconcile() {
        Log.d(TAG, "Reconciling enforcement for $userId - entry")
        
        Log.d(TAG, "Reconciling enforcement for $userId - waiting for lock")
        globalMutex.withLock {
            Log.d(TAG, "Reconciling enforcement for $userId - lock acquired")

            // Check for device ownership
            val currentOwner = enforcementRepository.getDeviceEnforcementOwnerId()
            if (currentOwner != userId) {
                Log.w(TAG, "Cannot reconcile: Device enforcement is owned by $currentOwner")
                return@withLock
            }

            val snapshot = enforcementRepository.getSnapshot()
            if (snapshot == null) {
                Log.d(TAG, "No active session to reconcile")
                return@withLock
            }

            val ledger = enforcementRepository.getLedger()
            
            Log.d(TAG, "Reconciling enforcement for $userId - strategy start")
            val result = strategy.reconcile(snapshot, ledger)
            Log.d(TAG, "Reconciling enforcement for $userId - strategy end")
            
            handleEnforcementResult(result)
            Log.d(TAG, "Reconciling enforcement for $userId - state published")
        }
    }

    /**
     * Checks if there's an active enforcement snapshot but the focus state is NORMAL.
     * If so, marks the historical session as INTERRUPTED and cleans up.
     */
    suspend fun checkAndHandleOrphans() = globalMutex.withLock {
        val snapshot = enforcementRepository.getSnapshot()
        val owner = enforcementRepository.getDeviceEnforcementOwnerId()
        
        if (snapshot != null && owner == userId) {
            Log.i(TAG, "Orphaned session detected for $userId. Cleaning up.")
            try {
                sessionHistoryRepository.interruptSession(snapshot.sessionId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark orphan session in history: ${e.message}")
            }
            
            // Clean up state
            enforcementRepository.saveLedger(EnforcementLedger())
            enforcementRepository.saveSnapshot(null)
            enforcementRepository.setDeviceEnforcementOwnerId(null)
            updateStatus(EnforcementStatus.IDLE)
        }
    }

    private fun handleEnforcementResult(result: EnforcementResult) {
        val newStatus = when (result) {
            is EnforcementResult.Success -> {
                enforcementRepository.saveLedger(result.appliedLedger)
                EnforcementStatus.ENFORCEMENT_ACTIVE
            }
            is EnforcementResult.Simulated -> {
                enforcementRepository.saveLedger(result.appliedLedger)
                EnforcementStatus.ENFORCEMENT_SIMULATED
            }
            EnforcementResult.PartialFailure -> EnforcementStatus.ENFORCEMENT_DEGRADED
            EnforcementResult.Failure -> EnforcementStatus.ENFORCEMENT_FAILED
            EnforcementResult.Unavailable -> EnforcementStatus.ENFORCEMENT_FAILED
        }
        updateStatus(newStatus)
    }

    private fun updateStatus(newStatus: EnforcementStatus) {
        _status.update { newStatus }
        enforcementRepository.saveStatus(newStatus)
    }
}
