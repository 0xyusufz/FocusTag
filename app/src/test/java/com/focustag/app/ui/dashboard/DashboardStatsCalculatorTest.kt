package com.focustag.app.ui.dashboard

import com.focustag.app.data.model.FocusSessionRecord
import com.focustag.app.data.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DashboardStatsCalculatorTest {

    private val zoneId = ZoneId.of("Asia/Kolkata")
    
    @Test
    fun `calculate today duration - session entirely within today`() {
        // Today is 2026-09-02 in IST
        // 2026-09-02 10:00 IST is 2026-09-02 04:30 UTC
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
        // Current time: 2026-09-02 12:00 UTC -> 17:30 IST
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        
        // IST Midnight: 2026-09-01 18:30 UTC
        // Session starting at 18:25 UTC (Yesterday in IST) and ending at 18:35 UTC (Today in IST)
        val sessions = listOf(
            FocusSessionRecord(
                sessionId = "1",
                userId = "u1",
                tagId = null,
                startAt = Instant.parse("2026-09-01T18:25:00Z").toEpochMilli(),
                endAt = Instant.parse("2026-09-01T18:35:00Z").toEpochMilli(),
                status = SessionStatus.COMPLETED
            )
        )
        
        val state = DashboardStatsCalculator.calculate(sessions, emptyList(), now, zoneId)
        
        // Contribution to today (Sept 2nd IST) should be 5 minutes = 300000 ms
        assertEquals(300000L, state.todayDurationMillis)
        // Session count is based on startAt, so Sept 1st IST session doesn't count for TODAY (Sept 2nd)
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
                timestamp = Instant.parse("2026-09-02T10:30:00Z").toEpochMilli() // Today in IST
            ),
            com.focustag.app.data.model.InterceptionEvent(
                eventId = "e2",
                sessionId = "s1",
                userId = "u1",
                packageName = "com.bad.app",
                timestamp = Instant.parse("2026-09-01T15:30:00Z").toEpochMilli() // Yesterday in IST (before 18:30 UTC)
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

    @Test
    fun `calculate location aggregation`() {
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        val libTag = "1D:FF:7C:1C:1A:10:80"
        val c2Tag = "1D:3D:70:1C:1A:10:80"
        
        val tagMap = mapOf(
            libTag to "Library 1",
            c2Tag to "Classroom 2"
        )
        
        val sessions = listOf(
            FocusSessionRecord("s1", "u1", libTag, now - 3600000, now - 1800000, SessionStatus.COMPLETED),
            FocusSessionRecord("s2", "u1", c2Tag, now - 1200000, now, SessionStatus.COMPLETED)
        )
        
        val state = DashboardStatsCalculator.calculate(sessions, emptyList(), now, zoneId, tagMap = tagMap)
        
        assertEquals(2, state.todayLocationCount)
        // Sort by duration descending in calculator
        assertEquals("Library 1", state.todayLocations[0].displayName)
        assertEquals("Classroom 2", state.todayLocations[1].displayName)
    }

    @Test
    fun `unknown tag resolves to UID`() {
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        val unknownTag = "AA:BB:CC:DD:EE:FF:00"
        val sessions = listOf(
            FocusSessionRecord("s1", "u1", unknownTag, now - 1000, now, SessionStatus.COMPLETED)
        )
        
        val state = DashboardStatsCalculator.calculate(sessions, emptyList(), now, zoneId, tagMap = emptyMap())
        
        assertEquals(unknownTag, state.todayLocations[0].displayName)
    }

    @Test
    fun `simulated tag resolves correctly`() {
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        val sessions = listOf(
            FocusSessionRecord("s1", "u1", "simulated_tag_01", now - 1000, now, SessionStatus.COMPLETED)
        )
        
        val state = DashboardStatsCalculator.calculate(sessions, emptyList(), now, zoneId, tagMap = emptyMap())
        
        assertEquals("Simulated Tag", state.todayLocations[0].displayName)
    }

    @Test
    fun `calculate top distraction`() {
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        val events = listOf(
            com.focustag.app.data.model.InterceptionEvent("e1", "s1", "u1", "com.insta", now - 1000),
            com.focustag.app.data.model.InterceptionEvent("e2", "s1", "u1", "com.insta", now - 2000),
            com.focustag.app.data.model.InterceptionEvent("e3", "s1", "u1", "com.fb", now - 3000)
        )
        
        val state = DashboardStatsCalculator.calculate(emptyList(), events, now, zoneId)
        
        assertEquals("com.insta", state.topDistraction?.packageName)
        assertEquals("Insta", state.topDistraction?.appName)
        assertEquals(2, state.topDistraction?.blockedCount)
    }

    @Test
    fun `empty dataset handling`() {
        val now = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        val state = DashboardStatsCalculator.calculate(emptyList(), emptyList(), now, zoneId)
        
        assertEquals(0, state.todayDurationMillis)
        assertEquals(0, state.todaySessionCount)
        assertEquals(0, state.todayLocations.size)
        assertEquals(null, state.topDistraction)
        assertEquals(7, state.weeklyStats.size)
        assertEquals(7, state.dailySummaries.size)
    }
}
