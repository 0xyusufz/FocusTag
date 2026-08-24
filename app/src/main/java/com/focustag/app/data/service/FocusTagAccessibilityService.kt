package com.focustag.app.data.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FocusTagAccessibilityService : AccessibilityService() {

    private companion object {
        const val TAG = "FocusTagAccessibility"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "FocusTag AccessibilityService successfully connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            Log.d(TAG, "eventType=TYPE_WINDOW_STATE_CHANGED, package=$packageName")
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
