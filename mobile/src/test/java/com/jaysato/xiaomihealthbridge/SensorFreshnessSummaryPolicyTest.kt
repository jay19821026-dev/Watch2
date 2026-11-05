package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorFreshnessSummaryPolicyTest {
    @Test
    fun emptyOrStaleSummaryRequiresAttention() {
        assertTrue(SensorFreshnessSummaryPolicy.requiresAttention(0, 0, 1L))
        assertTrue(SensorFreshnessSummaryPolicy.requiresAttention(1, 1, 1L))
        assertTrue(SensorFreshnessSummaryPolicy.requiresAttention(1, 0, 0L))
    }

    @Test
    fun recentNonStaleReadingsDoNotRequireAttention() {
        assertFalse(SensorFreshnessSummaryPolicy.requiresAttention(2, 0, 1L))
    }

    @Test
    fun readingsWithoutTimestampsRequireAttentionEvenWhenOtherReadingsAreFresh() {
        assertTrue(SensorFreshnessSummaryPolicy.requiresAttention(2, 0, 1L, timestampUnknownCount = 1))
    }

    @Test
    fun futureTimestampReadingsRequireAttention() {
        assertTrue(SensorFreshnessSummaryPolicy.requiresAttention(2, 0, 1L, futureTimestampCount = 1))
    }
}
