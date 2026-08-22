package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementLifecycleState
import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.EnforcementState

sealed class EnforcementEvent {
    data class StartRequested(val policy: EnforcementPolicy) : EnforcementEvent()
    data object StartSucceeded : EnforcementEvent()
    data class StartFailed(val reason: String) : EnforcementEvent()
    data object StopRequested : EnforcementEvent()
    data object StopSucceeded : EnforcementEvent()
    data class StopFailed(val reason: String) : EnforcementEvent()
    data object RecoverySucceeded : EnforcementEvent()
}

class EnforcementStateMachine(
    initialState: EnforcementState = EnforcementState(),
    private val logger: EnforcementLogger = NoOpEnforcementLogger
) {
    var currentState: EnforcementState = initialState
        private set

    fun transition(event: EnforcementEvent): EnforcementState {
        val previousState = currentState
        val nextState = nextState(previousState, event)

        if (nextState == previousState) {
            logger.invalidTransition(previousState.lifecycleState, event.name)
        } else {
            logger.transition(previousState.lifecycleState, nextState.lifecycleState, event.name)
            currentState = nextState
        }

        return currentState
    }

    private fun nextState(
        state: EnforcementState,
        event: EnforcementEvent
    ): EnforcementState {
        return when (state.lifecycleState) {
            EnforcementLifecycleState.INACTIVE -> when (event) {
                is EnforcementEvent.StartRequested -> EnforcementState(
                    lifecycleState = EnforcementLifecycleState.STARTING,
                    activePolicy = event.policy
                )
                else -> state
            }

            EnforcementLifecycleState.STARTING -> when (event) {
                EnforcementEvent.StartSucceeded -> state.copy(
                    lifecycleState = EnforcementLifecycleState.ACTIVE,
                    lastError = null
                )
                is EnforcementEvent.StartFailed -> state.copy(
                    lifecycleState = EnforcementLifecycleState.RECOVERY_REQUIRED,
                    lastError = event.reason
                )
                EnforcementEvent.StopRequested -> state.copy(
                    lifecycleState = EnforcementLifecycleState.STOPPING
                )
                else -> state
            }

            EnforcementLifecycleState.ACTIVE -> when (event) {
                EnforcementEvent.StopRequested -> state.copy(
                    lifecycleState = EnforcementLifecycleState.STOPPING
                )
                else -> state
            }

            EnforcementLifecycleState.STOPPING -> when (event) {
                EnforcementEvent.StopSucceeded -> EnforcementState()
                is EnforcementEvent.StopFailed -> state.copy(
                    lifecycleState = EnforcementLifecycleState.RECOVERY_REQUIRED,
                    lastError = event.reason
                )
                else -> state
            }

            EnforcementLifecycleState.RECOVERY_REQUIRED -> when (event) {
                EnforcementEvent.StopRequested -> state.copy(
                    lifecycleState = EnforcementLifecycleState.STOPPING
                )
                EnforcementEvent.RecoverySucceeded -> EnforcementState()
                else -> state
            }
        }
    }

    private val EnforcementEvent.name: String
        get() = javaClass.simpleName
}
