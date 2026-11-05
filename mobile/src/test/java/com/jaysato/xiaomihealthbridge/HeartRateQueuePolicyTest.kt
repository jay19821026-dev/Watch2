package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateQueuePolicyTest {
    @Test
    fun fullQueueFlushesBeforeAppendAndNeverFitsByDroppingOldSamples() {
        assertEquals(false, HeartRateQueuePolicy.shouldFlushBeforeAppend(1_999, 2_000))
        assertEquals(true, HeartRateQueuePolicy.shouldFlushBeforeAppend(2_000, 2_000))
        assertEquals(true, HeartRateQueuePolicy.fitsCapacity(2_000, 2_000))
        assertEquals(false, HeartRateQueuePolicy.fitsCapacity(2_001, 2_000))
    }

    @Test
    fun compactsSamplesByMinuteAndKeepsTheLatestMeasurementTime() {
        val result = HeartRateQueuePolicy.compact(
            mapOf(60_001L to 60L, 60_020L to 80L, 120_005L to 70L, 180_000L to 0L)
        )

        assertEquals(mapOf(60_020L to 70L, 120_005L to 70L), result)
    }

    @Test
    fun retriesUseSameRecordIdAndOlderReplayCannotReplaceNewerMinuteValue() {
        val earlier = 1_700_000_000_010L
        val later = earlier + 20_000L

        assertEquals(
            HeartRateQueuePolicy.clientRecordId(earlier),
            HeartRateQueuePolicy.clientRecordId(later)
        )
        assertEquals(earlier, HeartRateQueuePolicy.clientRecordVersion(earlier))
        assertEquals(later, HeartRateQueuePolicy.clientRecordVersion(later))
    }
}
