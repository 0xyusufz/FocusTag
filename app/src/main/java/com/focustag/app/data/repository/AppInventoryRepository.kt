package com.focustag.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.focustag.app.data.model.AppCategory
import com.focustag.app.data.model.AppInfo

open class AppInventoryRepository(private val context: Context?) {

    private val alwaysRestrictedPackages = setOf(
        "com.android.settings",
        "com.android.vending", // Play Store
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.microsoft.emmx", // Edge
        "com.sec.android.app.sbrowser", // Samsung Internet
        "com.opera.browser",
        "com.brave.browser",
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.facebook.katana",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.snapchat.android"
    )

    open fun getInstalledApps(): List<AppInfo> {
        val pm = context!!.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val myPackageName = context.packageName

        return resolveInfos.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(pm).toString()

            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            val category = when {
                packageName == myPackageName -> AppCategory.CORE
                alwaysRestrictedPackages.contains(packageName) -> AppCategory.RESTRICTED
                appInfo?.category == ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.ALLOWABLE
                else -> AppCategory.RESTRICTED
            }

            AppInfo(packageName, appName, category)
        }.distinctBy { it.packageName }.sortedBy { it.appName }
    }
}
//package com.focustag.app.data.repository
//
//import android.content.Context
//import android.content.Intent
//import android.content.pm.ApplicationInfo
//import android.content.pm.PackageManager
//import com.focustag.app.data.model.AppCategory
//import com.focustag.app.data.model.AppInfo
//
//open class AppInventoryRepository(private val context: Context?) {
//
//    // A small, unambiguous set of OS/shell apps. Kept explicit rather than
//    // heuristic: misclassifying one of these could lock the user out of
//    // Settings or the package installer, so it isn't worth guessing.
//    private val systemRequiredPackages = setOf(
//        "com.android.settings",
//        "com.google.android.packageinstaller",
//        "com.android.systemui"
//    )
//
//    // Play Store categories that are reliably "distraction" apps.
//    // ApplicationInfo.category reflects the category an app declares via
//    // android:appCategory in its own manifest (API 26+, matching our minSdk) —
//    // the same signal Android's own Digital Wellbeing / Focus Mode uses.
//    // No package names to maintain: a newly installed social/game/video app
//    // is classified automatically, with no code change required.
//    private val distractionCategories = setOf(
//        ApplicationInfo.CATEGORY_SOCIAL,
//        ApplicationInfo.CATEGORY_GAME,
//        ApplicationInfo.CATEGORY_VIDEO
//    )
//
//    open fun getInstalledApps(): List<AppInfo> {
//        val pm = context!!.packageManager
//        val intent = Intent(Intent.ACTION_MAIN, null).apply {
//            addCategory(Intent.CATEGORY_LAUNCHER)
//        }
//
//        val resolveInfos = pm.queryIntentActivities(intent, 0)
//        val myPackageName = context.packageName
//
//        return resolveInfos.map { resolveInfo ->
//            val packageName = resolveInfo.activityInfo.packageName
//            val appName = resolveInfo.loadLabel(pm).toString()
//            val category = classify(pm, packageName, myPackageName)
//
//            AppInfo(packageName, appName, category)
//        }.distinctBy { it.packageName }.sortedBy { it.appName }
//    }
//
//    private fun classify(pm: PackageManager, packageName: String, myPackageName: String): AppCategory {
//        return when {
//            packageName == myPackageName -> AppCategory.CORE
//            systemRequiredPackages.contains(packageName) -> AppCategory.SYSTEM_REQUIRED
//            isDistractionCategory(pm, packageName) -> AppCategory.RESTRICTED
//            else -> AppCategory.ALLOWABLE
//        }
//    }
//
//    /**
//     * Reads the app's self-declared Play Store category and checks it against
//     * [distractionCategories]. Apps that don't declare a category (or declare
//     * one outside this set) fall through to ALLOWABLE — the user can still
//     * block them manually from the app-selection screen.
//     */
//    private fun isDistractionCategory(pm: PackageManager, packageName: String): Boolean {
//        val declaredCategory = try {
//            pm.getApplicationInfo(packageName, 0).category
//        } catch (e: PackageManager.NameNotFoundException) {
//            return false
//        }
//        return declaredCategory in distractionCategories
//    }
//}