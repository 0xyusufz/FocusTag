package com.focustag.app.platform.enforcement

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focustag.app.domain.enforcement.ForegroundAppSnapshot

class FocusTagAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString()
        if (packageName.isNullOrBlank()) {
            Log.w(TAG, "Window state event missing package name")
            return
        }

        if (packageName == packageNameForFocusTag() && BlockingActivity.isVisible) {
            return
        }

        AccessibilityForegroundAppEventBus.emit(
            ForegroundAppSnapshot(
                packageName = packageName,
                detectedAtMillis = System.currentTimeMillis()
            )
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityForegroundAppEventBus.emitServiceState(AccessibilityServiceState.CONNECTED)
        Log.d(TAG, "FocusTag accessibility service connected")
    }

    override fun onInterrupt() {
        AccessibilityForegroundAppEventBus.emitServiceState(AccessibilityServiceState.INTERRUPTED)
        Log.w(TAG, "FocusTag accessibility service interrupted")
    }

    override fun onDestroy() {
        AccessibilityForegroundAppEventBus.emitServiceState(AccessibilityServiceState.DESTROYED)
        super.onDestroy()
        Log.d(TAG, "FocusTag accessibility service destroyed")
    }

    private fun packageNameForFocusTag(): String {
        return applicationContext.packageName
    }

    private companion object {
        const val TAG = "FocusTagAccessibility"
    }
}
