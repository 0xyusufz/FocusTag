package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementLifecycleState
import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.FocusState
import org.junit.Assert.assertEquals
import org.junit.Test

class EnforcementStateMachineTest {

    private val policy = EnforcementPolicy(
        focusState = FocusState.FOCUS_ACTIVE,
        activeTagId = "tag",
        blockedPackages = setOf("com.example.blocked"),
        protectedPackages = setOf("com.focustag.app"),
        allowedPackages = emptySet()
    )

    @Test
    fun startAndStopFollowsValidLifecycle() {
        val stateMachine = EnforcementStateMachine(logger = TestEnforcementLogger())

        stateMachine.transition(EnforcementEvent.StartRequested(policy))
        assertEquals(EnforcementLifecycleState.STARTING, stateMachine.currentState.lifecycleState)

        stateMachine.transition(EnforcementEvent.StartSucceeded)
        assertEquals(EnforcementLifecycleState.ACTIVE, stateMachine.currentState.lifecycleState)

        stateMachine.transition(EnforcementEvent.StopRequested)
        assertEquals(EnforcementLifecycleState.STOPPING, stateMachine.currentState.lifecycleState)

        stateMachine.transition(EnforcementEvent.StopSucceeded)
        assertEquals(EnforcementLifecycleState.INACTIVE, stateMachine.currentState.lifecycleState)
    }

    @Test
    fun invalidTransitionIsIgnored() {
        val stateMachine = EnforcementStateMachine(logger = TestEnforcementLogger())

        stateMachine.transition(EnforcementEvent.StopRequested)

        assertEquals(EnforcementLifecycleState.INACTIVE, stateMachine.currentState.lifecycleState)
    }

    @Test
    fun startFailureMovesToRecoveryRequired() {
        val stateMachine = EnforcementStateMachine(logger = TestEnforcementLogger())

        stateMachine.transition(EnforcementEvent.StartRequested(policy))
        stateMachine.transition(EnforcementEvent.StartFailed("unsupported"))

        assertEquals(EnforcementLifecycleState.RECOVERY_REQUIRED, stateMachine.currentState.lifecycleState)
        assertEquals("unsupported", stateMachine.currentState.lastError)
    }
}

private class TestEnforcementLogger : EnforcementLogger {
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
