package com.focustag.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.focustag.app.data.model.FocusSessionState
import com.focustag.app.data.model.FocusState

class FocusRepository(private val context: Context, private val userId: String) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("focus_prefs_$userId", Context.MODE_PRIVATE)
    }

    private companion object {
        const val KEY_FOCUS_STATE = "focus_state"
        const val KEY_ACTIVE_TAG_ID = "active_tag_id"
    }

    fun getFocusSessionState(): FocusSessionState {
        val stateName = prefs.getString(KEY_FOCUS_STATE, FocusState.NORMAL.name) ?: FocusState.NORMAL.name
        val focusState = try {
            FocusState.valueOf(stateName)
        } catch (e: Exception) {
            FocusState.NORMAL
        }
        val activeTagId = prefs.getString(KEY_ACTIVE_TAG_ID, null)
        return FocusSessionState(focusState, activeTagId)
    }

    fun saveFocusSessionState(state: FocusSessionState) {
        prefs.edit().apply {
            putString(KEY_FOCUS_STATE, state.focusState.name)
            putString(KEY_ACTIVE_TAG_ID, state.activeTagId)
            apply()
        }
    }
}
