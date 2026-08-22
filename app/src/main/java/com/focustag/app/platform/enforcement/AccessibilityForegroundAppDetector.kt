package com.focustag.app.platform.enforcement

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.focustag.app.domain.enforcement.ForegroundAppDetector
import com.focustag.app.domain.enforcement.ForegroundAppSnapshot
import com.focustag.app.domain.enforcement.ForegroundMonitoringHandle

/**
 * Uses the user-enabled AccessibilityService because UsageStatsManager only observes foreground
 * history and cannot reliably redirect a blocked application.
 */
class AccessibilityForegroundAppDetector(
    context: Context
) : ForegroundAppDetector {

    private val appContext = context.applicationContext
    private val accessibilityManager =
        appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val serviceComponent = ComponentName(
        appContext,
        FocusTagAccessibilityService::class.java
    )

    override fun hasRequiredPermission(): Boolean {
        return accessibilityManager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { serviceInfo ->
                val service = serviceInfo.resolveInfo.serviceInfo
                service.packageName == serviceComponent.packageName &&
                    service.name == serviceComponent.className
            }
    }

    override fun currentForegroundApp(): ForegroundAppSnapshot? {
        return AccessibilityForegroundAppEventBus.lastSnapshot()
    }

    override fun startMonitoring(
        onForegroundAppChanged: (ForegroundAppSnapshot) -> Unit,
        onFailure: (Throwable) -> Unit
    ): ForegroundMonitoringHandle {
        AccessibilityForegroundAppEventBus.clearLastSnapshot()

        val listener: (ForegroundAppSnapshot) -> Unit = { snapshot ->
            try {
                onForegroundAppChanged(snapshot)
            } catch (error: Throwable) {
                onFailure(error)
            }
        }

        AccessibilityForegroundAppEventBus.addListener(listener)

        return object : ForegroundMonitoringHandle {
            override fun stop() {
                AccessibilityForegroundAppEventBus.removeListener(listener)
            }
        }
    }
}
