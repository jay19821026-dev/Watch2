package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSnapshotOrderingTest {
    @Test
    fun sameSensorTimestampCannotBypassIncomingValueOrTimeValidation() {
        val now = 1_000_000L
        listOf(Double.NaN, 0.0, 221.0).forEach { value ->
            assertNull(FeatureSnapshotOrdering.canonicalHealthServicesHeartRateValue(
                now - 1, 0L, now - 1, value, Double.NaN, 83.0, now
            ))
        }
        listOf(0L, now + 300_001L).forEach { timestamp ->
            assertNull(FeatureSnapshotOrdering.canonicalHealthServicesHeartRateValue(
                timestamp, 0L, timestamp, 83.0, Double.NaN, 83.0, now
            ))
        }
        assertNull(FeatureSnapshotOrdering.canonicalHealthServicesHeartRateValue(
            now - 2, now - 1, now - 2, 83.0, 90.0, 83.0, now
        ))
    }

    @Test
    fun wearStateRequiresARecentTrustedSensorTimestamp() {
        val now = 100_000L
        assertTrue(
            FeatureSnapshotOrdering.isFreshWearState(
                true, true, now - 1_000L, "android_sensor_event_elapsed_realtime", now
            )
        )
        assertFalse(
            FeatureSnapshotOrdering.isFreshWearState(
                true, true, now - 1_000L, "watch_sample_timestamp_source_unreported", now
            )
        )
        assertFalse(
            FeatureSnapshotOrdering.isFreshWearState(
                true, true, now - 5 * 60 * 1000L - 1L, "android_sensor_event_elapsed_realtime", now
            )
        )
        assertFalse(
            FeatureSnapshotOrdering.isFreshWearState(
                true, true, now + 1L, "android_sensor_event_elapsed_realtime", now
            )
        )
        assertFalse(
            FeatureSnapshotOrdering.isFreshWearState(
                false, true, now, "android_sensor_event_elapsed_realtime", now
            )
        )
    }

    @Test
    fun classifiesRejectedHeartRateTimestampsWithoutChangingAcceptance() {
        val now = 1_000_000L

        assertEquals("same_timestamp", FeatureSnapshotOrdering.rejectionReason(now, now, now))
        assertEquals("older_timestamp", FeatureSnapshotOrdering.rejectionReason(now - 1, now, now))
        assertEquals("future_timestamp", FeatureSnapshotOrdering.rejectionReason(now + 301_001, now, now))
        assertEquals("invalid_timestamp", FeatureSnapshotOrdering.rejectionReason(0, 0, now))
        assertNull(FeatureSnapshotOrdering.rejectionReason(now + 1, now, now))
    }

    @Test
    fun wearReportRemainsIndependentFromHealthServicesHeartRate() {
        assertNull(FeatureSnapshotOrdering.currentWearState(false, true))
        assertNull(FeatureSnapshotOrdering.currentWearState(false, false))
        assertEquals(false, FeatureSnapshotOrdering.currentWearState(true, false))
        assertEquals(true, FeatureSnapshotOrdering.currentWearState(true, true))
    }

    @Test
    fun wearStateTransitionFromWornThroughUnknownToOffWristIsNotMisclassified() {
        assertEquals(true, FeatureSnapshotOrdering.currentWearState(true, true))
        assertNull(FeatureSnapshotOrdering.currentWearState(false, false))
        assertEquals(false, FeatureSnapshotOrdering.currentWearState(true, false))
    }

    @Test
    fun acceptsFreshFieldEvenWhenSnapshotTimestampIsOlderThanAnotherMetric() {
        val now = 10_000L
        assertTrue(FeatureSnapshotOrdering.shouldAccept(now - 1_000L, now - 2_000L, now))
    }

    @Test
    fun rejectsOlderFieldAndMissingOrImplausibleFutureTimestamp() {
        val now = 10_000L
        assertFalse(FeatureSnapshotOrdering.shouldAccept(now - 2_000L, now - 1_000L, now))
        assertFalse(FeatureSnapshotOrdering.shouldAccept(now - 1_000L, now - 1_000L, now))
        assertFalse(FeatureSnapshotOrdering.shouldAccept(0L, 0L, now))
        assertFalse(FeatureSnapshotOrdering.shouldAccept(now + 300_001L, 0L, now))
    }

    @Test
    fun heartRateSnapshotsMustBeNewerThanBothFeatureAndSensorRows() {
        val now = 10_000L
        assertTrue(FeatureSnapshotOrdering.shouldAccept(now - 1_000L, now - 2_000L, now - 3_000L, now))
        assertFalse(FeatureSnapshotOrdering.shouldAccept(now - 1_000L, now - 1_000L, now - 3_000L, now))
        assertFalse(FeatureSnapshotOrdering.shouldAccept(now - 1_000L, now - 3_000L, now - 1_000L, now))
        assertFalse(FeatureSnapshotOrdering.shouldAccept(now - 2_000L, now - 3_000L, now - 1_000L, now))
    }

    @Test
    fun sameTimestampHeartRateKeepsTheFirstFeatureValue() {
        val now = 10_000L
        assertEquals(
            99.0,
            FeatureSnapshotOrdering.canonicalHeartRateValue(now - 1_000L, now - 1_000L, now - 2_000L, 83.0, 99.0, now)!!,
            0.0
        )
        assertEquals(
            83.0,
            FeatureSnapshotOrdering.canonicalHeartRateValue(now - 1_000L, now - 2_000L, now - 1_000L, 83.0, 99.0, now)!!,
            0.0
        )
        assertNull(FeatureSnapshotOrdering.canonicalHeartRateValue(now - 2_000L, now - 1_000L, now - 3_000L, 83.0, 99.0, now))
    }

    @Test
    fun duplicateHealthServicesHeartRateKeepsCommittedSensorValue() {
        val now = 10_000L
        assertEquals(
            83.0,
            FeatureSnapshotOrdering.canonicalHealthServicesHeartRateValue(
                incomingTimestamp = now - 1_000L,
                previousFeatureTimestamp = now - 1_000L,
                previousSensorTimestamp = now - 1_000L,
                incomingBpm = 91.0,
                previousFeatureBpm = 83.0,
                previousSensorBpm = 83.0,
                now = now
            )!!,
            0.0
        )
        assertEquals(
            83.0,
            FeatureSnapshotOrdering.canonicalHealthServicesHeartRateValue(
                incomingTimestamp = now - 1_000L,
                previousFeatureTimestamp = now - 1_000L,
                previousSensorTimestamp = now - 1_000L,
                incomingBpm = 91.0,
                previousFeatureBpm = 91.0,
                previousSensorBpm = 83.0,
                now = now
            )!!,
            0.0
        )
    }
}
