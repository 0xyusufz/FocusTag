package com.focustag.app.ui.dashboard

import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.model.InterceptionEvent
import com.focustag.app.data.model.SessionStatus
import com.focustag.app.ui.history.HistorySessionItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object DashboardStatsCalculator {

    fun calculate(
        sessions: List<FocusSessionRecord>,
        events: List<InterceptionEvent>,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DashboardState {
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        // 1. Today's stats
        var todayDuration = 0L
        var todaySessionCount = 0
        
        sessions.forEach { session ->
            val sessionStartDate = Instant.ofEpochMilli(session.startAt).atZone(zoneId).toLocalDate()
            if (sessionStartDate == today) {
                todaySessionCount++
            }

            val effectiveEnd = when {
                session.status == SessionStatus.IN_PROGRESS -> now
                session.endAt != null -> session.endAt
                else -> session.startAt
            }

            todayDuration += calculateDayContribution(session.startAt, effectiveEnd, todayStart, todayEnd)
        }

        val todayBlockedCount = events.count { event ->
            val eventDate = Instant.ofEpochMilli(event.timestamp).atZone(zoneId).toLocalDate()
            eventDate == today
        }

        // 2. Active session
        val activeSession = sessions.find { it.status == SessionStatus.IN_PROGRESS }

        // 3. Recent sessions (top 3)
        val recentSessions = sessions.take(3).map { session ->
            val sessionEvents = events.filter { it.sessionId == session.sessionId }
            val duration = if (session.endAt != null) {
                session.endAt - session.startAt
            } else if (session.status == SessionStatus.IN_PROGRESS) {
                now - session.startAt
            } else {
                0L
            }
            HistorySessionItem(session, duration, sessionEvents.size)
        }

        // 4. Weekly stats (last 7 days)
        val weeklyStats = (0..6).reversed().map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val dStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            
            var dayDuration = 0L
            sessions.forEach { session ->
                val effectiveEnd = when {
                    session.status == SessionStatus.IN_PROGRESS -> now
                    session.endAt != null -> session.endAt
                    else -> session.startAt
                }
                dayDuration += calculateDayContribution(session.startAt, effectiveEnd, dStart, dEnd)
            }
            
            WeeklyStat(
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                durationMillis = dayDuration
            )
        }

        return DashboardState(
            todayDurationMillis = todayDuration,
            todaySessionCount = todaySessionCount,
            todayBlockedCount = todayBlockedCount,
            activeSession = activeSession,
            recentSessions = recentSessions,
            weeklyStats = weeklyStats,
            isLoading = false
        )
    }

    private fun calculateDayContribution(start: Long, end: Long, dayStart: Long, dayEnd: Long): Long {
        val overlapStart = maxOf(start, dayStart)
        val overlapEnd = minOf(end, dayEnd)
        return if (overlapStart < overlapEnd) {
            overlapEnd - overlapStart
        } else {
            0L
        }
    }
}
