package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorFreshnessSummaryPolicyTest {
    @Test
    fun scanCardExplainsStatusWithoutChannelOrTimestampCounts() {
        assertEquals("手錶正在掃描，完成後可在下方查看各項結果。",
            SensorFreshnessSummaryPolicy.displaySummary("running", 1L, 0, 0))
        assertEquals("最近掃描已有資料；請在下方逐項查看讀值與說明。點此可重新掃描。",
            SensorFreshnessSummaryPolicy.displaySummary("completed", 1L, 2, 4))
        assertEquals("這次沒有取得量測資料；確認佩戴後點此重新掃描。",
            SensorFreshnessSummaryPolicy.displaySummary("completed", 1L, 0, 0))
    }

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
