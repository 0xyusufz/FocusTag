package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementLifecycleState
import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.FocusState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementHealthMonitorTest {

    @Test
    fun activeCoreWithUnsupportedOptionalModulesIsHealthy() {
        val monitor = startedMonitor()

        val snapshot = monitor.checkHealth(observation())

        assertEquals(EnforcementHealthState.HEALTHY, snapshot.overallState)
        assertEquals(EnforcementModuleStatus.STARTED, snapshot.appBlockingState)
        assertTrue(snapshot.policyAvailable)
    }

    @Test
    fun degradedOptionalCapabilityDoesNotFailCoreHealth() {
        val monitor = startedMonitor()

        val snapshot = monitor.checkHealth(
            observation(
                optionalCapabilities = mapOf(
                    EnforcementCapability.VISIBILITY_CONTROL to EnforcementModuleStatus.UNSUPPORTED,
                    EnforcementCapability.NETWORK_RESTRICTION to EnforcementModuleStatus.UNSUPPORTED,
                    EnforcementCapability.ESCAPE_PROTECTION to EnforcementModuleStatus.DEGRADED
                )
            )
        )

        assertEquals(EnforcementHealthState.DEGRADED, snapshot.overallState)
        assertEquals(EnforcementModuleStatus.STARTED, snapshot.appBlockingState)
    }

    @Test
    fun failedAppBlockingProducesFailedHealth() {
        val monitor = startedMonitor()

        val snapshot = monitor.checkHealth(
            observation(
                appBlockingStatus = EnforcementModuleStatus.FAILED,
                appBlockingMonitoringActive = false,
                failureReason = "Foreground detector failed."
            )
        )

        assertEquals(EnforcementHealthState.FAILED, snapshot.overallState)
        assertEquals("Foreground detector failed.", snapshot.failureReason)
    }

    @Test
    fun unavailableAccessibilityProducesFailedHealth() {
        val monitor = startedMonitor()

        val snapshot = monitor.checkHealth(
            observation(accessibilityAvailable = false)
        )

        assertEquals(EnforcementHealthState.FAILED, snapshot.overallState)
        assertEquals(false, snapshot.accessibilityAvailable)
    }

    @Test
    fun unavailablePolicyProducesFailedHealth() {
        val monitor = startedMonitor()

        val snapshot = monitor.checkHealth(observation(policy = null))

        assertEquals(EnforcementHealthState.FAILED, snapshot.overallState)
        assertEquals(false, snapshot.policyAvailable)
    }

    @Test
    fun nonActiveCoordinatorStateProducesFailedHealth() {
        val monitor = startedMonitor()

        val snapshot = monitor.checkHealth(
            observation(enforcementState = EnforcementLifecycleState.STARTING)
        )

        assertEquals(EnforcementHealthState.FAILED, snapshot.overallState)
    }

    @Test
    fun inactiveFocusProducesInactiveHealth() {
        val monitor = startedMonitor()

        val snapshot = monitor.checkHealth(
            observation(focusState = FocusState.NORMAL)
        )

        assertEquals(EnforcementHealthState.INACTIVE, snapshot.overallState)
    }

    @Test
    fun restoredCoreAfterFailureReportsRecoveringThenHealthy() {
        val monitor = startedMonitor()
        monitor.checkHealth(
            observation(
                appBlockingStatus = EnforcementModuleStatus.FAILED,
                appBlockingMonitoringActive = false
            )
        )

        val recovering = monitor.checkHealth(observation())
        val healthy = monitor.checkHealth(observation())

        assertEquals(EnforcementHealthState.RECOVERING, recovering.overallState)
        assertEquals(EnforcementHealthState.HEALTHY, healthy.overallState)
    }

    @Test
    fun stopReturnsMonitorToInactiveWithoutStartingRecovery() {
        val monitor = startedMonitor()
        monitor.checkHealth(observation())

        monitor.stop()

        assertEquals(EnforcementHealthState.INACTIVE, monitor.currentHealth.overallState)
    }

    private fun startedMonitor(): DefaultHealthMonitor {
        return DefaultHealthMonitor().also { it.start() }
    }

    private fun observation(
        focusState: FocusState = FocusState.FOCUS_ACTIVE,
        policy: EnforcementPolicy? = activePolicy(),
        appBlockingStatus: EnforcementModuleStatus = EnforcementModuleStatus.STARTED,
        appBlockingMonitoringActive: Boolean = true,
        accessibilityAvailable: Boolean = true,
        enforcementState: EnforcementLifecycleState = if (focusState == FocusState.FOCUS_ACTIVE) {
            EnforcementLifecycleState.ACTIVE
        } else {
            EnforcementLifecycleState.INACTIVE
        },
        optionalCapabilities: Map<EnforcementCapability, EnforcementModuleStatus> = mapOf(
            EnforcementCapability.VISIBILITY_CONTROL to EnforcementModuleStatus.UNSUPPORTED,
            EnforcementCapability.NETWORK_RESTRICTION to EnforcementModuleStatus.UNSUPPORTED,
            EnforcementCapability.ESCAPE_PROTECTION to EnforcementModuleStatus.UNSUPPORTED
        ),
        failureReason: String? = null
    ): EnforcementHealthObservation {
        return EnforcementHealthObservation(
            focusState = focusState,
            enforcementState = enforcementState,
            policy = policy,
            appBlockingStatus = appBlockingStatus,
            appBlockingMonitoringActive = appBlockingMonitoringActive,
            accessibilityAvailable = accessibilityAvailable,
            optionalCapabilities = optionalCapabilities,
            failureReason = failureReason
        )
    }

    private fun activePolicy(): EnforcementPolicy {
        return EnforcementPolicy(
            focusState = FocusState.FOCUS_ACTIVE,
            activeTagId = "tag",
            blockedPackages = setOf("com.example.blocked"),
            protectedPackages = setOf("com.focustag.app"),
            allowedPackages = setOf("com.example.allowed")
        )
    }
}
