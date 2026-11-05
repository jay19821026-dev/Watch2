package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class WearInsightPacketPolicyTest {
    @Test
    fun absentOrInvalidSourceTimeIsUnknownInsteadOfReusingOlderMetadata() {
        assertEquals(0L, WearInsightPacketPolicy.sourceTimestamp(false, 1_000L))
        assertEquals(0L, WearInsightPacketPolicy.sourceTimestamp(true, 0L))
        assertEquals(1_000L, WearInsightPacketPolicy.sourceTimestamp(true, 1_000L))
    }

    @Test
    fun absentOrOutOfRangeCompositeScoresClearPreviouslyStoredValues() {
        assertEquals(-1, WearInsightPacketPolicy.scoreOrUnavailable(false, 82))
        assertEquals(-1, WearInsightPacketPolicy.scoreOrUnavailable(true, -1))
        assertEquals(-1, WearInsightPacketPolicy.scoreOrUnavailable(true, 101))
        assertEquals(0, WearInsightPacketPolicy.scoreOrUnavailable(true, 0))
        assertEquals(100, WearInsightPacketPolicy.scoreOrUnavailable(true, 100))
    }

    @Test
    fun disabledCompositeScoreIsNeverShownEvenWhenOldCacheContainsValue() {
        assertEquals(null, WearInsightPacketPolicy.compositeScoreForDisplay(84))
        assertEquals(null, WearInsightPacketPolicy.compositeScoreForDisplay(-1))
    }
}
