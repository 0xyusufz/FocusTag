package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementLifecycleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementAuditEventTest {

    @Test
    fun eventContainsOnlyStructuredDiagnosticFields() {
        val event = AuditEvent(
            eventType = AuditEventType.APP_BLOCKED,
            severity = AuditSeverity.INFO,
            source = "AppBlockingModule",
            enforcementState = EnforcementLifecycleState.ACTIVE,
            capability = EnforcementCapability.APP_BLOCKING
        )

        assertEquals(AuditEventType.APP_BLOCKED, event.eventType)
        assertEquals(EnforcementCapability.APP_BLOCKING, event.capability)
        assertEquals(null, event.reason)
    }

    @Test
    fun inMemoryStorePreservesEventOrder() {
        val store = InMemoryAuditEventStore()
        store.append(AuditEvent(AuditEventType.ENFORCEMENT_STARTED, source = "test"))
        store.append(AuditEvent(AuditEventType.HEALTH_FAILED, source = "test"))

        assertEquals(
            listOf(
                AuditEventType.ENFORCEMENT_STARTED,
                AuditEventType.HEALTH_FAILED
            ),
            store.events().map { it.eventType }
        )
    }

    @Test
    fun healthMonitorAuditsMeaningfulTransitionsOnly() {
        val store = InMemoryAuditEventStore()
        val logger = RecordingAuditLogger(store)
        val monitor = DefaultHealthMonitor(logger).also { it.start() }
        val failed = healthObservation(accessibilityAvailable = false)

        monitor.checkHealth(failed)
        monitor.checkHealth(failed)

        assertEquals(1, store.events().count { it.eventType == AuditEventType.HEALTH_FAILED })
    }

    @Test
    fun recoveryEventsUseTheSameAuditStore() {
        val store = InMemoryAuditEventStore()
        val logger = RecordingAuditLogger(store)

        logger.recoveryEvent(
            RecoveryEvent(
                type = RecoveryEventType.RECOVERY_ACTION,
                action = RecoveryAction.RESTART_MONITORING,
                message = "restart"
            )
        )

        assertTrue(store.events().any { it.eventType == AuditEventType.RECOVERY_ACTION })
    }

    private fun healthObservation(accessibilityAvailable: Boolean): EnforcementHealthObservation {
        return EnforcementHealthObservation(
            focusState = com.focustag.app.data.model.FocusState.FOCUS_ACTIVE,
            enforcementState = EnforcementLifecycleState.ACTIVE,
            policy = com.focustag.app.data.model.EnforcementPolicy(
                focusState = com.focustag.app.data.model.FocusState.FOCUS_ACTIVE,
                activeTagId = "tag",
                blockedPackages = emptySet(),
                protectedPackages = emptySet(),
                allowedPackages = emptySet()
            ),
            appBlockingStatus = EnforcementModuleStatus.STARTED,
            appBlockingMonitoringActive = true,
            accessibilityAvailable = accessibilityAvailable
        )
    }
}

private class RecordingAuditLogger(
    private val store: AuditEventStore
) : EnforcementLogger {
    override fun auditEvent(event: AuditEvent) {
        store.append(event)
    }

    override fun transition(
        from: EnforcementLifecycleState,
        to: EnforcementLifecycleState,
        eventName: String
    ) = Unit

    override fun invalidTransition(
        from: EnforcementLifecycleState,
        eventName: String
    ) = Unit

    override fun moduleResult(result: EnforcementModuleResult) = Unit
}