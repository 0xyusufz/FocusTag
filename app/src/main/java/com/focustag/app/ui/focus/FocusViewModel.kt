package com.focustag.app.ui.focus

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.model.FocusSessionState
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.repository.AppInventoryRepository
import com.focustag.app.data.repository.AppPolicyRepository
import com.focustag.app.data.repository.FocusRepository
import com.focustag.app.domain.FocusStateEngine
import com.focustag.app.domain.PolicyEngine
import com.focustag.app.domain.enforcement.EnforcementAbstraction
import com.focustag.app.domain.enforcement.EnforcementHealthSnapshot
import com.focustag.app.domain.enforcement.EnforcementHealthState
import com.focustag.app.domain.enforcement.EnforcementRuntime
import com.focustag.app.domain.enforcement.RecoveryResult
import com.focustag.app.data.model.EnforcementPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FocusViewModel(
    private val focusRepository: FocusRepository,
    private val appInventoryRepository: AppInventoryRepository? = null,
    private val appPolicyRepository: AppPolicyRepository? = null,
    private val enforcementAbstraction: EnforcementAbstraction? = null,
    private val enforcementRuntime: EnforcementRuntime? = null
) : ViewModel() {

    private val _focusState = MutableStateFlow(focusRepository.getFocusSessionState())
    val focusState = _focusState.asStateFlow()

    private val _enforcementHealth = MutableStateFlow(EnforcementHealthSnapshot())
    val enforcementHealth = _enforcementHealth.asStateFlow()

    private val _recoveryResult = MutableStateFlow<RecoveryResult?>(null)
    val recoveryResult = _recoveryResult.asStateFlow()

    private val healthListener: ((EnforcementHealthSnapshot) -> Unit) = { snapshot ->
        _enforcementHealth.value = snapshot
    }

    init {
        enforcementRuntime?.addHealthListener(healthListener)
        reinitializeActiveEnforcementIfNeeded()
    }

    fun onSimulatedTagTap() {
        viewModelScope.launch {
            val currentState = _focusState.value
            // Use a fixed simulated tag ID for Phase 4
            val simulatedTagId = "simulated_tag_01"

            val nextState = FocusStateEngine.calculateNextState(currentState, simulatedTagId)

            // Persist
            focusRepository.saveFocusSessionState(nextState)

            // Update UI
            _focusState.update { nextState }

            syncEnforcement(currentState, nextState)
        }
    }

    private fun reinitializeActiveEnforcementIfNeeded() {
        val persistedState = _focusState.value
        if (persistedState.focusState != FocusState.FOCUS_ACTIVE) {
            return
        }

        viewModelScope.launch {
            syncEnforcement(
                currentState = FocusSessionState(),
                nextState = persistedState
            )
        }
    }

    private fun syncEnforcement(
        currentState: FocusSessionState,
        nextState: FocusSessionState
    ) {
        val inventoryRepository = appInventoryRepository
        val policyRepository = appPolicyRepository
        val enforcement = enforcementAbstraction

        if (inventoryRepository != null && policyRepository != null && enforcement != null) {
            try {
                val resolvedPolicies = PolicyEngine.resolveList(
                    inventoryRepository.getInstalledApps(),
                    policyRepository.getBlockedApps()
                )

                FocusStateEngine.syncEnforcement(
                    previousState = currentState,
                    nextState = nextState,
                    resolvedPolicies = resolvedPolicies,
                    enforcementAbstraction = enforcement
                )

                val runtime = enforcementRuntime
                if (runtime != null) {
                    val policy = EnforcementPolicy.from(nextState, resolvedPolicies)
                    val health = runtime.checkHealth()
                    _enforcementHealth.value = health
                    if (health.overallState == EnforcementHealthState.FAILED && nextState.focusState == FocusState.FOCUS_ACTIVE) {
                        val recovery = runtime.recover(policy)
                        _recoveryResult.value = recovery
                        _enforcementHealth.value = runtime.currentHealth
                    }
                    if (nextState.focusState != FocusState.FOCUS_ACTIVE) {
                        _enforcementHealth.value = runtime.currentHealth
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to sync enforcement state", error)
            }
        }
    }

    override fun onCleared() {
        enforcementRuntime?.removeHealthListener(healthListener)
        enforcementRuntime?.close()
        super.onCleared()
    }

    private companion object {
        const val TAG = "FocusViewModel"
    }
}
