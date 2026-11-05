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
    fun sumsLatestRecordedDayAndDoesNotIncludeFutureRecords() {
        val result = RecentDailyMetricFallback.latestDailyTotal(
            listOf(
                Instant.parse("2026-10-06T08:00:00Z") to 100L,
                Instant.parse("2026-10-07T02:00:00Z") to 2_000L,
                Instant.parse("2026-10-07T14:00:00Z") to 2_373L,
                Instant.parse("2026-10-09T02:00:00Z") to 9_999L
            ), zone, today
        )

        assertEquals(LocalDate.parse("2026-10-07"), result?.date)
        assertEquals(4_373L, result?.total)
    }

    @Test
    fun emptyOrOnlyInvalidRecordsHaveNoFallback() {
        assertNull(RecentDailyMetricFallback.latestDailyTotal(emptyList(), zone, today))
        assertNull(
            RecentDailyMetricFallback.latestDailyTotal(
                listOf(Instant.parse("2026-10-07T10:00:00Z") to -1L), zone, today
            )
        )
    }
}
