package com.focustag.app.domain

import com.focustag.app.data.model.FocusSessionState
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.model.FocusTransition

object FocusStateEngine {

    /**
     * Determines the logical transition for an incoming NFC tag event based on Protocol v1.
     * This is a pure decision layer.
     */
    fun determineTransition(currentState: FocusSessionState, incomingTag: String): FocusTransition {
        val normalized = NfcProtocol.normalize(incomingTag) ?: return FocusTransition.Ignore

        // 1. Simulation Bypass (simulated_tag_01 is NOT in the physical registry)
        if (normalized == "simulated_tag_01") {
            return when (currentState.focusState) {
                FocusState.NORMAL -> FocusTransition.Start(normalized)
                FocusState.FOCUS_ACTIVE -> {
                    if (normalized == currentState.activeTagId) FocusTransition.Stop
                    else FocusTransition.Ignore
                }
            }
        }

        // 2. Protocol v1 Registry Check: Unknown UID -> IGNORE
        if (!NfcProtocol.isRegistered(normalized)) {
            return FocusTransition.Ignore
        }

        // 3. Registered Tag Transitions
        return when (currentState.focusState) {
            FocusState.NORMAL -> {
                // No active session + registered tag -> START
                FocusTransition.Start(normalized)
            }
            FocusState.FOCUS_ACTIVE -> {
                if (normalized == currentState.activeTagId) {
                    // Active + same registered tag -> STOP
                    FocusTransition.Stop
                } else {
                    // Active + different registered tag -> IGNORE (Session-bound tag protocol)
                    FocusTransition.Ignore
                }
            }
        }
    }

    /**
     * Toggles the focus state based on the current state and a tag event.
     * Preserved for compatibility with existing FocusViewModel.
     */
    fun calculateNextState(currentState: FocusSessionState, tagId: String?): FocusSessionState {
        val transition = determineTransition(currentState, tagId ?: "")
        return when (transition) {
            is FocusTransition.Start -> FocusSessionState(
                focusState = FocusState.FOCUS_ACTIVE,
                activeTagId = transition.tagId
            )
            is FocusTransition.Stop -> FocusSessionState(
                focusState = FocusState.NORMAL,
                activeTagId = null
            )
            else -> currentState // IGNORE
        }
    }
}
