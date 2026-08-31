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

class SupabaseHistoryRepository {

    private fun toIsoString(millis: Long): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
    }

    suspend fun upsertSession(record: FocusSessionRecord): Result<Unit> {
        return try {
            val dto = FocusSessionRecordMapper.toDto(record, ::toIsoString)
            SupabaseModule.client.postgrest["focus_sessions"].upsert(dto) {
                onConflict = "id"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertEvent(event: InterceptionEvent): Result<Unit> {
        return try {
            val dto = InterceptionEventMapper.toDto(event, ::toIsoString)
            SupabaseModule.client.postgrest["interception_events"].upsert(dto) {
                onConflict = "id"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
}
