package com.focustag.app.platform.enforcement

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.focustag.app.domain.enforcement.ForegroundAppDetector
import com.focustag.app.domain.enforcement.ForegroundAppSnapshot
import com.focustag.app.domain.enforcement.ForegroundMonitoringHandle

/**
 * Foreground app detector backed by UsageStatsManager.
 *
 * Requires the user to grant Usage Access for FocusTag in Android Settings.
 * Android exposes this as an app-op behind PACKAGE_USAGE_STATS, not as a normal
 * runtime permission dialog.
 */
class UsageStatsForegroundAppDetector(
    context: Context,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val queryWindowMillis: Long = DEFAULT_QUERY_WINDOW_MILLIS
) : ForegroundAppDetector {

    private val appContext = context.applicationContext
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val appOpsManager =
        appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val handler = Handler(Looper.getMainLooper())

    override fun hasRequiredPermission(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }

        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun currentForegroundApp(): ForegroundAppSnapshot? {
        if (!hasRequiredPermission()) {
            return null
        }

        val now = System.currentTimeMillis()
        return queryLatestForegroundApp(now - queryWindowMillis, now)
            ?: queryMostRecentlyUsedApp(now - FALLBACK_QUERY_WINDOW_MILLIS, now)
    }

    override fun startMonitoring(
        onForegroundAppChanged: (ForegroundAppSnapshot) -> Unit,
        onFailure: (Throwable) -> Unit
    ): ForegroundMonitoringHandle {
        var lastEmittedPackage: String? = null

        val runnable = object : Runnable {
            override fun run() {
                try {
                    val snapshot = currentForegroundApp()
                    if (snapshot != null && snapshot.packageName != lastEmittedPackage) {
                        lastEmittedPackage = snapshot.packageName
                        onForegroundAppChanged(snapshot)
                    }
                } catch (error: Throwable) {
                    onFailure(error)
                } finally {
                    handler.postDelayed(this, pollIntervalMillis)
                }
            }
        }

        handler.post(runnable)

        return object : ForegroundMonitoringHandle {
            override fun stop() {
                handler.removeCallbacks(runnable)
            }
        }
    }

    private fun queryLatestForegroundApp(
        startTimeMillis: Long,
        endTimeMillis: Long
    ): ForegroundAppSnapshot? {
        val events = usageStatsManager.queryEvents(startTimeMillis, endTimeMillis)
        val event = UsageEvents.Event()
        var latestPackageName: String? = null
        var latestTimestamp = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (isForegroundEvent(event) && event.timeStamp >= latestTimestamp) {
                latestPackageName = event.packageName
                latestTimestamp = event.timeStamp
            }
        }

        return latestPackageName?.let { packageName ->
            ForegroundAppSnapshot(
                packageName = packageName,
                detectedAtMillis = latestTimestamp
            )
        }
    }

    private fun isForegroundEvent(event: UsageEvents.Event): Boolean {
        return event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
    }

    private fun queryMostRecentlyUsedApp(
        startTimeMillis: Long,
        endTimeMillis: Long
    ): ForegroundAppSnapshot? {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTimeMillis,
            endTimeMillis
        )

        return stats
            .maxByOrNull { it.lastTimeUsed }
            ?.takeIf { it.lastTimeUsed > 0L }
            ?.let { usageStats ->
                ForegroundAppSnapshot(
                    packageName = usageStats.packageName,
                    detectedAtMillis = usageStats.lastTimeUsed
                )
            }
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 1_000L
        const val DEFAULT_QUERY_WINDOW_MILLIS = 10_000L
        const val FALLBACK_QUERY_WINDOW_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
