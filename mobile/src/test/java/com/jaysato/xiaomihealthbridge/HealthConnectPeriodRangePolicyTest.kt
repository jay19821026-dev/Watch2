package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class HealthConnectPeriodRangePolicyTest {
    @Test
    fun convertsCalendarDayBoundariesToLocalDateTimeAcrossDst() {
        val zone = ZoneId.of("America/Los_Angeles")
        val range = HealthConnectPeriodRangePolicy.localRange(
            Instant.parse("2026-03-08T08:00:00Z"),
            Instant.parse("2026-03-09T07:00:00Z"),
            zone
        )

        assertEquals(LocalDateTime.parse("2026-03-08T00:00:00"), range.first)
        assertEquals(LocalDateTime.parse("2026-03-09T00:00:00"), range.second)
    }

    @Test
    fun autumnRepeatedHourStillMapsCalendarEndpointsToLocalMidnight() {
        val zone = ZoneId.of("America/Los_Angeles")
        val range = HealthConnectPeriodRangePolicy.localRange(
            Instant.parse("2026-11-01T07:00:00Z"),
            Instant.parse("2026-11-02T08:00:00Z"),
            zone
        )

        assertEquals(LocalDateTime.parse("2026-11-01T00:00:00"), range.first)
        assertEquals(LocalDateTime.parse("2026-11-02T00:00:00"), range.second)
    }
}
