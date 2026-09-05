package com.focustag.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class StudentFocusStatus {
    ACTIVE,
    NOT_ACTIVE
}

@Serializable
data class TeacherClass(
    val id: String,
    val name: String,
    val locationName: String,
    val studentCount: Int
)

@Serializable
data class RosterStudent(
    val id: String,
    val name: String,
    val status: StudentFocusStatus
)
