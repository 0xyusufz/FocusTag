package com.focustag.app.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.model.AppCategory
import com.focustag.app.data.model.ResolvedPolicy
import com.focustag.app.data.repository.AppInventoryRepository
import com.focustag.app.data.repository.AppPolicyRepository
import com.focustag.app.domain.PolicyEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppSelectionUiState(
    val isLoading: Boolean = false,
    val resolvedPolicies: List<ResolvedPolicy> = emptyList(),
    val errorMessage: String? = null
)

class AppSelectionViewModel(
    private val inventoryRepository: AppInventoryRepository,
    private val policyRepository: AppPolicyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSelectionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val apps = inventoryRepository.getInstalledApps()
                val blocked = policyRepository.getBlockedApps()
                val resolved = PolicyEngine.resolveList(apps, blocked)
                _uiState.update { it.copy(isLoading = false, resolvedPolicies = resolved) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load apps") }
            }
        }
    }

    fun toggleAppSelection(packageName: String) {
        val policy = _uiState.value.resolvedPolicies.find { it.appInfo.packageName == packageName }
        if (policy?.appInfo?.category == AppCategory.ALLOWABLE) {
            policyRepository.toggleAppBlock(packageName)
            refreshApps()
        }
    }
}
