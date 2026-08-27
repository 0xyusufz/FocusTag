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
                // Activate Focus Mode with the physical/simulated tag ID
                FocusSessionState(
                    focusState = FocusState.FOCUS_ACTIVE,
                    activeTagId = tagId
                )
            }
            FocusState.FOCUS_ACTIVE -> {
                // Deactivate Focus Mode ONLY if same tag is used
                if (tagId == currentState.activeTagId) {
                    FocusSessionState(
                        focusState = FocusState.NORMAL,
                        activeTagId = null
                    )
                } else {
                    // Mismatched tag: remain ACTIVE
                    currentState
                }
            }
        }
    }
}
