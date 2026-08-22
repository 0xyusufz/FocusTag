package com.focustag.app.platform.enforcement

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.domain.enforcement.EnforcementCapability
import com.focustag.app.domain.enforcement.EnforcementLogger
import com.focustag.app.domain.enforcement.EnforcementModuleResult
import com.focustag.app.domain.enforcement.EnforcementModuleStatus
import com.focustag.app.domain.enforcement.EscapeProtectionEnforcer
import com.focustag.app.domain.enforcement.EscapeProtectionCapabilityAssessment
import com.focustag.app.domain.enforcement.EscapeProtectionEvent
import com.focustag.app.domain.enforcement.EscapeProtectionResult
import com.focustag.app.domain.enforcement.EscapeProtectionStatus
import com.focustag.app.domain.enforcement.EscapeRoute
import com.focustag.app.domain.enforcement.ForegroundAppSnapshot

/**
 * Reports detectable escape conditions without attempting privileged Android changes.
 *
 * AccessibilityService lifecycle and Settings foreground events are observable. Re-enabling the
 * service, preventing Settings access, and protecting the process from force-stop or uninstall are
 * not available to a normal application.
 */
class AndroidEscapeProtectionController(
    context: Context,
    private val logger: EnforcementLogger = AndroidEnforcementLogger()
) : EscapeProtectionEnforcer {

    private val appContext = context.applicationContext
    private val accessibilityDetector = AccessibilityForegroundAppDetector(appContext)
    private val usageStatsDetector = UsageStatsForegroundAppDetector(appContext)
    private val handler = Handler(Looper.getMainLooper())

    private var foregroundListener: ((ForegroundAppSnapshot) -> Unit)? = null
    private var serviceStateListener: ((AccessibilityServiceState) -> Unit)? = null
    private var lastAccessibilityAvailable: Boolean? = null
    private var lastUsageAccessAvailable: Boolean? = null
    private var monitoring = false

    private val capabilityPoller = object : Runnable {
        override fun run() {
            if (!monitoring) {
                return
            }

            checkCapabilities(reportUnchanged = false)
            handler.postDelayed(this, CAPABILITY_POLL_INTERVAL_MILLIS)
        }
    }

    @Synchronized
    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        stopMonitoring()

        val initialResult = checkCapabilities(reportUnchanged = true)

        foregroundListener = { snapshot ->
            if (snapshot.packageName == SETTINGS_PACKAGE) {
                report(
                    EscapeProtectionResult(
                        status = EscapeProtectionStatus.DETECTED,
                        route = EscapeRoute.SETTINGS_OPENED,
                        message = "Android Settings became foreground; FocusTag cannot prevent Settings access."
                    )
                )
            }
        }
        serviceStateListener = { state ->
            when (state) {
                AccessibilityServiceState.CONNECTED -> report(
                    EscapeProtectionResult(
                        status = EscapeProtectionStatus.SAFE,
                        message = "AccessibilityService connected."
                    )
                )
                AccessibilityServiceState.INTERRUPTED,
                AccessibilityServiceState.DESTROYED -> report(
                    EscapeProtectionResult(
                        status = EscapeProtectionStatus.DETECTED,
                        route = EscapeRoute.ENFORCEMENT_SERVICE_UNAVAILABLE,
                        message = "AccessibilityService is interrupted or unavailable; foreground enforcement is degraded."
                    )
                )
            }
        }

        AccessibilityForegroundAppEventBus.addListener(requireNotNull(foregroundListener))
        AccessibilityForegroundAppEventBus.addServiceStateListener(requireNotNull(serviceStateListener))
        monitoring = true
        handler.post(capabilityPoller)

        return if (initialResult.status == EscapeProtectionStatus.SAFE) {
            moduleResult(
                status = EnforcementModuleStatus.STARTED,
                message = "Escape protection monitoring started."
            )
        } else {
            moduleResult(
                status = EnforcementModuleStatus.DEGRADED,
                message = initialResult.message ?: "Escape protection monitoring started with degraded capability."
            )
        }
    }

    @Synchronized
    override fun stop(): EnforcementModuleResult {
        stopMonitoring()
        return moduleResult(
            status = EnforcementModuleStatus.STOPPED,
            message = "Escape protection monitoring stopped."
        )
    }

    private fun checkCapabilities(reportUnchanged: Boolean): EscapeProtectionResult {
        val accessibilityAvailable = accessibilityDetector.hasRequiredPermission()
        val usageAccessAvailable = usageStatsDetector.hasRequiredPermission()
        val result = EscapeProtectionCapabilityAssessment.assess(
            accessibilityAvailable = accessibilityAvailable,
            usageAccessAvailable = usageAccessAvailable
        )

        if (reportUnchanged || accessibilityAvailable != lastAccessibilityAvailable) {
            if (!accessibilityAvailable) {
                report(
                    EscapeProtectionResult(
                        status = EscapeProtectionStatus.DETECTED,
                        route = EscapeRoute.ACCESSIBILITY_DISABLED,
                        message = "AccessibilityService is unavailable; foreground escape detection is degraded."
                    )
                )
            }
            lastAccessibilityAvailable = accessibilityAvailable
        }

        if (reportUnchanged || usageAccessAvailable != lastUsageAccessAvailable) {
            if (!usageAccessAvailable) {
                report(
                    EscapeProtectionResult(
                        status = EscapeProtectionStatus.DETECTED,
                        route = EscapeRoute.USAGE_ACCESS_DISABLED,
                        message = "Usage Access is unavailable; supporting usage observation is degraded."
                    )
                )
            }
            lastUsageAccessAvailable = usageAccessAvailable
        }

        return result
    }

    private fun report(result: EscapeProtectionResult) {
        logger.escapeProtectionEvent(EscapeProtectionEvent(result))
    }

    private fun moduleResult(
        status: EnforcementModuleStatus,
        message: String
    ): EnforcementModuleResult {
        return EnforcementModuleResult(
            capability = EnforcementCapability.ESCAPE_PROTECTION,
            status = status,
            message = message
        )
    }

    private fun stopMonitoring() {
        monitoring = false
        handler.removeCallbacks(capabilityPoller)
        foregroundListener?.let(AccessibilityForegroundAppEventBus::removeListener)
        serviceStateListener?.let(AccessibilityForegroundAppEventBus::removeServiceStateListener)
        foregroundListener = null
        serviceStateListener = null
        lastAccessibilityAvailable = null
        lastUsageAccessAvailable = null
    }

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val CAPABILITY_POLL_INTERVAL_MILLIS = 2_000L
    }
}
