package com.focustag.app.ui.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.model.AccessibilityCapability
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.repository.FocusRepository
import com.focustag.app.domain.EnforcementCoordinator
import com.focustag.app.domain.FocusStateEngine
import com.focustag.app.util.AccessibilityCapabilityChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FocusViewModel(
    private val context: Context,
    private val focusRepository: FocusRepository,
    private val enforcementCoordinator: EnforcementCoordinator
) : ViewModel() {

    private val _focusState = MutableStateFlow(focusRepository.getFocusSessionState())
    val focusState = _focusState.asStateFlow()

    private val _accessibilityCapability = MutableStateFlow(AccessibilityCapability.ACCESSIBILITY_UNAVAILABLE)
    val accessibilityCapability = _accessibilityCapability.asStateFlow()

    private val _isTransitioning = MutableStateFlow(false)
    val isTransitioning = _isTransitioning.asStateFlow()

    val enforcementStatus = enforcementCoordinator.status

    init {
        refreshAccessibilityCapability()
        
        // Initial reconciliation if app was killed while ACTIVE
        viewModelScope.launch {
            if (_focusState.value.focusState == FocusState.FOCUS_ACTIVE) {
                _isTransitioning.update { true }
                try {
                    enforcementCoordinator.reconcile()
                } finally {
                    _isTransitioning.update { false }
                }
            } else {
                refreshEnforcementStatus()
            }
        }

        // Monitor capability changes to trigger reconciliation or update status
        viewModelScope.launch {
            accessibilityCapability.collect { capability ->
                val isReady = capability == AccessibilityCapability.ACCESSIBILITY_READY
                enforcementCoordinator.refreshStatus(isReady)
                
                if (isReady && _focusState.value.focusState == FocusState.FOCUS_ACTIVE) {
                    enforcementCoordinator.reconcile()
                }
            }
        }
    }

    fun refreshAccessibilityCapability() {
        _accessibilityCapability.update { 
            AccessibilityCapabilityChecker.checkAccessibilityCapability(context)
        }
    }

    fun refreshEnforcementStatus() {
        val isReady = _accessibilityCapability.value == AccessibilityCapability.ACCESSIBILITY_READY
        enforcementCoordinator.refreshStatus(isReady)
    }

    fun onSimulatedTagTap() {
        if (_isTransitioning.value) return

        val currentState = _focusState.value
        // Use a fixed simulated tag ID for Phase 4
        val simulatedTagId = "simulated_tag_01"
        
        val nextState = FocusStateEngine.calculateNextState(currentState, simulatedTagId)
        
        // Guard: If transitioning to ACTIVE, require accessibility to be READY
        if (nextState.focusState == FocusState.FOCUS_ACTIVE && 
            _accessibilityCapability.value != AccessibilityCapability.ACCESSIBILITY_READY) {
            refreshAccessibilityCapability() // Force refresh in case system state changed
            return
        }

        // Persist FocusState
        focusRepository.saveFocusSessionState(nextState)
        
        // Orchestrate Enforcement
        viewModelScope.launch {
            _isTransitioning.update { true }
            try {
                if (nextState.focusState == FocusState.FOCUS_ACTIVE) {
                    enforcementCoordinator.startEnforcement()
                } else {
                    enforcementCoordinator.stopEnforcement()
                }
                
                // Update UI state last to ensure enforcement flow started
                _focusState.update { nextState }
            } finally {
                _isTransitioning.update { false }
            }
        }
    }
}
