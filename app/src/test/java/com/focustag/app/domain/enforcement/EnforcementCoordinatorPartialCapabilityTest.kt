package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementLifecycleState
import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.FocusState
import org.junit.Assert.assertEquals
import org.junit.Test

class EnforcementCoordinatorPartialCapabilityTest {

    @Test
    fun unsupportedSecondaryModulesDoNotPreventActiveAppBlocking() {
        val coordinator = EnforcementCoordinator(
            appBlockingEnforcer = StartedAppBlockingEnforcer,
            visibilityControlEnforcer = unsupportedVisibilityControlEnforcer,
            networkRestrictionEnforcer = unsupportedNetworkRestrictionEnforcer,
            escapeProtectionEnforcer = unsupportedEscapeProtectionEnforcer,
            stateMachine = EnforcementStateMachine(logger = PartialCapabilityTestLogger()),
            logger = PartialCapabilityTestLogger()
        )

        val state = coordinator.start(activePolicy())

        assertEquals(EnforcementLifecycleState.ACTIVE, state.lifecycleState)
        assertEquals(
            EnforcementModuleStatus.STARTED,
            coordinator.capabilityStatuses[EnforcementCapability.APP_BLOCKING]
        )
        assertEquals(
            EnforcementModuleStatus.UNSUPPORTED,
            coordinator.capabilityStatuses[EnforcementCapability.VISIBILITY_CONTROL]
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

private object StartedAppBlockingEnforcer : AppBlockingEnforcer {
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

private val unsupportedVisibilityControlEnforcer = object : VisibilityControlEnforcer {
    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        return unsupported(EnforcementCapability.VISIBILITY_CONTROL)
    }

    override fun stop(): EnforcementModuleResult {
        return stopped(EnforcementCapability.VISIBILITY_CONTROL)
    }
}

private val unsupportedNetworkRestrictionEnforcer = object : NetworkRestrictionEnforcer {
    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        return unsupported(EnforcementCapability.NETWORK_RESTRICTION)
    }

    override fun stop(): EnforcementModuleResult {
        return stopped(EnforcementCapability.NETWORK_RESTRICTION)
    }
}

private val unsupportedEscapeProtectionEnforcer = object : EscapeProtectionEnforcer {
    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        return unsupported(EnforcementCapability.ESCAPE_PROTECTION)
    }

    override fun stop(): EnforcementModuleResult {
        return stopped(EnforcementCapability.ESCAPE_PROTECTION)
    }
}

private fun unsupported(capability: EnforcementCapability): EnforcementModuleResult {
    return EnforcementModuleResult(
        capability = capability,
        status = EnforcementModuleStatus.UNSUPPORTED
    )
}

private fun stopped(capability: EnforcementCapability): EnforcementModuleResult {
    return EnforcementModuleResult(
        capability = capability,
        status = EnforcementModuleStatus.STOPPED
    )
}

private class PartialCapabilityTestLogger : EnforcementLogger {
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
