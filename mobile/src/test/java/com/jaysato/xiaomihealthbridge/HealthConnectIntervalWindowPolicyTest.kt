package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HealthConnectIntervalWindowPolicyTest {
    private data class Interval(val id: String, val start: Instant, val end: Instant)

    private val windowStart = Instant.parse("2026-09-29T00:00:00Z")
    private val windowEnd = Instant.parse("2026-09-30T00:00:00Z")

    @Test
    fun retainsContainedIntervalsAndCountsOnlyOverlappingBoundaryCrossings() {
        val containedAtStart = Interval("start", windowStart, windowStart.plusSeconds(60))
        val containedAtEnd = Interval("end", windowEnd.minusSeconds(60), windowEnd)
        val crossesStart = Interval("cross-start", windowStart.minusSeconds(60), windowStart.plusSeconds(60))
        val crossesEnd = Interval("cross-end", windowEnd.minusSeconds(60), windowEnd.plusSeconds(60))
        val touchesStartOnly = Interval("touch-start", windowStart.minusSeconds(60), windowStart)
        val touchesEndOnly = Interval("touch-end", windowEnd, windowEnd.plusSeconds(60))

        val result = HealthConnectIntervalWindowPolicy.retainContained(
            listOf(containedAtStart, containedAtEnd, crossesStart, crossesEnd, touchesStartOnly, touchesEndOnly),
            windowStart,
            windowEnd,
            Interval::start,
            Interval::end
        )

        assertEquals(listOf(containedAtStart, containedAtEnd), result.retained)
        assertEquals(2, result.excludedOverlaps)
    }
}
