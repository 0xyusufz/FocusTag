package com.focustag.app.ui.dashboard

import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DashboardStatsCalculatorTest {

    private val zoneId = ZoneId.of("UTC")
    
    @Test
    fun `calculate today duration - session entirely within today`() {
        // Today is 2026-09-02
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        
        val sessions = listOf(
            FocusSessionRecord(
                sessionId = "1",
                userId = "u1",
                tagId = null,
                startAt = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli(),
                endAt = Instant.parse("2026-09-02T11:00:00Z").toEpochMilli(),
                status = SessionStatus.COMPLETED
            )
        )
        
        val state = DashboardStatsCalculator.calculate(sessions, emptyList(), now, zoneId)
        
        // Duration should be 1 hour = 3600000 ms
        assertEquals(3600000L, state.todayDurationMillis)
        assertEquals(1, state.todaySessionCount)
    }

    @Test
    fun `calculate today duration - session crossing midnight (start yesterday)`() {
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        
        val sessions = listOf(
            FocusSessionRecord(
                sessionId = "1",
                userId = "u1",
                tagId = null,
                startAt = Instant.parse("2026-09-01T23:50:00Z").toEpochMilli(),
                endAt = Instant.parse("2026-09-02T00:10:00Z").toEpochMilli(),
                status = SessionStatus.COMPLETED
            )
        )
        
        val state = DashboardStatsCalculator.calculate(sessions, emptyList(), now, zoneId)
        
        // Contribution to today (Sept 2nd) should be 10 minutes = 600000 ms
        assertEquals(600000L, state.todayDurationMillis)
        // Session count is based on startAt, so SEPT 1st session doesn't count for TODAY (SEPT 2nd)
        assertEquals(0, state.todaySessionCount)
    }

    @Test
    fun `calculate today duration - active session contribution`() {
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        
        val sessions = listOf(
            FocusSessionRecord(
                sessionId = "1",
                userId = "u1",
                tagId = null,
                startAt = Instant.parse("2026-09-02T11:30:00Z").toEpochMilli(),
                status = SessionStatus.IN_PROGRESS
            )
        )
        
        val state = DashboardStatsCalculator.calculate(sessions, emptyList(), now, zoneId)
        
        // Duration should be 30 mins = 1800000 ms
        assertEquals(1800000L, state.todayDurationMillis)
        assertEquals(1, state.todaySessionCount)
        assertEquals("1", state.activeSession?.sessionId)
    }

    @Test
    fun `calculate blocked count for today`() {
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        
        val events = listOf(
            com.focustag.app.data.model.InterceptionEvent(
                eventId = "e1",
                sessionId = "s1",
                userId = "u1",
                packageName = "com.bad.app",
                timestamp = Instant.parse("2026-09-02T10:30:00Z").toEpochMilli()
            ),
            com.focustag.app.data.model.InterceptionEvent(
                eventId = "e2",
                sessionId = "s1",
                userId = "u1",
                packageName = "com.bad.app",
                timestamp = Instant.parse("2026-09-01T23:30:00Z").toEpochMilli() // Yesterday
            )
        )
        
        val state = DashboardStatsCalculator.calculate(emptyList(), events, now, zoneId)
        
        assertEquals(1, state.todayBlockedCount)
    }

    @Test
    fun `calculate weekly stats`() {
        // Today is Wednesday, Sept 2nd
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        
        val sessions = listOf(
            FocusSessionRecord(
                sessionId = "s1", userId = "u1", tagId = null,
                startAt = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli(),
                endAt = Instant.parse("2026-09-02T11:00:00Z").toEpochMilli(),
                status = SessionStatus.COMPLETED
            ),
            FocusSessionRecord(
                sessionId = "s2", userId = "u1", tagId = null,
                startAt = Instant.parse("2026-09-01T10:00:00Z").toEpochMilli(),
                endAt = Instant.parse("2026-09-01T11:00:00Z").toEpochMilli(),
                status = SessionStatus.COMPLETED
            )
        )
        
        val state = DashboardStatsCalculator.calculate(sessions, emptyList(), now, zoneId)
        
        // Weekly stats should have 7 entries
        assertEquals(7, state.weeklyStats.size)
        // Today (Index 6, last entry because of (0..6).reversed().map)
        // Wait, (0..6).reversed() is 6, 5, 4, 3, 2, 1, 0.
        // Today is daysAgo = 0, so it's the LAST entry in the list.
        assertEquals("Wed", state.weeklyStats[6].dayName)
        assertEquals(3600000L, state.weeklyStats[6].durationMillis)
        
        // Yesterday (daysAgo = 1, so index 5)
        assertEquals("Tue", state.weeklyStats[5].dayName)
        assertEquals(3600000L, state.weeklyStats[5].durationMillis)
    }
}
