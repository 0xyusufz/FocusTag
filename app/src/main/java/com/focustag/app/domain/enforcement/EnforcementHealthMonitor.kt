package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementLifecycleState
import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.FocusState

interface HealthMonitor {
    val currentHealth: EnforcementHealthSnapshot

    fun start()

    fun stop()

    fun checkHealth(observation: EnforcementHealthObservation): EnforcementHealthSnapshot
}

enum class EnforcementHealthState {
    HEALTHY,
    DEGRADED,
    FAILED,
    INACTIVE,
    RECOVERING
}

enum class HealthCapabilityClass {
    CORE,
    OPTIONAL
}

data class EnforcementHealthObservation(
    val focusState: FocusState,
    val enforcementState: EnforcementLifecycleState,
    val policy: EnforcementPolicy?,
    val appBlockingStatus: EnforcementModuleStatus?,
    val appBlockingMonitoringActive: Boolean,
    val accessibilityAvailable: Boolean,
    val optionalCapabilities: Map<EnforcementCapability, EnforcementModuleStatus> = emptyMap(),
    val failureReason: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class EnforcementHealthSnapshot(
    val overallState: EnforcementHealthState = EnforcementHealthState.INACTIVE,
    val coreCapabilities: Map<EnforcementCapability, EnforcementModuleStatus> = emptyMap(),
    val optionalCapabilities: Map<EnforcementCapability, EnforcementModuleStatus> = emptyMap(),
    val focusState: FocusState = FocusState.NORMAL,
    val enforcementState: EnforcementLifecycleState = EnforcementLifecycleState.INACTIVE,
    val appBlockingState: EnforcementModuleStatus? = null,
    val accessibilityAvailable: Boolean = false,
    val policyAvailable: Boolean = false,
    val timestampMillis: Long = 0L,
    val failureReason: String? = null
)

class DefaultHealthMonitor(
    private val logger: EnforcementLogger = NoOpEnforcementLogger
) : HealthMonitor {
    override var currentHealth: EnforcementHealthSnapshot = EnforcementHealthSnapshot()
        private set

    private var monitoring = false

    override fun start() {
        monitoring = true
    }

    override fun stop() {
        monitoring = false
        currentHealth = EnforcementHealthSnapshot()
    }

    override fun checkHealth(
        observation: EnforcementHealthObservation
    ): EnforcementHealthSnapshot {
        val previousState = currentHealth.overallState
        val coreCapabilities = mapOf(
            EnforcementCapability.APP_BLOCKING to
                (observation.appBlockingStatus ?: EnforcementModuleStatus.FAILED)
        )
        val policyAvailable = observation.policy != null
        val inactive = observation.focusState != FocusState.FOCUS_ACTIVE

        val failureReason = when {
            inactive -> null
            !policyAvailable -> observation.failureReason ?: "Enforcement policy is unavailable."
            observation.enforcementState != EnforcementLifecycleState.ACTIVE ->
                observation.failureReason ?: "Enforcement coordinator is not active."
            observation.appBlockingStatus != EnforcementModuleStatus.STARTED ->
                observation.failureReason ?: "App Blocking is not started."
            !observation.appBlockingMonitoringActive ->
                observation.failureReason ?: "App Blocking monitoring is not active."
            !observation.accessibilityAvailable ->
                observation.failureReason ?: "AccessibilityService is unavailable."
            else -> null
        }

        val coreFailed = !inactive && failureReason != null
        val optionalDegraded = observation.optionalCapabilities.values.any { status ->
            status == EnforcementModuleStatus.DEGRADED ||
                status == EnforcementModuleStatus.FAILED
        }
        val baseState = when {
            inactive -> EnforcementHealthState.INACTIVE
            coreFailed -> EnforcementHealthState.FAILED
            optionalDegraded -> EnforcementHealthState.DEGRADED
            else -> EnforcementHealthState.HEALTHY
        }
        val overallState = if (
            monitoring &&
            previousState == EnforcementHealthState.FAILED &&
            baseState == EnforcementHealthState.HEALTHY
        ) {
            EnforcementHealthState.RECOVERING
        } else {
            baseState
        }

        currentHealth = EnforcementHealthSnapshot(
            overallState = overallState,
            coreCapabilities = coreCapabilities,
            optionalCapabilities = observation.optionalCapabilities,
            focusState = observation.focusState,
            enforcementState = observation.enforcementState,
            appBlockingState = observation.appBlockingStatus,
            accessibilityAvailable = observation.accessibilityAvailable,
            policyAvailable = policyAvailable,
            timestampMillis = observation.timestampMillis,
            failureReason = failureReason
        )
        if (currentHealth.overallState != previousState) {
            val eventType = when (currentHealth.overallState) {
                EnforcementHealthState.DEGRADED -> AuditEventType.HEALTH_DEGRADED
                EnforcementHealthState.FAILED -> AuditEventType.HEALTH_FAILED
                EnforcementHealthState.RECOVERING -> AuditEventType.HEALTH_RECOVERING
                EnforcementHealthState.HEALTHY ->
                    if (previousState == EnforcementHealthState.RECOVERING) {
                        AuditEventType.HEALTH_RESTORED
                    } else {
                        null
                    }
                EnforcementHealthState.INACTIVE -> null
            }
            eventType?.let {
                logger.auditEvent(
                    AuditEvent(
                        eventType = it,
                        severity = when (currentHealth.overallState) {
                            EnforcementHealthState.FAILED -> AuditSeverity.ERROR
                            EnforcementHealthState.DEGRADED -> AuditSeverity.WARNING
                            else -> AuditSeverity.INFO
                        },
                        source = "HealthMonitor",
                        healthState = currentHealth.overallState,
                        enforcementState = currentHealth.enforcementState,
                        reason = currentHealth.failureReason
                    )
                )
            }
        }
        return currentHealth
    }
}
