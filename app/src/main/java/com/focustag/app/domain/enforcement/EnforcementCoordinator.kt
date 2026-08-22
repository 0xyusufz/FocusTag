package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementPolicy
import com.focustag.app.data.model.EnforcementLifecycleState
import com.focustag.app.data.model.EnforcementState

class EnforcementCoordinator(
    private val appBlockingEnforcer: AppBlockingEnforcer,
    private val visibilityControlEnforcer: VisibilityControlEnforcer,
    private val networkRestrictionEnforcer: NetworkRestrictionEnforcer,
    private val escapeProtectionEnforcer: EscapeProtectionEnforcer,
    private val stateMachine: EnforcementStateMachine = EnforcementStateMachine(),
    private val logger: EnforcementLogger = NoOpEnforcementLogger
) : EnforcementAbstraction {

    private val latestModuleResults = mutableMapOf<EnforcementCapability, EnforcementModuleResult>()

    val capabilityStatuses: Map<EnforcementCapability, EnforcementModuleStatus>
        get() = latestModuleResults.mapValues { (_, result) -> result.status }

    override val currentState: EnforcementState
        get() = stateMachine.currentState

    override fun start(policy: EnforcementPolicy): EnforcementState {
        if (!policy.shouldEnforce) {
            return stateMachine.currentState
        }

        logger.auditEvent(
            AuditEvent(
                eventType = AuditEventType.ENFORCEMENT_START_REQUESTED,
                source = "EnforcementCoordinator",
                enforcementState = currentState.lifecycleState
            )
        )

        val startingState = stateMachine.transition(EnforcementEvent.StartRequested(policy))
        if (startingState.lifecycleState != EnforcementLifecycleState.STARTING) {
            return startingState
        }

        val results = listOf(
            appBlockingEnforcer.start(policy),
            visibilityControlEnforcer.start(policy),
            networkRestrictionEnforcer.start(policy),
            escapeProtectionEnforcer.start(policy)
        )

        results.forEach(logger::moduleResult)
        results.forEach { result ->
            latestModuleResults[result.capability] = result
        }
        val failures = results.filter { it.isFailure }

        return if (failures.isEmpty()) {
            stateMachine.transition(EnforcementEvent.StartSucceeded).also { state ->
                logger.auditEvent(
                    AuditEvent(
                        eventType = AuditEventType.ENFORCEMENT_STARTED,
                        source = "EnforcementCoordinator",
                        enforcementState = state.lifecycleState
                    )
                )
            }
        } else {
            stateMachine.transition(
                EnforcementEvent.StartFailed(failures.joinToString("; ") { failure ->
                    "${failure.capability}: ${failure.message ?: failure.status.name}"
                })
            ).also { state ->
                logger.auditEvent(
                    AuditEvent(
                        eventType = AuditEventType.ENFORCEMENT_FAILED,
                        severity = AuditSeverity.ERROR,
                        source = "EnforcementCoordinator",
                        enforcementState = state.lifecycleState,
                        reason = failures.joinToString("; ") { it.message ?: it.status.name }
                    )
                )
            }
        }
    }

    override fun stop(): EnforcementState {
        logger.auditEvent(
            AuditEvent(
                eventType = AuditEventType.ENFORCEMENT_STOP_REQUESTED,
                source = "EnforcementCoordinator",
                enforcementState = currentState.lifecycleState
            )
        )
        val stoppingState = stateMachine.transition(EnforcementEvent.StopRequested)
        if (stoppingState.lifecycleState != EnforcementLifecycleState.STOPPING) {
            return stoppingState
        }

        val results = listOf(
            escapeProtectionEnforcer.stop(),
            networkRestrictionEnforcer.stop(),
            visibilityControlEnforcer.stop(),
            appBlockingEnforcer.stop()
        )

        results.forEach(logger::moduleResult)
        results.forEach { result ->
            latestModuleResults[result.capability] = result
        }
        val failures = results.filter { it.status == EnforcementModuleStatus.FAILED }

        return if (failures.isEmpty()) {
            stateMachine.transition(EnforcementEvent.StopSucceeded).also { state ->
                logger.auditEvent(
                    AuditEvent(
                        eventType = AuditEventType.ENFORCEMENT_STOPPED,
                        source = "EnforcementCoordinator",
                        enforcementState = state.lifecycleState
                    )
                )
            }
        } else {
            stateMachine.transition(
                EnforcementEvent.StopFailed(failures.joinToString("; ") { failure ->
                    "${failure.capability}: ${failure.message ?: failure.status.name}"
                })
            ).also { state ->
                logger.auditEvent(
                    AuditEvent(
                        eventType = AuditEventType.ENFORCEMENT_FAILED,
                        severity = AuditSeverity.ERROR,
                        source = "EnforcementCoordinator",
                        enforcementState = state.lifecycleState,
                        reason = failures.joinToString("; ") { it.message ?: it.status.name }
                    )
                )
            }
        }
    }
}
