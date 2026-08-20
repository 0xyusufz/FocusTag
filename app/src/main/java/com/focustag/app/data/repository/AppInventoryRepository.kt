package com.focustag.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.focustag.app.data.model.AppCategory
import com.focustag.app.data.model.AppInfo

class AppInventoryRepository(private val context: Context) {

    private val restrictedPackages = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.facebook.katana",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.snapchat.android"
    )

    private val systemRequiredPackages = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.systemui"
    )

    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val myPackageName = context.packageName

        return resolveInfos.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(pm).toString()
            
            val category = when {
                packageName == myPackageName -> AppCategory.CORE
                restrictedPackages.contains(packageName) -> AppCategory.RESTRICTED
                systemRequiredPackages.contains(packageName) -> AppCategory.SYSTEM_REQUIRED
                // Heuristic: system apps without classification are UNCLASSIFIED, user apps ALLOWABLE
                else -> AppCategory.ALLOWABLE 
            }

            AppInfo(packageName, appName, category)
        }.distinctBy { it.packageName }.sortedBy { it.appName }
    }
}
