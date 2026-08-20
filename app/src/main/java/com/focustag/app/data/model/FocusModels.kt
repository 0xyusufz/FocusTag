package com.focustag.app.data.model

enum class FocusState {
    NORMAL,
    FOCUS_ACTIVE
}

data class FocusSessionState(
    val focusState: FocusState = FocusState.NORMAL,
    val activeTagId: String? = null
)
