package com.focustag.app.data.repository
//teacherrepository
import android.util.Log
import com.focustag.app.data.model.RosterStudent
import com.focustag.app.data.model.StudentFocusStatus
import com.focustag.app.data.model.TeacherClass
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG = "TeacherRepo"

@Serializable
data class ClassWithLocationDto(
    val id: String,
    val name: String,
    val location: LocationDto,
    @SerialName("enrollments")
    val studentIds: List<StudentIdDto> = emptyList()
)

@Serializable
data class StudentIdDto(
    @SerialName("student_id")
    val studentId: String
)

@Serializable
data class LocationDto(
    val id: String,
    val name: String
)

@Serializable
data class TeacherAccessDto(
    @SerialName("class_id")
    val classId: String,
    @SerialName("classes")
    val classInfo: ClassWithLocationDto
)

@Serializable
data class EnrollmentWithProfileDto(
    @SerialName("student_id")
    val studentId: String,
    @SerialName("profiles")
    val profile: ProfileSimpleDto
)

@Serializable
data class ProfileSimpleDto(
    val name: String?
)

@Serializable
data class SessionSimpleDto(
    @SerialName("user_id")
    val userId: String
)

interface TeacherRepository {
    suspend fun getMyClasses(): Result<List<TeacherClass>>
    suspend fun getClassRoster(classId: String): Result<List<RosterStudent>>
}

class SupabaseTeacherRepository : TeacherRepository {

    override suspend fun getMyClasses(): Result<List<TeacherClass>> {
        return try {
            Log.d(TAG, "Fetching classes for teacher")
            // Join teacher_class_access -> classes -> locations
            // Also fetch enrollment student IDs to get count
            val results = SupabaseModule.client.from("teacher_class_access")
                .select(columns = Columns.raw("class_id, classes(id, name, location:locations(id, name), enrollments(student_id))"))
                .decodeList<TeacherAccessDto>()
            
            val classes = results.map { access ->
                TeacherClass(
                    id = access.classInfo.id,
                    name = access.classInfo.name,
                    locationName = access.classInfo.location.name,
                    studentCount = access.classInfo.studentIds.size
                )
            }
            Result.success(classes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch classes: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getClassRoster(classId: String): Result<List<RosterStudent>> {
        return try {
            Log.d(TAG, "Fetching roster for class: $classId")
            
            // 1. Fetch class info to get location_id
            val classInfo = SupabaseModule.client.from("classes")
                .select(columns = Columns.raw("id, location_id")) {
                    filter { eq("id", classId) }
                }
                .decodeSingle<ClassWithLocationIdDto>()

            // 2. Fetch valid UIDs for that location
            val tags = SupabaseModule.client.from("nfc_tags")
                .select(columns = Columns.raw("uid")) {
                    filter { 
                        eq("location_id", classInfo.locationId)
                        eq("is_active", true)
                    }
                }
                .decodeList<NfcUidDto>()
            val validUids = tags.map { it.uid }

            // 3. Fetch enrolled students
            val enrollments = SupabaseModule.client.from("enrollments")
                .select(columns = Columns.raw("student_id, profiles(name)")) {
                    filter { eq("class_id", classId) }
                }
                .decodeList<EnrollmentWithProfileDto>()

            // 4. Fetch active sessions for these students at this location
            // RLS will ensure the teacher only sees what they are authorized for
            val activeSessions = if (validUids.isNotEmpty()) {
                SupabaseModule.client.from("focus_sessions")
                    .select(columns = Columns.raw("user_id")) {
                        filter {
                            eq("status", "IN_PROGRESS")
                            isIn("tag_id", validUids)
                        }
                    }
                    .decodeList<SessionSimpleDto>()
            } else emptyList()

            val activeUserIds = activeSessions.map { it.userId }.toSet()

            val roster = enrollments.map { e ->
                RosterStudent(
                    id = e.studentId,
                    name = e.profile.name ?: "Unknown Student",
                    status = if (activeUserIds.contains(e.studentId)) StudentFocusStatus.ACTIVE else StudentFocusStatus.NOT_ACTIVE
                )
            }.sortedBy { it.name }

            Result.success(roster)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch roster: ${e.message}", e)
            Result.failure(e)
        }
    }
}

@Serializable
data class ClassWithLocationIdDto(
    val id: String,
    @SerialName("location_id")
    val locationId: String
)

@Serializable
data class NfcUidDto(
    val uid: String
)
