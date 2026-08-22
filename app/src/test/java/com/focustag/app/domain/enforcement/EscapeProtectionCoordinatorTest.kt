package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementLifecycleState
import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.FocusState
import org.junit.Assert.assertEquals
import org.junit.Test

class EscapeProtectionCoordinatorTest {

    @Test
    fun degradedEscapeProtectionDoesNotStopAppBlocking() {
        val escapeEnforcer = RecordingEscapeProtectionEnforcer(
            EnforcementModuleStatus.DEGRADED
        )
        val coordinator = EnforcementCoordinator(
            appBlockingEnforcer = StartedAppBlockingEnforcerForEscapeTest,
            visibilityControlEnforcer = UnsupportedVisibilityForEscapeTest,
            networkRestrictionEnforcer = UnsupportedNetworkForEscapeTest,
            escapeProtectionEnforcer = escapeEnforcer,
            stateMachine = EnforcementStateMachine(logger = EscapeTestLogger()),
            logger = EscapeTestLogger()
        )

        val state = coordinator.start(activePolicy())

        assertEquals(EnforcementLifecycleState.ACTIVE, state.lifecycleState)
        assertEquals(1, escapeEnforcer.startCount)
    }

    @Test
    fun stoppingFocusStopsEscapeMonitoring() {
        val escapeEnforcer = RecordingEscapeProtectionEnforcer(
            EnforcementModuleStatus.STARTED
        )
        val coordinator = EnforcementCoordinator(
            appBlockingEnforcer = StartedAppBlockingEnforcerForEscapeTest,
            visibilityControlEnforcer = UnsupportedVisibilityForEscapeTest,
            networkRestrictionEnforcer = UnsupportedNetworkForEscapeTest,
            escapeProtectionEnforcer = escapeEnforcer,
            stateMachine = EnforcementStateMachine(logger = EscapeTestLogger()),
            logger = EscapeTestLogger()
        )

        coordinator.start(activePolicy())
        val state = coordinator.stop()

        assertEquals(EnforcementLifecycleState.INACTIVE, state.lifecycleState)
        assertEquals(1, escapeEnforcer.stopCount)
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

private class RecordingEscapeProtectionEnforcer(
    private val startStatus: EnforcementModuleStatus
) : EscapeProtectionEnforcer {
    var startCount = 0
    var stopCount = 0

    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        startCount += 1
        return EnforcementModuleResult(
            capability = EnforcementCapability.ESCAPE_PROTECTION,
            status = startStatus
        )
    }

    override fun stop(): EnforcementModuleResult {
        stopCount += 1
        return EnforcementModuleResult(
            capability = EnforcementCapability.ESCAPE_PROTECTION,
            status = EnforcementModuleStatus.STOPPED
        )
    }
}

private object StartedAppBlockingEnforcerForEscapeTest : AppBlockingEnforcer {
    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        return EnforcementModuleResult(
            capability = EnforcementCapability.APP_BLOCKING,
            status = EnforcementModuleStatus.STARTED
        )
    }

    override fun stop(): EnforcementModuleResult {
        return EnforcementModuleResult(
            capability = EnforcementCapability.APP_BLOCKING,
            status = EnforcementModuleStatus.STOPPED
        )
    }
}

private object UnsupportedVisibilityForEscapeTest : VisibilityControlEnforcer {
    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        return unsupported(EnforcementCapability.VISIBILITY_CONTROL)
    }

    override fun stop(): EnforcementModuleResult {
        return stopped(EnforcementCapability.VISIBILITY_CONTROL)
    }
}

private object UnsupportedNetworkForEscapeTest : NetworkRestrictionEnforcer {
    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        return unsupported(EnforcementCapability.NETWORK_RESTRICTION)
    }

    override fun stop(): EnforcementModuleResult {
        return stopped(EnforcementCapability.NETWORK_RESTRICTION)
    }
}

private fun unsupported(capability: EnforcementCapability): EnforcementModuleResult {
    return EnforcementModuleResult(capability, EnforcementModuleStatus.UNSUPPORTED)
}

private fun stopped(capability: EnforcementCapability): EnforcementModuleResult {
    return EnforcementModuleResult(capability, EnforcementModuleStatus.STOPPED)
}

private class EscapeTestLogger : EnforcementLogger {
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