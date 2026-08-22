package com.focustag.app.domain.enforcement

import com.focustag.app.data.model.EnforcementLifecycleState

enum class AuditEventType {
    ENFORCEMENT_START_REQUESTED,
    ENFORCEMENT_STARTED,
    ENFORCEMENT_STOP_REQUESTED,
    ENFORCEMENT_STOPPED,
    ENFORCEMENT_FAILED,
    APP_BLOCKED,
    APP_PROTECTED,
    APP_ALLOWED,
    APP_UNKNOWN,
    ESCAPE_DETECTED,
    ESCAPE_PROTECTION_DEGRADED,
    ESCAPE_PROTECTION_UNSUPPORTED,
    HEALTH_DEGRADED,
    HEALTH_FAILED,
    HEALTH_RECOVERING,
    HEALTH_RESTORED,
    RECOVERY_REQUESTED,
    RECOVERY_STARTED,
    RECOVERY_ACTION,
    RECOVERY_VERIFICATION,
    RECOVERY_SUCCEEDED,
    RECOVERY_FAILED,
    RECOVERY_EXHAUSTED,
    USER_ACTION_REQUIRED,
    CAPABILITY_UNSUPPORTED,
    CAPABILITY_DEGRADED,
    CAPABILITY_FAILED
}

enum class AuditSeverity {
    INFO,
    WARNING,
    ERROR
}

data class AuditEvent(
    val eventType: AuditEventType,
    val timestampMillis: Long = System.currentTimeMillis(),
    val severity: AuditSeverity = AuditSeverity.INFO,
    val source: String,
    val enforcementState: EnforcementLifecycleState? = null,
    val healthState: EnforcementHealthState? = null,
    val capability: EnforcementCapability? = null,
    val reason: String? = null
)

interface AuditEventStore {
    fun append(event: AuditEvent)

    fun events(): List<AuditEvent>
}

class InMemoryAuditEventStore : AuditEventStore {
    private val storedEvents = mutableListOf<AuditEvent>()

    override fun append(event: AuditEvent) {
        synchronized(storedEvents) {
            storedEvents += event
        }
    }

    override fun events(): List<AuditEvent> {
        return synchronized(storedEvents) { storedEvents.toList() }
    }
}
