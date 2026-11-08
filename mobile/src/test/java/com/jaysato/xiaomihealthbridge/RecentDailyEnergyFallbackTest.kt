package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentDailyEnergyFallbackTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val today = LocalDate.parse("2026-10-08")

    @Test fun usesHighestSourceTotalWithoutAddingDifferentSources() {
        val result = RecentDailyEnergyFallback.latestDailyTotal(listOf(
            record("phone", "2026-10-07T02:00:00Z", "2026-10-07T03:00:00Z", 40.0),
            record("watch", "2026-10-07T02:00:00Z", "2026-10-07T03:00:00Z", 30.0),
            record("watch", "2026-10-07T03:00:00Z", "2026-10-07T04:00:00Z", 50.0),
            record("watch", "2026-10-09T02:00:00Z", "2026-10-09T03:00:00Z", 999.0)
        ), zone, today)
        assertEquals(80.0, result!!.kilocalories, 0.001)
        assertEquals(2, result.originCount)
        assertEquals(LocalDate.parse("2026-10-07"), result.date)
    }

    @Test fun rejectsOverlappingOrInvalidRecordsWithinDay() {
        assertNull(RecentDailyEnergyFallback.latestDailyTotal(listOf(
            record("watch", "2026-10-07T02:00:00Z", "2026-10-07T03:00:00Z", 30.0),
            record("watch", "2026-10-07T02:30:00Z", "2026-10-07T04:00:00Z", 50.0)
        ), zone, today))
        assertNull(RecentDailyEnergyFallback.latestDailyTotal(listOf(
            record("watch", "2026-10-07T02:00:00Z", "2026-10-07T03:00:00Z", Double.NaN)
        ), zone, today))
        assertNull(RecentDailyEnergyFallback.latestDailyTotal(listOf(
            record("", "2026-10-07T02:00:00Z", "2026-10-07T03:00:00Z", 30.0)
        ), zone, today))
    }

    @Test fun acceptsExactMidnightEndButRejectsCrossMidnight() {
        val exact = record("watch", "2026-10-07T16:00:00Z", "2026-10-08T16:00:00Z", 200.0)
        assertEquals(200.0, RecentDailyEnergyFallback.latestDailyTotal(listOf(exact), zone, today)!!.kilocalories, 0.001)
        assertNull(RecentDailyEnergyFallback.latestDailyTotal(listOf(
            record("watch", "2026-10-07T15:50:00Z", "2026-10-07T16:10:00Z", 30.0)
        ), zone, today))
    }

    private fun record(origin: String, start: String, end: String, kilocalories: Double) =
        RecentDailyEnergyFallback.Record(origin, Instant.parse(start), Instant.parse(end), kilocalories)
}
