package com.focustag.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppCategory {
    CORE,
    SYSTEM_REQUIRED,
    RESTRICTED,
    ALLOWABLE,
    UNCLASSIFIED
}

@Serializable
enum class FocusAction {
    ALLOW,
    BLOCK,
    PROTECTED
}

@Serializable
data class AppInfo(
    val packageName: String,
    val appName: String,
    val category: AppCategory
)

@Serializable
data class ResolvedPolicy(
    val appInfo: AppInfo,
    val action: FocusAction
)
