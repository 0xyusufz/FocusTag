package com.focustag.app.ui.focus

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.data.model.*
import com.focustag.app.data.repository.FocusRepository
import com.focustag.app.data.repository.NfcRepository
import com.focustag.app.data.repository.NfcRegistryCache
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.auth.auth
import com.focustag.app.domain.EnforcementCoordinator
import com.focustag.app.domain.FocusStateEngine
import com.focustag.app.domain.NfcProtocol
import com.focustag.app.util.AccessibilityCapabilityChecker
import com.focustag.app.util.NfcCapabilityChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

open class FocusViewModel(
    private val context: Context?,
    private val focusRepository: FocusRepository,
    private val enforcementCoordinator: EnforcementCoordinator,
    private val nfcRepository: NfcRepository? = null,
    private val currentUserIdProvider: () -> String? = {
        try { SupabaseModule.client.auth.currentSessionOrNull()?.user?.id } catch (e: Exception) { null }
    }
) : ViewModel() {

    private companion object {
        const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        const val MAX_CACHE_AGE_MS = 48 * 60 * 60 * 1000L // 48 hours
    }

    protected val _focusState = MutableStateFlow(focusRepository.getFocusSessionState())
    val focusState = _focusState.asStateFlow()

    protected val _accessibilityCapability = MutableStateFlow(AccessibilityCapability.ACCESSIBILITY_UNAVAILABLE)
    val accessibilityCapability = _accessibilityCapability.asStateFlow()

    protected val _nfcCapability = MutableStateFlow(NfcCapability.NFC_UNAVAILABLE)
    val nfcCapability = _nfcCapability.asStateFlow()

    protected val _isTransitioning = MutableStateFlow(false)
    val isTransitioning = _isTransitioning.asStateFlow()

    val enforcementStatus = enforcementCoordinator.status

    private var lastRefreshTime = 0L
    private var isRefreshing = false

    init {
        refreshAccessibilityCapability()
        refreshNfcCapability()
        
        loadInitialRegistry()
        refreshRegistry()

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

    private fun loadInitialRegistry() {
        val nfcRepo = nfcRepository
        if (nfcRepo == null) {
            NfcProtocol.setRegisteredTags(emptySet())
            return
        }
        
        // H1 FIX: We need current user's institution to verify cache identity before applying.
        val currentUserId = currentUserIdProvider()
        if (currentUserId == null) {
            Log.d("FocusViewModel", "No authenticated user. Physical NFC disabled.")
            NfcProtocol.setRegisteredTags(emptySet())
            return
        }

        val cache = nfcRepo.getCache()
        if (cache == null) {
            NfcProtocol.setRegisteredTags(emptySet())
            return
        }

        val now = System.currentTimeMillis()
        
        val verifiedProfilePrefs = context?.getSharedPreferences("verified_profile_$currentUserId", Context.MODE_PRIVATE)
        val verifiedInstitutionId = verifiedProfilePrefs?.getString("institution_id", null)

        // Only apply cache if the institution has been previously verified for this user
        if (verifiedInstitutionId == null || verifiedInstitutionId != cache.fetchedForInstitutionId) {
            Log.d("FocusViewModel", "Initial registry: institution mismatch or not yet verified locally.")
            NfcProtocol.setRegisteredTags(emptySet())
            return
        }

        // Age check: cache must be within 48-hour window
        if (now - cache.fetchedAtMillis > MAX_CACHE_AGE_MS) {
            Log.d("FocusViewModel", "Initial registry: cache expired.")
            NfcProtocol.setRegisteredTags(emptySet())
            return
        }

        Log.d("FocusViewModel", "Initial registry: Loaded ${cache.activeUids.size} tags for institution.")
        NfcProtocol.setRegisteredTags(cache.activeUids)
        // SURGICAL FIX: Do NOT restore lastRefreshTime from disk cache.
        // lastRefreshTime should only track the last successful network refresh in the current process.
        // This ensures Scenario B (Restart) always performs an authoritative refresh if online.
    }

    fun refreshRegistry(force: Boolean = false) {
        if (isRefreshing || nfcRepository == null) return
        val now = System.currentTimeMillis()
        
        // RELIABILITY FIX: Bypass throttle if the registry is currently empty (e.g. after logout clear)
        // OR if a force refresh is requested (e.g. on app resume)
        val shouldBypassThrottle = force || NfcProtocol.isRegistryEmpty()
        
        if (!shouldBypassThrottle && now - lastRefreshTime < REFRESH_INTERVAL_MS && lastRefreshTime != 0L) return

        viewModelScope.launch {
            isRefreshing = true
            try {
                nfcRepository.fetchProfile().onSuccess { profile ->
                    val institutionId = profile?.institutionId
                    
                    // Update local verified profile cache to allow safe offline loading on next launch
                    val currentUserId = currentUserIdProvider()
                    if (currentUserId != null) {
                        val prefs = context?.getSharedPreferences("verified_profile_$currentUserId", Context.MODE_PRIVATE)
                        if (institutionId != null) {
                            prefs?.edit()?.putString("institution_id", institutionId)?.apply()
                        } else {
                            prefs?.edit()?.remove("institution_id")?.apply()
                        }
                    }

                    if (institutionId == null) {
                        Log.d("FocusViewModel", "No institution assigned. Clearing physical registry.")
                        NfcProtocol.setRegisteredTags(emptySet())
                    } else {
                        // Check if current cache is for a different institution
                        val cache = nfcRepository.getCache()
                        if (cache != null && cache.fetchedForInstitutionId != institutionId) {
                            Log.d("FocusViewModel", "Institution changed. Discarding old cache.")
                            NfcProtocol.setRegisteredTags(emptySet())
                            // Overwrite with cleared cache to prevent resurrection
                            nfcRepository.saveCache(NfcRegistryCache(emptySet(), emptyMap(), institutionId, 0))
                        }

                        nfcRepository.fetchActiveTagsWithNames().onSuccess { tagMap ->
                            val normalizedTagMap = tagMap.mapKeys { (uid, _) -> NfcProtocol.normalize(uid) ?: uid }
                            NfcProtocol.setRegisteredTags(normalizedTagMap.keys)
                            nfcRepository.saveCache(NfcRegistryCache(normalizedTagMap.keys, normalizedTagMap, institutionId, now))
                            lastRefreshTime = now
                            Log.d("FocusViewModel", "NFC registry refreshed: ${normalizedTagMap.size} tags.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FocusViewModel", "Error refreshing NFC registry: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
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
