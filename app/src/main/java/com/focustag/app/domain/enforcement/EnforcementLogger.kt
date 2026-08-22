package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.EnforcementLifecycleState

interface EnforcementLogger {
    fun auditEvent(event: AuditEvent) = Unit

    fun transition(
        from: EnforcementLifecycleState,
        to: EnforcementLifecycleState,
        eventName: String
    )

    fun invalidTransition(
        from: EnforcementLifecycleState,
        eventName: String
    )

    fun moduleResult(result: EnforcementModuleResult)

    fun policyLoaded(policy: EnforcementPolicy) = Unit

    fun foregroundPackageDetected(packageName: String?) = Unit

    fun appBlockingDecision(
        packageName: String?,
        decision: AppBlockingDecision
    ) = Unit

    fun permissionUnavailable(capability: EnforcementCapability) = Unit

    fun monitoringStarted(capability: EnforcementCapability) = Unit

    fun monitoringStopped(capability: EnforcementCapability) = Unit

    fun detectorFailure(error: Throwable) = Unit

    fun blockingExperienceResult(result: BlockingExperienceResult) = Unit

    fun escapeProtectionEvent(event: EscapeProtectionEvent) = Unit

    fun recoveryEvent(event: RecoveryEvent) = Unit
}

object NoOpEnforcementLogger : EnforcementLogger
