package com.focustag.app.data.model

enum class AppCategory {
    CORE,
    SYSTEM_REQUIRED,
    RESTRICTED,
    ALLOWABLE,
    UNCLASSIFIED
}

enum class FocusAction {
    ALLOW,
    BLOCK,
    PROTECTED
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val category: AppCategory
)

data class ResolvedPolicy(
    val appInfo: AppInfo,
    val action: FocusAction
)
