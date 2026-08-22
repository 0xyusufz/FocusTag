package com.focustag.app.platform.enforcement

import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.domain.enforcement.AppBlockingModule
import com.focustag.app.domain.enforcement.EnforcementCapability
import com.focustag.app.domain.enforcement.EnforcementCoordinator
import com.focustag.app.domain.enforcement.EnforcementHealthSnapshot
import com.focustag.app.domain.enforcement.EnforcementHealthObservation
import com.focustag.app.domain.enforcement.EnforcementModuleStatus
import com.focustag.app.domain.enforcement.EnforcementRuntime
import com.focustag.app.domain.enforcement.EnforcementState
import com.focustag.app.domain.enforcement.EnforcementLogger
import com.focustag.app.domain.enforcement.DefaultHealthMonitor
import com.focustag.app.domain.enforcement.DefaultRecoveryManager
import com.focustag.app.domain.enforcement.RecoveryManager
import com.focustag.app.domain.enforcement.RecoveryResult
import com.focustag.app.domain.enforcement.HealthMonitor
import com.focustag.app.domain.enforcement.EnforcementHealthState
import com.focustag.app.data.model.FocusState
import java.util.concurrent.CopyOnWriteArraySet

class AndroidEnforcementRuntime(
    private val coordinator: EnforcementCoordinator,
    private val appBlockingModule: AppBlockingModule,
    override val healthMonitor: HealthMonitor,
    private val recoveryManager: RecoveryManager,
    private val accessibilityDetector: AccessibilityForegroundAppDetector,
    private val logger: EnforcementLogger
) : EnforcementRuntime {

    private var activePolicy: EnforcementPolicy? = null
    private var serviceStateListener: ((AccessibilityServiceState) -> Unit)? = null
    private var serviceOperational = true
    private val healthListeners = CopyOnWriteArraySet<(EnforcementHealthSnapshot) -> Unit>()

    init {
        serviceStateListener = { state ->
            serviceOperational = state == AccessibilityServiceState.CONNECTED
            checkHealth()
            if (state == AccessibilityServiceState.CONNECTED &&
                currentHealth.overallState == EnforcementHealthState.FAILED
            ) {
                recover(activePolicy)
            }
        }
        AccessibilityForegroundAppEventBus.addServiceStateListener(requireNotNull(serviceStateListener))
    }

    override val currentState: EnforcementState
        get() = coordinator.currentState

    override val currentHealth: EnforcementHealthSnapshot
        get() = healthMonitor.currentHealth

    override fun start(policy: EnforcementPolicy): EnforcementState {
        activePolicy = policy
        healthMonitor.start()
        val state = coordinator.start(policy)
        checkHealth()
        return state
    }

    override fun stop(): EnforcementState {
        val state = coordinator.stop()
        activePolicy = null
        healthMonitor.stop()
        recoveryManager.reset()
        return state
    }

    override fun checkHealth(): EnforcementHealthSnapshot {
        val policy = activePolicy
        val statuses = coordinator.capabilityStatuses
        val snapshot = healthMonitor.checkHealth(
            EnforcementHealthObservation(
                focusState = policy?.focusState ?: FocusState.NORMAL,
                enforcementState = coordinator.currentState.lifecycleState,
                policy = policy,
                appBlockingStatus = statuses[EnforcementCapability.APP_BLOCKING],
                appBlockingMonitoringActive = appBlockingModule.isMonitoringActive() && serviceOperational,
                accessibilityAvailable = accessibilityDetector.hasRequiredPermission(),
                optionalCapabilities = statuses.filterKeys { it != EnforcementCapability.APP_BLOCKING },
                failureReason = coordinator.currentState.lastError
            )
        )
        healthListeners.forEach { listener -> listener(snapshot) }
        return snapshot
    }

    override fun recover(policy: EnforcementPolicy?): RecoveryResult {
        val result = recoveryManager.attempt(policy ?: activePolicy)
        checkHealth()
        return result
    }

    override fun addHealthListener(listener: (EnforcementHealthSnapshot) -> Unit) {
        healthListeners.add(listener)
    }

    override fun removeHealthListener(listener: (EnforcementHealthSnapshot) -> Unit) {
        healthListeners.remove(listener)
    }

    override fun close() {
        serviceStateListener?.let(AccessibilityForegroundAppEventBus::removeServiceStateListener)
        serviceStateListener = null
        healthListeners.clear()
        if (activePolicy != null) {
            stop()
        }
    }
}
