package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementLifecycleState
import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.FocusState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryManagerTest {

    @Test
    fun healthyHealthDoesNotTriggerRecovery() {
        val appBlocking = FakeRecoverableAppBlocking(EnforcementModuleStatus.STARTED)
        val manager = manager(health(EnforcementHealthState.HEALTHY), appBlocking)

        val result = manager.attempt(activePolicy())

        assertEquals(RecoveryOutcome.NO_ACTION, result.outcome)
        assertEquals(0, appBlocking.recoveryCount)
    }

    @Test
    fun failedMonitoringWithAccessibilityAvailableRecoversAndVerifies() {
        val appBlocking = FakeRecoverableAppBlocking(EnforcementModuleStatus.STARTED)
        val manager = manager(health(EnforcementHealthState.FAILED), appBlocking)

        val result = manager.attempt(activePolicy())

        assertEquals(RecoveryOutcome.RECOVERED, result.outcome)
        assertEquals(1, appBlocking.recoveryCount)
    }

    @Test
    fun accessibilityFailureRequiresUserActionWithoutRetry() {
        val appBlocking = FakeRecoverableAppBlocking(EnforcementModuleStatus.STARTED)
        val manager = manager(
            health(
                EnforcementHealthState.FAILED,
                accessibilityAvailable = false
            ),
            appBlocking
        )

        val result = manager.attempt(activePolicy())
        val secondResult = manager.attempt(activePolicy())

        assertEquals(RecoveryOutcome.USER_ACTION_REQUIRED, result.outcome)
        assertEquals(RecoveryOutcome.USER_ACTION_REQUIRED, secondResult.outcome)
        assertEquals(0, appBlocking.recoveryCount)
    }

    @Test
    fun missingPolicyRequiresUserActionWithoutInventingPolicy() {
        val appBlocking = FakeRecoverableAppBlocking(EnforcementModuleStatus.STARTED)
        val manager = manager(
            health(
                EnforcementHealthState.FAILED,
                policyAvailable = false
            ),
            appBlocking
        )

        val result = manager.attempt(null)

        assertEquals(RecoveryOutcome.USER_ACTION_REQUIRED, result.outcome)
        assertEquals(0, appBlocking.recoveryCount)
    }

    @Test
    fun optionalUnsupportedCapabilityDoesNotTriggerRecovery() {
        val appBlocking = FakeRecoverableAppBlocking(EnforcementModuleStatus.STARTED)
        val manager = manager(health(EnforcementHealthState.DEGRADED), appBlocking)

        val result = manager.attempt(activePolicy())

        assertEquals(RecoveryOutcome.NO_ACTION, result.outcome)
        assertEquals(0, appBlocking.recoveryCount)
    }

    @Test
    fun failedRecoveryIsBoundedByAttemptsAndCooldown() {
        var now = 0L
        val appBlocking = FakeRecoverableAppBlocking(EnforcementModuleStatus.FAILED)
        val manager = manager(
            health(EnforcementHealthState.FAILED),
            appBlocking,
            nowMillis = { now },
            maxAttempts = 3,
            cooldownMillis = 10L
        )

        assertEquals(RecoveryOutcome.FAILED, manager.attempt(activePolicy()).outcome)
        assertEquals(RecoveryOutcome.COOLDOWN, manager.attempt(activePolicy()).outcome)

        now = 10L
        assertEquals(RecoveryOutcome.FAILED, manager.attempt(activePolicy()).outcome)
        now = 20L
        val exhausted = manager.attempt(activePolicy())

        assertEquals(RecoveryOutcome.EXHAUSTED, exhausted.outcome)
        assertTrue(appBlocking.recoveryCount <= 3)
    }

    @Test
    fun resetClearsAttemptLimit() {
        var now = 0L
        val appBlocking = FakeRecoverableAppBlocking(EnforcementModuleStatus.FAILED)
        val manager = manager(
            health(EnforcementHealthState.FAILED),
            appBlocking,
            nowMillis = { now },
            maxAttempts = 1,
            cooldownMillis = 0L
        )

        manager.attempt(activePolicy())
        assertEquals(RecoveryOutcome.EXHAUSTED, manager.attempt(activePolicy()).outcome)

        manager.reset()
        now = 1L
        assertEquals(RecoveryOutcome.FAILED, manager.attempt(activePolicy()).outcome)
    }

    private fun manager(
        snapshot: EnforcementHealthSnapshot,
        appBlocking: FakeRecoverableAppBlocking,
        nowMillis: () -> Long = { 0L },
        maxAttempts: Int = 3,
        cooldownMillis: Long = 30_000L
    ): DefaultRecoveryManager {
        return DefaultRecoveryManager(
            healthMonitor = FakeHealthMonitor(snapshot),
            appBlockingEnforcer = appBlocking,
            logger = RecoveryTestLogger(),
            nowMillis = nowMillis,
            maxAttempts = maxAttempts,
            cooldownMillis = cooldownMillis
        )
    }

    private fun health(
        state: EnforcementHealthState,
        accessibilityAvailable: Boolean = true,
        policyAvailable: Boolean = true
    ): EnforcementHealthSnapshot {
        return EnforcementHealthSnapshot(
            overallState = state,
            focusState = FocusState.FOCUS_ACTIVE,
            enforcementState = EnforcementLifecycleState.ACTIVE,
            appBlockingState = if (state == EnforcementHealthState.FAILED) {
                EnforcementModuleStatus.FAILED
            } else {
                EnforcementModuleStatus.STARTED
            },
            accessibilityAvailable = accessibilityAvailable,
            policyAvailable = policyAvailable
        )
    }

    private fun activePolicy(): EnforcementPolicy {
        return EnforcementPolicy(
            focusState = FocusState.FOCUS_ACTIVE,
            activeTagId = "tag",
            blockedPackages = setOf("com.example.blocked"),
            protectedPackages = setOf("com.focustag.app"),
            allowedPackages = emptySet()
        )
    }
}

private class FakeHealthMonitor(
    override var currentHealth: EnforcementHealthSnapshot
) : HealthMonitor {
    override fun start() = Unit
    override fun stop() = Unit
    override fun checkHealth(observation: EnforcementHealthObservation): EnforcementHealthSnapshot {
        return currentHealth
    }
}

private class FakeRecoverableAppBlocking(
    private val status: EnforcementModuleStatus
) : AppBlockingEnforcer {
    var recoveryCount = 0

    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        return EnforcementModuleResult(EnforcementCapability.APP_BLOCKING, status)
    }

    override fun stop(): EnforcementModuleResult {
        return EnforcementModuleResult(
            EnforcementCapability.APP_BLOCKING,
            EnforcementModuleStatus.STOPPED
        )
    }

    override fun recoverMonitoring(): EnforcementModuleResult {
        recoveryCount += 1
        return EnforcementModuleResult(EnforcementCapability.APP_BLOCKING, status)
    }
}

private class RecoveryTestLogger : EnforcementLogger {
    override fun transition(
        from: EnforcementLifecycleState,
        to: EnforcementLifecycleState,
        eventName: String
    ) = Unit

    override fun invalidTransition(
        from: EnforcementLifecycleState,
        eventName: String
    ) = Unit

    override fun moduleResult(result: EnforcementModuleResult) = Unit
}
