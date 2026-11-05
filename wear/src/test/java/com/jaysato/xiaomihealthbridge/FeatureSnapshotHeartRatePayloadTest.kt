package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureSnapshotHeartRatePayloadTest {
    @Test
    fun publishesBpmTimestampAndSourceAsOneConsistentSnapshotPayload() {
        val timestamp = 1_791_234_567_890L

        val payload = FeatureSnapshotHeartRatePayload.create(86.5, timestamp)
            ?: error("Expected valid Health Services event payload")

        assertEquals(86.5, payload.bpm, 0.0)
        assertEquals(timestamp, payload.eventTimestampMillis)
        assertEquals(HealthServicesHeartRatePayload.TIMESTAMP_SOURCE, payload.timestampSource)
    }

    @Test
    fun missingOrInvalidEventTimestampRejectsHeartRatePayload() {
        assertNull(FeatureSnapshotHeartRatePayload.create(86.5, 0L))
        assertNull(FeatureSnapshotHeartRatePayload.create(86.5, -1L))
    }

    @Test
    fun acceptsInclusiveProducerBpmBoundaries() {
        val minimum = FeatureSnapshotHeartRatePayload.create(1.0, 1L)
            ?: error("Expected minimum supported BPM to be accepted")
        val maximum = FeatureSnapshotHeartRatePayload.create(300.0, 1L)
            ?: error("Expected maximum supported BPM to be accepted")

        assertEquals(1.0, minimum.bpm, 0.0)
        assertEquals(300.0, maximum.bpm, 0.0)
    }

    @Test
    fun rejectsZeroNegativeOutOfRangeAndNonFiniteBpm() {
        assertNull(FeatureSnapshotHeartRatePayload.create(0.0, 1L))
        assertNull(FeatureSnapshotHeartRatePayload.create(-1.0, 1L))
        assertNull(FeatureSnapshotHeartRatePayload.create(300.0001, 1L))
        assertNull(FeatureSnapshotHeartRatePayload.create(Double.NaN, 1L))
        assertNull(FeatureSnapshotHeartRatePayload.create(Double.POSITIVE_INFINITY, 1L))
        assertNull(FeatureSnapshotHeartRatePayload.create(Double.NEGATIVE_INFINITY, 1L))
    }
}
