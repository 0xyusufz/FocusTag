package com.focustag.app.data.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.repository.EnforcementRepository
import com.focustag.app.data.repository.FocusRepository
import com.focustag.app.data.model.FocusAction
import java.util.concurrent.atomic.AtomicReference

data class AccessibilitySessionState(
    val ownerUserId: String? = null,
    val sessionId: String? = null,
    val blockedPackages: Set<String> = emptySet(),
    val isArmed: Boolean = false
)

class FocusTagAccessibilityService : AccessibilityService() {

    private companion object {
        const val TAG = "FocusTagAccessibility"
        val sessionState = AtomicReference(AccessibilitySessionState())

        fun updateSessionState(newState: AccessibilitySessionState) {
            sessionState.set(newState)
            Log.d(TAG, "Session state updated: isArmed=${newState.isArmed}, owner=${newState.ownerUserId}, blockedCount=${newState.blockedPackages.size}")
        }
    }

    // Public bridge for the strategy
    fun updateSessionStateBridge(newState: AccessibilitySessionState) = updateSessionState(newState)

    // Companion object extension to allow strategy to call it
    object StateManager {
        fun update(newState: AccessibilitySessionState) = updateSessionState(newState)
    }

    private var lastInterceptionTime = 0L
    private var lastInteractedPackage: String? = null
    private val DEBOUNCE_MS = 500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "FocusTag AccessibilityService successfully connected")
        attemptRecovery()
    }

    private fun attemptRecovery() {
        try {
            val enforcementRepo = EnforcementRepository(applicationContext, "") // Device-level access
            val activeOwnerId = enforcementRepo.getDeviceEnforcementOwnerId() ?: return
            
            // Re-init repo with correct userId for user-scoped data
            val userEnforcementRepo = EnforcementRepository(applicationContext, activeOwnerId)
            val focusRepo = FocusRepository(applicationContext, activeOwnerId)
            
            val snapshot = userEnforcementRepo.getSnapshot()
            val focusSession = focusRepo.getFocusSessionState()

            if (focusSession.focusState == FocusState.FOCUS_ACTIVE && snapshot != null && snapshot.userId == activeOwnerId) {
                val blockedPackages = snapshot.policies
                    .filter { it.action == FocusAction.BLOCK }
                    .map { it.appInfo.packageName }
                    .filter { it != packageName } // Defensive self-protection
                    .toSet()

                updateSessionState(AccessibilitySessionState(
                    ownerUserId = activeOwnerId,
                    sessionId = snapshot.sessionId,
                    blockedPackages = blockedPackages,
                    isArmed = true
                ))
                Log.d(TAG, "Successfully recovered active focus session for $activeOwnerId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recovery failed: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString() ?: return
            if (pkgName.isEmpty() || pkgName == packageName) return

            val state = sessionState.get()
            if (!state.isArmed || state.ownerUserId == null) return

            try {
                if (state.blockedPackages.contains(pkgName)) {
                    val currentTime = System.currentTimeMillis()
                    if (pkgName == lastInteractedPackage && (currentTime - lastInterceptionTime) < DEBOUNCE_MS) {
                        return
                    }
                    
                    Log.i(TAG, "INTERCEPTED: $pkgName. Redirecting to HOME.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    
                    lastInterceptionTime = currentTime
                    lastInteractedPackage = pkgName
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing event: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "FocusTag AccessibilityService was interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FocusTag AccessibilityService destroyed")
    }
}
