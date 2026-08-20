package com.focustag.app.domain

import com.focustag.app.data.model.FocusSessionState
import com.focustag.app.data.model.FocusState

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
}
