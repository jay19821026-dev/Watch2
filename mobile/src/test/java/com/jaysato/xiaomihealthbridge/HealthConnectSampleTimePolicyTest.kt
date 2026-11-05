package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HealthConnectSampleTimePolicyTest {
    @Test
    fun sampleRangeIsInclusiveAtStartAndExclusiveAtEnd() {
        val start = Instant.parse("2026-09-29T00:00:00Z")
        val end = Instant.parse("2026-09-30T00:00:00Z")

        assertTrue(HealthConnectSampleTimePolicy.isWithinHalfOpenRange(start, start, end))
        assertTrue(HealthConnectSampleTimePolicy.isWithinHalfOpenRange(end.minusNanos(1), start, end))
        assertFalse(HealthConnectSampleTimePolicy.isWithinHalfOpenRange(end, start, end))
        assertFalse(HealthConnectSampleTimePolicy.isWithinHalfOpenRange(start.minusNanos(1), start, end))
    }
}
