package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentDailyMetricFallbackTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val today = LocalDate.parse("2026-10-08")

    @Test
    fun sumsNonOverlappingRecordsWithinOneSourceAndSkipsFutureDay() {
        val result = RecentDailyMetricFallback.latestDailyTotal(
            listOf(
                record("watch", "2026-10-06T08:00:00Z", "2026-10-06T09:00:00Z", 100),
                record("watch", "2026-10-07T02:00:00Z", "2026-10-07T03:00:00Z", 2_000),
                record("watch", "2026-10-07T14:00:00Z", "2026-10-07T15:00:00Z", 2_373),
                record("watch", "2026-10-09T02:00:00Z", "2026-10-09T03:00:00Z", 9_999)
            ), zone, today
        )

        assertEquals(LocalDate.parse("2026-10-07"), result?.date)
        assertEquals(4_373L, result?.total)
        assertEquals(false, result?.isSourceMaximum)
    }

    @Test
    fun emptyOrOnlyInvalidRecordsHaveNoFallback() {
        assertNull(RecentDailyMetricFallback.latestDailyTotal(emptyList(), zone, today))
        assertNull(
            RecentDailyMetricFallback.latestDailyTotal(
                listOf(record("watch", "2026-10-07T10:00:00Z", "2026-10-07T11:00:00Z", -1)), zone, today
            )
        )
    }

    @Test
    fun overlappingSourcesReturnLabeledMaximumInsteadOfDoubleCount() {
        val result = RecentDailyMetricFallback.latestDailyTotal(listOf(
            record("phone", "2026-10-07T02:00:00Z", "2026-10-07T03:00:00Z", 2_000),
            record("watch", "2026-10-07T02:00:00Z", "2026-10-07T03:00:00Z", 2_373)
        ), zone, today)
        assertEquals(2_373L, result?.total)
        assertEquals(2, result?.originCount)
        assertEquals(true, result?.isSourceMaximum)
    }

    @Test
    fun overlappingIntervalsWithinOneSourceCannotBeAdded() {
        assertNull(RecentDailyMetricFallback.latestDailyTotal(listOf(
            record("watch", "2026-10-07T02:00:00Z", "2026-10-07T03:00:00Z", 2_000),
            record("watch", "2026-10-07T02:30:00Z", "2026-10-07T03:30:00Z", 2_373)
        ), zone, today))
    }

    @Test
    fun crossingLocalMidnightCannotBeTreatedAsWholeDay() {
        assertNull(RecentDailyMetricFallback.latestDailyTotal(listOf(
            record("watch", "2026-10-07T15:50:00Z", "2026-10-07T16:10:00Z", 200)
        ), zone, today))
    }

    @Test
    fun recordEndingAtNextLocalMidnightBelongsToPreviousDay() {
        val result = RecentDailyMetricFallback.latestDailyTotal(listOf(
            record("watch", "2026-10-07T16:00:00Z", "2026-10-08T16:00:00Z", 10_000)
        ), zone, today)
        assertEquals(LocalDate.parse("2026-10-08"), result?.date)
        assertEquals(10_000L, result?.total)
    }

    private fun record(origin: String, start: String, end: String, count: Long) =
        RecentDailyMetricFallback.Record(origin, Instant.parse(start), Instant.parse(end), count)
}
