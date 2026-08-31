package com.focustag.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    INTERRUPTED
}

@Serializable
data class FocusSessionRecord(
    val sessionId: String,
    val userId: String,
    val tagId: String?,
    val startAt: Long,
    val endAt: Long? = null,
    val status: SessionStatus,
    val syncDirty: Boolean = true
)

@Serializable
data class InterceptionEvent(
    val eventId: String,
    val sessionId: String,
    val userId: String,
    val packageName: String,
    val timestamp: Long,
    val syncDirty: Boolean = true
)
