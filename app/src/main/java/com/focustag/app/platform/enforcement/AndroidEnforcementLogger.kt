package com.focustag.app.platform.enforcement

import android.util.Log
import com.focustag.app.domain.enforcement.AppBlockingDecision
import com.focustag.app.domain.enforcement.AuditEvent
import com.focustag.app.domain.enforcement.AuditEventStore
import com.focustag.app.domain.enforcement.AuditEventType
import com.focustag.app.domain.enforcement.AuditSeverity
import com.focustag.app.domain.enforcement.BlockingExperienceResult
import com.focustag.app.domain.enforcement.EnforcementCapability
import com.focustag.app.domain.enforcement.EnforcementLogger
import com.focustag.app.domain.enforcement.EnforcementModuleResult
import com.focustag.app.domain.enforcement.EnforcementModuleStatus
import com.focustag.app.domain.enforcement.EscapeProtectionEvent
import com.focustag.app.domain.enforcement.EscapeProtectionStatus
import com.focustag.app.domain.enforcement.RecoveryEvent
import com.focustag.app.domain.enforcement.RecoveryEventType
import com.focustag.app.data.model.EnforcementLifecycleState
import com.focustag.app.data.model.EnforcementPolicy

class AndroidEnforcementLogger(
    private val tag: String = "Enforcement",
    private val auditStore: AuditEventStore = com.focustag.app.domain.enforcement.InMemoryAuditEventStore()
) : EnforcementLogger {
    override fun auditEvent(event: AuditEvent) {
        runCatching { auditStore.append(event) }
        val capability = event.capability?.let { ", capability=$it" }.orEmpty()
        val reason = event.reason?.let { ": $it" }.orEmpty()
        val level = when (event.severity) {
            AuditSeverity.INFO -> Log.INFO
            AuditSeverity.WARNING -> Log.WARN
            AuditSeverity.ERROR -> Log.ERROR
        }
        Log.println(level, tag, "Audit ${event.eventType}$capability$reason")
    }

    override fun transition(from: EnforcementLifecycleState, to: EnforcementLifecycleState, eventName: String) {
        Log.d(tag, "State transition: $from -> $to on $eventName")
    }

    override fun invalidTransition(from: EnforcementLifecycleState, eventName: String) {
        Log.w(tag, "Invalid state transition ignored: $from on $eventName")
    }

    override fun moduleResult(result: EnforcementModuleResult) {
        Log.d(tag, "Module ${result.capability} returned ${result.status}: ${result.message.orEmpty()}")
        val eventType = when (result.status) {
            EnforcementModuleStatus.UNSUPPORTED -> AuditEventType.CAPABILITY_UNSUPPORTED
            EnforcementModuleStatus.DEGRADED -> AuditEventType.CAPABILITY_DEGRADED
            EnforcementModuleStatus.FAILED -> AuditEventType.CAPABILITY_FAILED
            else -> null
        }
        eventType?.let {
            auditEvent(
                AuditEvent(
                    eventType = it,
                    severity = if (result.status == EnforcementModuleStatus.FAILED) AuditSeverity.ERROR else AuditSeverity.WARNING,
                    source = "EnforcementCoordinator",
                    capability = result.capability,
                    reason = result.message
                )
            )
        }
    }

    override fun policyLoaded(policy: EnforcementPolicy) {
        Log.d(tag, "Policy loaded: blocked=${policy.blockedPackages.size}, protected=${policy.protectedPackages.size}, allowed=${policy.allowedPackages.size}")
    }

    override fun foregroundPackageDetected(packageName: String?) {
        Log.d(tag, "Foreground package detected: ${packageName ?: "unknown"}")
    }

    override fun appBlockingDecision(packageName: String?, decision: AppBlockingDecision) {
        Log.d(tag, "App blocking decision: package=${packageName ?: "unknown"}, decision=$decision")
    }

    override fun permissionUnavailable(capability: EnforcementCapability) {
        Log.w(tag, "Permission unavailable for $capability")
    }

    override fun monitoringStarted(capability: EnforcementCapability) {
        Log.d(tag, "Monitoring started for $capability")
    }

    override fun monitoringStopped(capability: EnforcementCapability) {
        Log.d(tag, "Monitoring stopped for $capability")
    }

    override fun detectorFailure(error: Throwable) {
        Log.e(tag, "Foreground app detector failure", error)
    }

    override fun blockingExperienceResult(result: BlockingExperienceResult) {
        val message = result.message?.let { ": $it" }.orEmpty()
        Log.println(if (result.succeeded) Log.INFO else Log.ERROR, tag, "Blocking experience ${if (result.succeeded) "succeeded" else "failed"}$message")
    }

    override fun escapeProtectionEvent(event: EscapeProtectionEvent) {
        val eventType = when (event.result.status) {
            EscapeProtectionStatus.DETECTED -> AuditEventType.ESCAPE_DETECTED
            EscapeProtectionStatus.UNSUPPORTED -> AuditEventType.ESCAPE_PROTECTION_UNSUPPORTED
            EscapeProtectionStatus.FAILED -> AuditEventType.ESCAPE_PROTECTION_DEGRADED
            EscapeProtectionStatus.SAFE, EscapeProtectionStatus.MITIGATED -> null
        }
        eventType?.let {
            auditEvent(AuditEvent(it, severity = AuditSeverity.WARNING, source = "EscapeProtection", capability = EnforcementCapability.ESCAPE_PROTECTION, reason = event.result.message))
        }
    }

    override fun recoveryEvent(event: RecoveryEvent) {
        val type = when (event.type) {
            RecoveryEventType.RECOVERY_REQUESTED -> AuditEventType.RECOVERY_REQUESTED
            RecoveryEventType.RECOVERY_STARTED -> AuditEventType.RECOVERY_STARTED
            RecoveryEventType.RECOVERY_ACTION -> AuditEventType.RECOVERY_ACTION
            RecoveryEventType.RECOVERY_VERIFICATION -> AuditEventType.RECOVERY_VERIFICATION
            RecoveryEventType.RECOVERY_SUCCEEDED -> AuditEventType.RECOVERY_SUCCEEDED
            RecoveryEventType.RECOVERY_FAILED -> AuditEventType.RECOVERY_FAILED
            RecoveryEventType.RECOVERY_EXHAUSTED -> AuditEventType.RECOVERY_EXHAUSTED
            RecoveryEventType.USER_ACTION_REQUIRED -> AuditEventType.USER_ACTION_REQUIRED
        }
        auditEvent(AuditEvent(type, severity = if (event.type == RecoveryEventType.RECOVERY_FAILED) AuditSeverity.WARNING else AuditSeverity.INFO, source = "RecoveryManager", reason = event.message))
    }
}
