package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorSnapshotTimestampTest {
    @Test
    fun usesSensorSampleTimeInsteadOfRecentPublicationTime() {
        assertEquals(1_000L, SensorSnapshotTimestamp.sampleTime(1_000L, true))
    }

    @Test
    fun zeroSampleTimeRemainsUnknownInsteadOfLookingFresh() {
        assertEquals(0L, SensorSnapshotTimestamp.sampleTime(0L, true))
    }

    @Test
    fun legacyPayloadWithoutWatchTimeRemainsUnknownDespiteRecentPublication() {
        val sampleTime = SensorSnapshotTimestamp.sampleTime(0L, false)
        assertEquals(0L, sampleTime)
        val state = SensorDisplayState.from(
            samples = 4,
            status = "has_data",
            measuredAt = sampleTime,
            now = 10_000L,
            freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS
        )
        assertEquals(SensorDisplayState.TIMESTAMP_UNKNOWN, state)
        assertEquals(false, SensorSuiteExportPolicy.countsAsFreshRawData(true, state))
    }

    @Test
    fun rejectsRegressingSampleTimeOnlyWithinTheSameSession() {
        assertEquals(
            "older_sample_timestamp",
            SensorSnapshotTimestamp.rejectionReason(4_000L, 5_000L, 10L, 10L, true)
        )
        assertNull(SensorSnapshotTimestamp.rejectionReason(4_000L, 5_000L, 11L, 10L, true))
        assertNull(SensorSnapshotTimestamp.rejectionReason(4_000L, 5_000L, 10L, 10L, false))
    }
}
