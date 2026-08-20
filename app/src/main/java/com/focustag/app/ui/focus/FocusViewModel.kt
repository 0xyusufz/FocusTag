package com.focustag.app.ui.focus

import androidx.lifecycle.ViewModel
import com.focustag.app.data.model.FocusSessionState
import com.focustag.app.data.repository.FocusRepository
import com.focustag.app.domain.FocusStateEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FocusViewModel(private val focusRepository: FocusRepository) : ViewModel() {

    private val _focusState = MutableStateFlow(focusRepository.getFocusSessionState())
    val focusState = _focusState.asStateFlow()

    fun onSimulatedTagTap() {
        val currentState = _focusState.value
        // Use a fixed simulated tag ID for Phase 4
        val simulatedTagId = "simulated_tag_01"
        
        val nextState = FocusStateEngine.calculateNextState(currentState, simulatedTagId)
        
        // Persist
        focusRepository.saveFocusSessionState(nextState)
        
        // Update UI
        _focusState.update { nextState }
    }
}
