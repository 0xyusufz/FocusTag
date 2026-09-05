package com.focustag.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String? = null,
    @SerialName("role")
    val role: String = "student",
    @SerialName("institution_id")
    val institutionId: String? = null
)
