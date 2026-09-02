package com.focustag.app.data.repository

import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.model.InterceptionEvent
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter

@Serializable
data class FocusSessionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("tag_id") val tagId: String?,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String?,
    val status: String
)

@Serializable
data class InterceptionEventDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("created_at") val createdAt: String
)

sealed class SyncError : Exception() {
    data class Permanent(val code: String?, override val message: String?) : SyncError()
    data class Retryable(override val message: String?) : SyncError()
    data object Conflict : SyncError() // Specifically for 409
}

class SupabaseHistoryRepository {

    private fun toIsoString(millis: Long): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
    }

    private fun fromIsoString(iso: String): Long {
        return Instant.parse(iso).toEpochMilli()
    }

    private fun classifyError(e: Exception): SyncError {
        if (e is io.github.jan.supabase.exceptions.RestException) {
            val status = e.response.status.value
            return when (status) {
                409 -> SyncError.Conflict
                401 -> SyncError.Retryable("Unauthorized - needs refresh")
                400, 403, 422 -> SyncError.Permanent(status.toString(), e.message)
                429 -> SyncError.Retryable("Rate limited: ${e.message}")
                in 500..599 -> SyncError.Retryable("Server error: ${e.message}")
                else -> SyncError.Permanent(status.toString(), e.message)
            }
        }
        val msg = e.message?.lowercase() ?: ""
        if (msg.contains("timeout") || msg.contains("network") || msg.contains("connectivity")) {
            return SyncError.Retryable(e.message)
        }
        return SyncError.Permanent("unknown", e.message)
    }

    suspend fun fetchSessions(userId: String): Result<List<FocusSessionRecord>> {
        return try {
            val dtos = SupabaseModule.client.postgrest["focus_sessions"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<FocusSessionDto>()
            val records = dtos.map { FocusSessionRecordMapper.fromDto(it, ::fromIsoString) }
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchEvents(userId: String): Result<List<InterceptionEvent>> {
        return try {
            val dtos = SupabaseModule.client.postgrest["interception_events"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<InterceptionEventDto>()
            val events = dtos.map { InterceptionEventMapper.fromDto(it, ::fromIsoString) }
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertSession(record: FocusSessionRecord): Result<Unit> {
        return try {
            val dto = FocusSessionRecordMapper.toDto(record, ::toIsoString)
            SupabaseModule.client.postgrest["focus_sessions"].upsert(dto) {
                onConflict = "id"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(classifyError(e))
        }
    }

    suspend fun insertEvent(event: InterceptionEvent): Result<Unit> {
        return try {
            val dto = InterceptionEventMapper.toDto(event, ::toIsoString)
            // Use insert() instead of upsert() for S3 append-only compliance
            SupabaseModule.client.postgrest["interception_events"].insert(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            val classified = classifyError(e)
            if (classified is SyncError.Conflict) {
                // 409 Conflict means it's already there (idempotent success)
                Result.success(Unit)
            } else {
                Result.failure(classified)
            }
        }
    }
}

object FocusSessionRecordMapper {
    fun toDto(record: FocusSessionRecord, dateTransformer: (Long) -> String): FocusSessionDto {
        return FocusSessionDto(
            id = record.sessionId,
            userId = record.userId,
            tagId = record.tagId,
            startAt = dateTransformer(record.startAt),
            endAt = record.endAt?.let { dateTransformer(it) },
            status = record.status.name
        )
    }

    fun fromDto(dto: FocusSessionDto, dateParser: (String) -> Long): FocusSessionRecord {
        return FocusSessionRecord(
            sessionId = dto.id,
            userId = dto.userId,
            tagId = dto.tagId,
            startAt = dateParser(dto.startAt),
            endAt = dto.endAt?.let { dateParser(it) },
            status = com.focustag.app.data.model.SessionStatus.valueOf(dto.status),
            syncDirty = false
        )
    }
}

object InterceptionEventMapper {
    fun toDto(event: InterceptionEvent, dateTransformer: (Long) -> String): InterceptionEventDto {
        return InterceptionEventDto(
            id = event.eventId,
            userId = event.userId,
            sessionId = event.sessionId,
            packageName = event.packageName,
            createdAt = dateTransformer(event.timestamp)
        )
    }

    fun fromDto(dto: InterceptionEventDto, dateParser: (String) -> Long): InterceptionEvent {
        return InterceptionEvent(
            eventId = dto.id,
            sessionId = dto.sessionId,
            userId = dto.userId,
            packageName = dto.packageName,
            timestamp = dateParser(dto.createdAt),
            syncDirty = false
        )
    }
}
