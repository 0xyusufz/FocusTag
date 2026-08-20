package com.focustag.app.domain

import com.focustag.app.data.model.AppCategory
import com.focustag.app.data.model.AppInfo
import com.focustag.app.data.model.FocusAction
import com.focustag.app.data.model.ResolvedPolicy

object PolicyEngine {

    fun resolve(app: AppInfo, userBlockedSet: Set<String>): ResolvedPolicy {
        val action = when (app.category) {
            AppCategory.CORE -> FocusAction.PROTECTED
            AppCategory.RESTRICTED -> FocusAction.BLOCK
            AppCategory.ALLOWABLE -> {
                if (userBlockedSet.contains(app.packageName)) {
                    FocusAction.BLOCK
                } else {
                    FocusAction.ALLOW
                }
            }
            AppCategory.SYSTEM_REQUIRED, AppCategory.UNCLASSIFIED -> FocusAction.ALLOW
        }
        
        return ResolvedPolicy(app, action)
    }

    fun resolveList(apps: List<AppInfo>, userBlockedSet: Set<String>): List<ResolvedPolicy> {
        return apps.map { resolve(it, userBlockedSet) }
    }
}
