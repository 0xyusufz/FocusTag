package com.focustag.app.ui.dashboard

import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.model.InterceptionEvent
import com.focustag.app.data.model.SessionStatus
import com.focustag.app.ui.history.HistorySessionItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DashboardStatsCalculator {

    private val IST = ZoneId.of("Asia/Kolkata")

    fun calculate(
        sessions: List<FocusSessionRecord>,
        events: List<InterceptionEvent>,
        now: Long,
        zoneId: ZoneId = IST,
        tagMap: Map<String, String> = emptyMap()
    ): DashboardState {
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()

        // 1. Daily summaries for the last 7 days (including today)
        val dailySummaries = (0..6).reversed().map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val dStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            
            // a. Filter sessions for this day
            val daySessions = sessions.filter { session ->
                val effectiveEnd = session.endAt ?: if (session.status == SessionStatus.IN_PROGRESS) now else session.startAt
                calculateDayContribution(session.startAt, effectiveEnd, dStart, dEnd) > 0
            }

            // b. Calculate duration and location aggregation
            var dayDuration = 0L
            val locationMap = mutableMapOf<String, Pair<Long, Int>>() // tagId -> (duration, count)
            
            daySessions.forEach { session ->
                val effectiveEnd = when {
                    session.status == SessionStatus.IN_PROGRESS -> now
                    session.endAt != null -> session.endAt
                    else -> session.startAt
                }
                val contribution = calculateDayContribution(session.startAt, effectiveEnd, dStart, dEnd)
                dayDuration += contribution
                
                val tagId = session.tagId ?: "unknown"
                val (dur, count) = locationMap.getOrDefault(tagId, 0L to 0)
                // For location stats, we count a session if it started on this day
                val sessionStartedOnThisDay = Instant.ofEpochMilli(session.startAt).atZone(zoneId).toLocalDate() == date
                locationMap[tagId] = (dur + contribution) to (if (sessionStartedOnThisDay) count + 1 else count)
            }

            val locationStats = locationMap.map { (tagId, pair) ->
                LocationStat(tagId, getDisplayNameForTag(tagId, tagMap), pair.first, pair.second)
            }.filter { it.durationMillis > 0 || it.sessionCount > 0 }.sortedByDescending { it.durationMillis }

            // c. Blocked count and top distraction
            val dayEvents = events.filter { event ->
                val eventDate = Instant.ofEpochMilli(event.timestamp).atZone(zoneId).toLocalDate()
                eventDate == date
            }
            
            val topDistraction = dayEvents.groupBy { it.packageName }
                .map { (pkg, evs) -> 
                    val appName = pkg.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    DistractionStat(pkg, appName, evs.size) 
                }
                .sortedWith(compareByDescending<DistractionStat> { it.blockedCount }.thenBy { it.packageName })
                .firstOrNull()

            DailySummary(
                dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                durationMillis = dayDuration,
                sessionCount = daySessions.count { Instant.ofEpochMilli(it.startAt).atZone(zoneId).toLocalDate() == date },
                blockedCount = dayEvents.size,
                locations = locationStats,
                topDistraction = topDistraction
            )
        }

        val todayStats = dailySummaries.last()
        val weeklyStats = (0..6).reversed().map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val summary = dailySummaries.find { it.dateLabel.contains(date.format(DateTimeFormatter.ofPattern("MMM d"))) }
            WeeklyStat(
                dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                durationMillis = summary?.durationMillis ?: 0L
            )
        }

        val bestDay = dailySummaries.maxByOrNull { it.durationMillis }

        return DashboardState(
            todayDurationMillis = todayStats.durationMillis,
            todaySessionCount = todayStats.sessionCount,
            todayBlockedCount = todayStats.blockedCount,
            todayLocationCount = todayStats.locations.size,
            todayLocations = todayStats.locations,
            topDistraction = todayStats.topDistraction,
            activeSession = sessions.find { it.status == SessionStatus.IN_PROGRESS },
            recentSessions = sessions.take(3).map { session ->
                val sessionEvents = events.filter { it.sessionId == session.sessionId }
                val duration = if (session.endAt != null) {
                    session.endAt - session.startAt
                } else if (session.status == SessionStatus.IN_PROGRESS) {
                    now - session.startAt
                } else {
                    0L
                }
                HistorySessionItem(session, duration, sessionEvents.size)
            },
            weeklyStats = weeklyStats,
            dailySummaries = dailySummaries.reversed(),
            bestDay = bestDay,
            isLoading = false,
            tagMap = tagMap
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

    private fun getDisplayNameForTag(tagId: String, tagMap: Map<String, String>): String {
        if (tagId == "simulated_tag_01") return "Simulated Tag"
        if (tagId == "unknown") return "Unknown Location"
        return tagMap[tagId] ?: tagId
    }
}
