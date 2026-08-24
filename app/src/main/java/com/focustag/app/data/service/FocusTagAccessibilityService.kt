package com.focustag.app.data.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focustag.app.data.model.FocusAction
import java.util.concurrent.atomic.AtomicReference

data class AccessibilitySessionState(
    val ownerUserId: String? = null,
    val sessionId: String? = null,
    val blockedPackages: Set<String> = emptySet(),
    val isArmed: Boolean = false
)

class FocusTagAccessibilityService : AccessibilityService() {

    companion object {
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
