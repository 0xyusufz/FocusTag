package com.focustag.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class EnforcementStatus {
    IDLE,
    ENFORCEMENT_SIMULATED,
    ENFORCEMENT_ACTIVE,
    ENFORCEMENT_DEGRADED,
    ENFORCEMENT_FAILED,
    ENFORCEMENT_UNAVAILABLE,
    CLEANUP_PENDING
}

@Serializable
enum class EnforcementMechanism {
    NO_OP,
    DEVICE_OWNER,
    ACCESSIBILITY
}

@Serializable
sealed class EnforcementResult {
    @Serializable
    data class Success(val appliedLedger: EnforcementLedger) : EnforcementResult()
    @Serializable
    data class Simulated(val appliedLedger: EnforcementLedger) : EnforcementResult()
    @Serializable
    data object PartialFailure : EnforcementResult()
    @Serializable
    data object Failure : EnforcementResult()
    @Serializable
    data object Unavailable : EnforcementResult()
}

@Serializable
data class EnforcementSnapshot(
    val sessionId: String,
    val userId: String,
    val timestamp: Long,
    val policies: List<ResolvedPolicy>
)

@Serializable
data class EnforcementLedgerEntry(
    val packageName: String,
    val mechanism: EnforcementMechanism,
    val appliedAt: Long,
    val sessionId: String
)

@Serializable
data class EnforcementLedger(
    val entries: Map<String, EnforcementLedgerEntry> = emptyMap()
)
