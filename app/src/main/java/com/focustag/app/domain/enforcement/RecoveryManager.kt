package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementPolicy

enum class RecoveryAction {
    RESTART_MONITORING,
    RECHECK_ACCESSIBILITY,
    REFRESH_ENFORCEMENT_STATE,
    REBUILD_POLICY,
    NO_ACTION,
    USER_ACTION_REQUIRED
}

enum class RecoveryEventType {
    RECOVERY_REQUESTED,
    RECOVERY_STARTED,
    RECOVERY_ACTION,
    RECOVERY_VERIFICATION,
    RECOVERY_SUCCEEDED,
    RECOVERY_FAILED,
    RECOVERY_EXHAUSTED,
    USER_ACTION_REQUIRED
}

data class RecoveryEvent(
    val type: RecoveryEventType,
    val action: RecoveryAction? = null,
    val message: String? = null,
    val attempt: Int = 0,
    val occurredAtMillis: Long = System.currentTimeMillis()
)

enum class RecoveryOutcome {
    NO_ACTION,
    RECOVERED,
    FAILED,
    EXHAUSTED,
    USER_ACTION_REQUIRED,
    COOLDOWN
}

data class RecoveryResult(
    val outcome: RecoveryOutcome,
    val action: RecoveryAction,
    val attempt: Int,
    val message: String
)

interface RecoveryManager {
    fun attempt(policy: EnforcementPolicy? = null): RecoveryResult

    fun reset()
}

class DefaultRecoveryManager(
    private val healthMonitor: HealthMonitor,
    private val appBlockingEnforcer: AppBlockingEnforcer,
    private val logger: EnforcementLogger = NoOpEnforcementLogger,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS
) : RecoveryManager {

    private var attempts = 0
    private var nextAllowedAtMillis = 0L

    override fun attempt(policy: EnforcementPolicy?): RecoveryResult {
        val snapshot = healthMonitor.currentHealth
        logger.recoveryEvent(
            RecoveryEvent(
                type = RecoveryEventType.RECOVERY_REQUESTED,
                message = "Recovery evaluated for ${snapshot.overallState}."
            )
        )

        when (snapshot.overallState) {
            EnforcementHealthState.INACTIVE,
            EnforcementHealthState.HEALTHY,
            EnforcementHealthState.DEGRADED,
            EnforcementHealthState.RECOVERING -> {
                return result(
                    RecoveryOutcome.NO_ACTION,
                    RecoveryAction.NO_ACTION,
                    "No core recovery is required."
                )
            }
            EnforcementHealthState.FAILED -> Unit
        }

        if (!snapshot.accessibilityAvailable) {
            return userAction("Enable FocusTag AccessibilityService in Android Settings.")
        }

        if (!snapshot.policyAvailable || policy == null) {
            return userAction("A valid enforcement policy is required before recovery.")
        }

        if (attempts >= maxAttempts) {
            logger.recoveryEvent(
                RecoveryEvent(
                    type = RecoveryEventType.RECOVERY_EXHAUSTED,
                    action = RecoveryAction.RESTART_MONITORING,
                    attempt = attempts,
                    message = "Recovery retry limit reached."
                )
            )
            return result(
                RecoveryOutcome.EXHAUSTED,
                RecoveryAction.USER_ACTION_REQUIRED,
                "Recovery retry limit reached; user action is required."
            )
        }

        if (nowMillis() < nextAllowedAtMillis) {
            return result(
                RecoveryOutcome.COOLDOWN,
                RecoveryAction.NO_ACTION,
                "Recovery cooldown is active."
            )
        }

        attempts += 1
        nextAllowedAtMillis = nowMillis() + cooldownMillis
        logger.recoveryEvent(
            RecoveryEvent(
                type = RecoveryEventType.RECOVERY_STARTED,
                action = RecoveryAction.RESTART_MONITORING,
                attempt = attempts
            )
        )
        logger.recoveryEvent(
            RecoveryEvent(
                type = RecoveryEventType.RECOVERY_ACTION,
                action = RecoveryAction.RESTART_MONITORING,
                attempt = attempts,
                message = "Restarting existing App Blocking monitoring."
            )
        )

        val restart = appBlockingEnforcer.recoverMonitoring()
        val verified = restart.status == EnforcementModuleStatus.STARTED
        logger.recoveryEvent(
            RecoveryEvent(
                type = RecoveryEventType.RECOVERY_VERIFICATION,
                action = RecoveryAction.RESTART_MONITORING,
                attempt = attempts,
                message = restart.message
            )
        )

        if (!verified) {
            logger.recoveryEvent(
                RecoveryEvent(
                    type = RecoveryEventType.RECOVERY_FAILED,
                    action = RecoveryAction.RESTART_MONITORING,
                    attempt = attempts,
                    message = restart.message
                )
            )
            return result(
                if (attempts >= maxAttempts) RecoveryOutcome.EXHAUSTED else RecoveryOutcome.FAILED,
                if (attempts >= maxAttempts) RecoveryAction.USER_ACTION_REQUIRED else RecoveryAction.RESTART_MONITORING,
                restart.message ?: "App Blocking monitoring recovery failed."
            )
        }

        logger.recoveryEvent(
            RecoveryEvent(
                type = RecoveryEventType.RECOVERY_SUCCEEDED,
                action = RecoveryAction.RESTART_MONITORING,
                attempt = attempts,
                message = "App Blocking monitoring restarted and verified."
            )
        )
        reset()
        return result(
            RecoveryOutcome.RECOVERED,
            RecoveryAction.RESTART_MONITORING,
            "App Blocking monitoring restarted and verified."
        )
    }

    override fun reset() {
        attempts = 0
        nextAllowedAtMillis = 0L
    }

    private fun userAction(message: String): RecoveryResult {
        logger.recoveryEvent(
            RecoveryEvent(
                type = RecoveryEventType.USER_ACTION_REQUIRED,
                action = RecoveryAction.USER_ACTION_REQUIRED,
                message = message
            )
        )
        return result(RecoveryOutcome.USER_ACTION_REQUIRED, RecoveryAction.USER_ACTION_REQUIRED, message)
    }

    private fun result(
        outcome: RecoveryOutcome,
        action: RecoveryAction,
        message: String
    ): RecoveryResult {
        return RecoveryResult(outcome, action, attempts, message)
    }

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_COOLDOWN_MILLIS = 30_000L
    }
}