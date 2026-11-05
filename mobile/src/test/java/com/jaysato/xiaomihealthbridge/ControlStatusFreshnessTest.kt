package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlStatusFreshnessTest {
    @Test
    fun marksOnlyRecentNonFutureReportsFresh() {
        val now = 10_000_000L
        val window = 30 * 60 * 1000L
        assertFalse(ControlStatusFreshness.isStale(now, now, window))
        assertFalse(ControlStatusFreshness.isStale(now - window, now, window))
        assertTrue(ControlStatusFreshness.isStale(now - window - 1L, now, window))
        assertTrue(ControlStatusFreshness.isStale(0L, now, window))
        assertTrue(ControlStatusFreshness.isStale(now + 1L, now, window))
    }
}
