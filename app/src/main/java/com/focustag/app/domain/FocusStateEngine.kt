package com.focustag.app.domain

import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.EnforcementState
import com.focustag.app.data.model.FocusSessionState
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.model.ResolvedPolicy
import com.focustag.app.domain.enforcement.EnforcementAbstraction

object FocusStateEngine {

    /**
     * Toggles the focus state based on the current state and a tag event.
     * In Phase 4, we simply toggle between NORMAL and FOCUS_ACTIVE.
     */
    fun calculateNextState(currentState: FocusSessionState, tagId: String?): FocusSessionState {
        return when (currentState.focusState) {
            FocusState.NORMAL -> {
                // Activate Focus Mode
                FocusSessionState(
                    focusState = FocusState.FOCUS_ACTIVE,
                    activeTagId = tagId
                )
            }
            FocusState.FOCUS_ACTIVE -> {
                // Deactivate Focus Mode
                // In future phases, we may check if tagId matches activeTagId
                FocusSessionState(
                    focusState = FocusState.NORMAL,
                    activeTagId = null
                )
            }
        }
    }

    fun syncEnforcement(
        previousState: FocusSessionState,
        nextState: FocusSessionState,
        resolvedPolicies: List<ResolvedPolicy>,
        enforcementAbstraction: EnforcementAbstraction
    ): EnforcementState {
        return when {
            previousState.focusState == FocusState.NORMAL &&
                nextState.focusState == FocusState.FOCUS_ACTIVE -> {
                val enforcementPolicy = EnforcementPolicy.from(nextState, resolvedPolicies)
                enforcementAbstraction.start(enforcementPolicy)
            }

            previousState.focusState == FocusState.FOCUS_ACTIVE &&
                nextState.focusState == FocusState.NORMAL -> {
                enforcementAbstraction.stop()
            }

            else -> enforcementAbstraction.currentState
        }
    }
}
