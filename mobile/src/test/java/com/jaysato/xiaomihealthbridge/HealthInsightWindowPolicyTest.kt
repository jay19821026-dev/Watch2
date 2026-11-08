package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class HealthInsightWindowPolicyTest {
    @Test
    fun sevenDayWindowStartsAtLocalMidnightIncludingTheFirstCalendarDay() {
        val zone = ZoneId.of("Asia/Taipei")
        val now = Instant.parse("2026-11-08T11:30:00Z")

        assertEquals(
            Instant.parse("2026-11-01T16:00:00Z"),
            HealthInsightWindowPolicy.lastSevenLocalDatesStart(now, zone)
        )
    }

    @Test
    fun sevenDayWindowUsesCalendarDatesAcrossDaylightSavingChange() {
        val zone = ZoneId.of("America/Los_Angeles")
        val now = Instant.parse("2026-03-10T19:00:00Z")
        val start = HealthInsightWindowPolicy.lastSevenLocalDatesStart(now, zone)

        assertEquals(Instant.parse("2026-03-04T08:00:00Z"), start)
        assertEquals(155L, Duration.between(start, now).toHours())
    }

    @Test
    fun sleepingAcrossTheWindowBoundaryIsKeptForClippedAnalysis() {
        data class Session(val start: Instant, val end: Instant)
        val start = Instant.parse("2026-11-01T16:00:00Z")
        val end = Instant.parse("2026-11-08T11:30:00Z")
        val crossing = Session(start.minusSeconds(3600), start.plusSeconds(3600))
        val old = Session(start.minusSeconds(7200), start.minusSeconds(1))
        val future = Session(end.plusSeconds(1), end.plusSeconds(3600))

        assertEquals(
            listOf(crossing),
            HealthInsightWindowPolicy.retainOverlappingIntervals(
                listOf(old, crossing, future), start, end, { it.start }, { it.end }
            )
        )
    }
}
