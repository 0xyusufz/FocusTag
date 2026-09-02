package com.focustag.app.ui.focus

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.model.AccessibilityCapability
import com.focustag.app.data.model.EnforcementStatus
import com.focustag.app.data.model.FocusSessionState
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.model.FocusTransition
import com.focustag.app.data.model.NfcCapability
import com.focustag.app.data.repository.FocusRepository
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.domain.EnforcementCoordinator
import com.focustag.app.domain.FocusStateEngine
import com.focustag.app.util.AccessibilityCapabilityChecker
import com.focustag.app.util.NfcCapabilityChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

open class FocusViewModel(
    private val context: Context?,
    private val focusRepository: FocusRepository,
    private val enforcementCoordinator: EnforcementCoordinator
) : ViewModel() {

    protected val _focusState = MutableStateFlow(focusRepository.getFocusSessionState())
    val focusState = _focusState.asStateFlow()

    protected val _accessibilityCapability = MutableStateFlow(AccessibilityCapability.ACCESSIBILITY_UNAVAILABLE)
    val accessibilityCapability = _accessibilityCapability.asStateFlow()

    protected val _nfcCapability = MutableStateFlow(NfcCapability.NFC_UNAVAILABLE)
    val nfcCapability = _nfcCapability.asStateFlow()

    protected val _isTransitioning = MutableStateFlow(false)
    val isTransitioning = _isTransitioning.asStateFlow()

    val enforcementStatus = enforcementCoordinator.status

    init {
        refreshAccessibilityCapability()
        refreshNfcCapability()
        
        // Initial reconciliation if app was killed while ACTIVE
        viewModelScope.launch {
            if (_focusState.value.focusState == FocusState.FOCUS_ACTIVE) {
                _isTransitioning.update { true }
                try {
                    enforcementCoordinator.reconcile()
                    
                    // Recover from ghost-active state if reconciliation determines enforcement is not active
                    val status = enforcementCoordinator.status.value
                    if (!isEnforcementActive(status)) {
                        Log.i("FocusViewModel", "Recovery: Enforcement not active. Resetting FocusState to NORMAL.")
                        val recoveredState = FocusSessionState(FocusState.NORMAL, null)
                        focusRepository.saveFocusSessionState(recoveredState)
                        _focusState.update { recoveredState }
                    }
                } finally {
                    _isTransitioning.update { false }
                }
            } else {
                enforcementCoordinator.checkAndHandleOrphans()
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

    open fun refreshAccessibilityCapability() {
        _accessibilityCapability.update { 
            AccessibilityCapabilityChecker.checkAccessibilityCapability(context!!)
        }
    }

    open fun refreshNfcCapability() {
        _nfcCapability.update {
            NfcCapabilityChecker.checkNfcCapability(context!!)
        }
    }

    open fun refreshEnforcementStatus() {
        val isReady = _accessibilityCapability.value == AccessibilityCapability.ACCESSIBILITY_READY
        enforcementCoordinator.refreshStatus(isReady)
    }

    fun onSimulatedTagTap() {
        onTagEvent("simulated_tag_01")
    }

    fun onTagEvent(tagId: String) {
        if (_isTransitioning.value) return

        val currentState = _focusState.value
        val transition = FocusStateEngine.determineTransition(currentState, tagId)
        
        if (transition is FocusTransition.Ignore) {
            Log.d("FocusViewModel", "NFC Protocol v1: Ignoring tag $tagId")
            return
        }
        
        // Guard: If we are starting, require accessibility to be READY
        val requiresAccessibility = transition is FocusTransition.Start
        
        if (requiresAccessibility && _accessibilityCapability.value != AccessibilityCapability.ACCESSIBILITY_READY) {
            Log.w("FocusViewModel", "NFC Protocol v1: Cannot start - Accessibility not ready")
            refreshAccessibilityCapability()
            return
        }

        viewModelScope.launch {
            _isTransitioning.update { true }
            try {
                executeTransition(transition)
            } catch (e: Exception) {
                Log.e("FocusViewModel", "NFC transition failed: ${e.message}", e)
            } finally {
                _isTransitioning.update { false }
            }
        }
    }

    private suspend fun executeTransition(transition: FocusTransition) {
        when (transition) {
            is FocusTransition.Start -> {
                enforcementCoordinator.startEnforcement(transition.tagId)
                if (isEnforcementActive()) {
                    val newState = FocusSessionState(FocusState.FOCUS_ACTIVE, transition.tagId)
                    focusRepository.saveFocusSessionState(newState)
                    _focusState.update { newState }
                }
            }
            is FocusTransition.Stop -> {
                enforcementCoordinator.stopEnforcement()
                if (enforcementCoordinator.status.value == EnforcementStatus.IDLE) {
                    val newState = FocusSessionState(FocusState.NORMAL, null)
                    focusRepository.saveFocusSessionState(newState)
                    _focusState.update { newState }
                }
            }
            is FocusTransition.Ignore -> {}
        }
    }

    private fun isEnforcementActive(s: EnforcementStatus = enforcementCoordinator.status.value): Boolean {
        return s == EnforcementStatus.ENFORCEMENT_ACTIVE || 
               s == EnforcementStatus.ENFORCEMENT_SIMULATED ||
               s == EnforcementStatus.ENFORCEMENT_DEGRADED
    }
}
