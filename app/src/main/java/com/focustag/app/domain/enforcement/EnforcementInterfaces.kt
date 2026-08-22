package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.EnforcementState

interface EnforcementAbstraction {
    val currentState: EnforcementState

    fun start(policy: EnforcementPolicy): EnforcementState

    fun stop(): EnforcementState
}

enum class EnforcementCapability {
    APP_BLOCKING,
    VISIBILITY_CONTROL,
    NETWORK_RESTRICTION,
    ESCAPE_PROTECTION
}

enum class EnforcementModuleStatus {
    STARTED,
    STOPPED,
    DEGRADED,
    UNSUPPORTED,
    FAILED
}

enum class EscapeProtectionStatus {
    SAFE,
    DETECTED,
    MITIGATED,
    UNSUPPORTED,
    FAILED
}

enum class EscapeRoute {
    SETTINGS_OPENED,
    ACCESSIBILITY_DISABLED,
    USAGE_ACCESS_DISABLED,
    ENFORCEMENT_SERVICE_UNAVAILABLE,
    APPLICATION_DISABLED,
    PROCESS_INTERRUPTED,
    UNINSTALL_ATTEMPT,
    FORCE_STOP_ATTEMPT,
    UNSUPPORTED_ESCAPE_ROUTE
}

data class EscapeProtectionResult(
    val status: EscapeProtectionStatus,
    val route: EscapeRoute? = null,
    val message: String? = null
)

data class EscapeProtectionEvent(
    val result: EscapeProtectionResult,
    val occurredAtMillis: Long = System.currentTimeMillis()
)

object EscapeProtectionCapabilityAssessment {
    fun assess(
        accessibilityAvailable: Boolean,
        usageAccessAvailable: Boolean
    ): EscapeProtectionResult {
        if (!accessibilityAvailable) {
            return EscapeProtectionResult(
                status = EscapeProtectionStatus.DETECTED,
                route = EscapeRoute.ACCESSIBILITY_DISABLED,
                message = "AccessibilityService is unavailable; foreground escape detection is degraded."
            )
        }

        if (!usageAccessAvailable) {
            return EscapeProtectionResult(
                status = EscapeProtectionStatus.DETECTED,
                route = EscapeRoute.USAGE_ACCESS_DISABLED,
                message = "Usage Access is unavailable; supporting usage observation is degraded."
            )
        }

        return EscapeProtectionResult(
            status = EscapeProtectionStatus.SAFE,
            message = "AccessibilityService and Usage Access are available."
        )
    }

    fun unsupported(route: EscapeRoute, message: String): EscapeProtectionResult {
        return EscapeProtectionResult(
            status = EscapeProtectionStatus.UNSUPPORTED,
            route = route,
            message = message
        )
    }
}

data class EnforcementModuleResult(
    val capability: EnforcementCapability,
    val status: EnforcementModuleStatus,
    val message: String? = null
) {
    val isFailure: Boolean
        get() = status == EnforcementModuleStatus.FAILED
}

interface AppBlockingEnforcer {
    fun start(policy: EnforcementPolicy): EnforcementModuleResult

    fun stop(): EnforcementModuleResult

    fun recoverMonitoring(): EnforcementModuleResult {
        return EnforcementModuleResult(
            capability = EnforcementCapability.APP_BLOCKING,
            status = EnforcementModuleStatus.FAILED,
            message = "App Blocking monitoring recovery is unavailable."
        )
    }
}

interface VisibilityControlEnforcer {
    fun start(policy: EnforcementPolicy): EnforcementModuleResult

    fun stop(): EnforcementModuleResult
}

interface NetworkRestrictionEnforcer {
    fun start(policy: EnforcementPolicy): EnforcementModuleResult

    fun stop(): EnforcementModuleResult
}

interface EscapeProtectionEnforcer {
    fun start(policy: EnforcementPolicy): EnforcementModuleResult

    fun stop(): EnforcementModuleResult
}
