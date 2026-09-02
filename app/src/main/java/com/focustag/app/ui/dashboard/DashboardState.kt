package com.focustag.app.ui.dashboard

import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.ui.history.HistorySessionItem

data class WeeklyStat(
    val dayName: String,
    val durationMillis: Long
)

data class LocationStat(
    val tagId: String,
    val displayName: String,
    val durationMillis: Long,
    val sessionCount: Int
)

data class DistractionStat(
    val packageName: String,
    val appName: String,
    val blockedCount: Int
)

data class DailySummary(
    val dateLabel: String, // e.g. "Monday, Sept 2"
    val durationMillis: Long,
    val sessionCount: Int,
    val blockedCount: Int,
    val locations: List<LocationStat>,
    val topDistraction: DistractionStat?
)

data class DashboardState(
    val todayDurationMillis: Long = 0,
    val todaySessionCount: Int = 0,
    val todayBlockedCount: Int = 0,
    val todayLocationCount: Int = 0,
    val todayLocations: List<LocationStat> = emptyList(),
    val topDistraction: DistractionStat? = null,
    val activeSession: FocusSessionRecord? = null,
    val recentSessions: List<HistorySessionItem> = emptyList(),
    val weeklyStats: List<WeeklyStat> = emptyList(),
    val dailySummaries: List<DailySummary> = emptyList(),
    val bestDay: DailySummary? = null,
    val isLoading: Boolean = false
)
