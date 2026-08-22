package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementPolicy

class AppBlockingModule(
    private val foregroundAppDetector: ForegroundAppDetector,
    private val blockingExperienceController: BlockingExperienceController = NoOpBlockingExperienceController,
    private val logger: EnforcementLogger = NoOpEnforcementLogger
) : AppBlockingEnforcer {

    private var activePolicy: EnforcementPolicy? = null
    private var monitoringHandle: ForegroundMonitoringHandle? = null
    private var lastDecision: AppBlockingDecision? = null
    private var lastAuditedPackage: String? = null
    private var lastAuditedDecision: AppBlockingDecision? = null

    override fun start(policy: EnforcementPolicy): EnforcementModuleResult {
        monitoringHandle?.stop()
        monitoringHandle = null
        activePolicy = policy
        logger.policyLoaded(policy)

        if (!policy.shouldEnforce) {
            activePolicy = null
            lastDecision = null
            lastAuditedPackage = null
            lastAuditedDecision = null
            logger.blockingExperienceResult(blockingExperienceController.clear())
            return EnforcementModuleResult(
                capability = EnforcementCapability.APP_BLOCKING,
                status = EnforcementModuleStatus.STOPPED,
                message = "Focus mode is inactive."
            )
        }

        if (!foregroundAppDetector.hasRequiredPermission()) {
            activePolicy = null
            lastDecision = null
            logger.blockingExperienceResult(blockingExperienceController.clear())
            logger.permissionUnavailable(EnforcementCapability.APP_BLOCKING)
            return EnforcementModuleResult(
                capability = EnforcementCapability.APP_BLOCKING,
                status = EnforcementModuleStatus.FAILED,
                message = "Foreground app detection capability is not available."
            )
        }

        monitoringHandle = foregroundAppDetector.startMonitoring(
            onForegroundAppChanged = ::evaluateForegroundApp,
            onFailure = { error ->
                logger.detectorFailure(error)
            }
        )

        foregroundAppDetector.currentForegroundApp()?.let(::evaluateForegroundApp)
        logger.monitoringStarted(EnforcementCapability.APP_BLOCKING)

        return EnforcementModuleResult(
            capability = EnforcementCapability.APP_BLOCKING,
            status = EnforcementModuleStatus.STARTED,
            message = "Foreground app monitoring started."
        )
    }

    override fun stop(): EnforcementModuleResult {
        monitoringHandle?.stop()
        monitoringHandle = null
        activePolicy = null
        lastDecision = null
        lastAuditedPackage = null
        lastAuditedDecision = null
        logger.blockingExperienceResult(blockingExperienceController.clear())
        logger.monitoringStopped(EnforcementCapability.APP_BLOCKING)

        return EnforcementModuleResult(
            capability = EnforcementCapability.APP_BLOCKING,
            status = EnforcementModuleStatus.STOPPED,
            message = "Foreground app monitoring stopped."
        )
    }

    override fun recoverMonitoring(): EnforcementModuleResult {
        val policy = activePolicy
        if (policy == null || !policy.shouldEnforce) {
            return EnforcementModuleResult(
                capability = EnforcementCapability.APP_BLOCKING,
                status = EnforcementModuleStatus.FAILED,
                message = "Cannot recover App Blocking without an active enforcement policy."
            )
        }

        if (!foregroundAppDetector.hasRequiredPermission()) {
            logger.permissionUnavailable(EnforcementCapability.APP_BLOCKING)
            return EnforcementModuleResult(
                capability = EnforcementCapability.APP_BLOCKING,
                status = EnforcementModuleStatus.FAILED,
                message = "Enable FocusTag AccessibilityService in Android Settings."
            )
        }

        monitoringHandle?.stop()
        monitoringHandle = foregroundAppDetector.startMonitoring(
            onForegroundAppChanged = ::evaluateForegroundApp,
            onFailure = { error -> logger.detectorFailure(error) }
        )

        val recovered = monitoringHandle != null
        if (recovered) {
            foregroundAppDetector.currentForegroundApp()?.let(::evaluateForegroundApp)
        }

        val result = EnforcementModuleResult(
            capability = EnforcementCapability.APP_BLOCKING,
            status = if (recovered) {
                EnforcementModuleStatus.STARTED
            } else {
                EnforcementModuleStatus.FAILED
            },
            message = if (recovered) {
                "App Blocking monitoring restarted and verified."
            } else {
                "App Blocking monitoring could not be restarted."
            }
        )
        logger.moduleResult(result)
        return result
    }

    fun isMonitoringActive(): Boolean {
        return monitoringHandle != null
    }

    fun currentDecision(): AppBlockingDecision? {
        return lastDecision
    }

    private fun evaluateForegroundApp(snapshot: ForegroundAppSnapshot) {
        logger.foregroundPackageDetected(snapshot.packageName)

        val policy = activePolicy
        if (policy == null) {
            lastDecision = AppBlockingDecision.NOT_ENFORCED
            logger.appBlockingDecision(snapshot.packageName, AppBlockingDecision.NOT_ENFORCED)
            return
        }

        val decision = AppBlockingDecisionEvaluator.evaluate(policy, snapshot.packageName)
        lastDecision = decision
        logger.appBlockingDecision(snapshot.packageName, decision)
        if (snapshot.packageName != lastAuditedPackage || decision != lastAuditedDecision) {
            val eventType = when (decision) {
                AppBlockingDecision.BLOCKED -> AuditEventType.APP_BLOCKED
                AppBlockingDecision.PROTECTED -> AuditEventType.APP_PROTECTED
                AppBlockingDecision.ALLOWED -> AuditEventType.APP_ALLOWED
                AppBlockingDecision.UNKNOWN -> AuditEventType.APP_UNKNOWN
                AppBlockingDecision.NOT_ENFORCED -> null
            }
            eventType?.let {
                logger.auditEvent(
                    AuditEvent(
                        eventType = it,
                        source = "AppBlockingModule",
                        capability = EnforcementCapability.APP_BLOCKING
                    )
                )
            }
            lastAuditedPackage = snapshot.packageName
            lastAuditedDecision = decision
        }

        if (BlockingExperienceDecisionMapper.requiresBlockingExperience(decision)) {
            val packageName = snapshot.packageName
            if (packageName.isNullOrBlank()) {
                logger.blockingExperienceResult(
                    BlockingExperienceResult(
                        succeeded = false,
                        message = "Cannot block an unknown foreground package."
                    )
                )
                return
            }

            val result = blockingExperienceController.showBlockedApp(packageName)
            logger.blockingExperienceResult(result)
        } else {
            logger.blockingExperienceResult(
                blockingExperienceController.clear()
            )
        }
    }
}
