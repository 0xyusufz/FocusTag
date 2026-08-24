package com.focustag.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.focustag.app.data.model.AccessibilityCapability
import com.focustag.app.data.service.FocusTagAccessibilityService

object AccessibilityCapabilityChecker {

    fun checkAccessibilityCapability(context: Context): AccessibilityCapability {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        val expectedComponentName = ComponentName(context, FocusTagAccessibilityService::class.java)

        val isEnabled = enabledServices.any { serviceInfo ->
            val enabledComponentName = ComponentName.unflattenFromString(serviceInfo.id)
            enabledComponentName == expectedComponentName
        }

        return if (isEnabled) {
            AccessibilityCapability.ACCESSIBILITY_READY
        } else {
            AccessibilityCapability.ACCESSIBILITY_UNAVAILABLE
        }
    }
}
