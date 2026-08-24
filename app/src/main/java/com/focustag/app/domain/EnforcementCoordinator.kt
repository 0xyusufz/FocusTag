package com.focustag.app.domain

import android.util.Log
import com.focustag.app.data.model.EnforcementLedger
import com.focustag.app.data.model.EnforcementResult
import com.focustag.app.data.model.EnforcementSnapshot
import com.focustag.app.data.model.EnforcementStatus
import com.focustag.app.data.repository.AppInventoryRepository
import com.focustag.app.data.repository.AppPolicyRepository
import com.focustag.app.data.repository.EnforcementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

private const val TAG = "EnforcementCoord"

class EnforcementCoordinator(
    private val userId: String,
    private val inventoryRepository: AppInventoryRepository,
    private val policyRepository: AppPolicyRepository,
    private val enforcementRepository: EnforcementRepository,
    private val strategy: EnforcementStrategy
) {

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

    suspend fun startEnforcement() {
        Log.d(TAG, "Starting enforcement for $userId")
        
        // Check for device owner conflict
        val currentOwner = enforcementRepository.getDeviceEnforcementOwnerId()
        if (currentOwner != null && currentOwner != userId) {
            Log.e(TAG, "Device enforcement already owned by $currentOwner")
            updateStatus(EnforcementStatus.ENFORCEMENT_FAILED)
            return
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
        
        // 4. Invoke Strategy
        val result = strategy.apply(snapshot)
        
        // 5. Update Status and Ledger
        handleEnforcementResult(result)
    }

    suspend fun stopEnforcement() {
        Log.d(TAG, "Stopping enforcement for $userId")

        // Check for device ownership
        val currentOwner = enforcementRepository.getDeviceEnforcementOwnerId()
        if (currentOwner != userId) {
            Log.w(TAG, "Cannot stop: Device enforcement is owned by $currentOwner")
            return
        }

        val ledger = enforcementRepository.getLedger()
        
        val result = strategy.release(ledger)
        if (result is EnforcementResult.Success || result is EnforcementResult.Simulated) {
            enforcementRepository.saveLedger(EnforcementLedger())
            enforcementRepository.saveSnapshot(null)
            enforcementRepository.setDeviceEnforcementOwnerId(null)
            updateStatus(EnforcementStatus.IDLE)
        } else {
            updateStatus(EnforcementStatus.CLEANUP_PENDING)
        }
    }

    suspend fun reconcile() {
        Log.d(TAG, "Reconciling enforcement for $userId")

        // Check for device ownership
        val currentOwner = enforcementRepository.getDeviceEnforcementOwnerId()
        if (currentOwner != userId) {
            Log.w(TAG, "Cannot reconcile: Device enforcement is owned by $currentOwner")
            return
        }

        val snapshot = enforcementRepository.getSnapshot()
        if (snapshot == null) {
            Log.d(TAG, "No active session to reconcile")
            return
        }

        val ledger = enforcementRepository.getLedger()
        val result = strategy.reconcile(snapshot, ledger)
        handleEnforcementResult(result)
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
