package com.focustag.app.ui.dashboard

import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.ui.history.HistorySessionItem

data class WeeklyStat(
    val dayName: String, // e.g., "Mon", "Tue"
    val durationMillis: Long
)

data class DashboardState(
    val todayDurationMillis: Long = 0,
    val todaySessionCount: Int = 0,
    val todayBlockedCount: Int = 0,
    val activeSession: FocusSessionRecord? = null,
    val recentSessions: List<HistorySessionItem> = emptyList(),
    val weeklyStats: List<WeeklyStat> = emptyList(),
    val isLoading: Boolean = false
)
