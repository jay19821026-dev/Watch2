package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearDataSyncPolicyTest {
    @Test
    fun workoutHeartRateMustBeInSessionAndNewerThanLastAcceptedSample() {
        assertFalse(WearDataSyncPolicy.isWorkoutHeartRateSampleEligible(false, 10_000L, 0L, 11_000L))
        assertFalse(WearDataSyncPolicy.isWorkoutHeartRateSampleEligible(true, 10_000L, 0L, 9_999L))
        assertFalse(WearDataSyncPolicy.isWorkoutHeartRateSampleEligible(true, 10_000L, 10_000L, 10_000L))
        assertTrue(WearDataSyncPolicy.isWorkoutHeartRateSampleEligible(true, 10_000L, 9_000L, 10_000L))
        assertFalse(WearDataSyncPolicy.isWorkoutHeartRateSampleEligible(true, 10_000L, 11_000L, 10_500L))
    }

    @Test
    fun heartRateHistoryRequiresNewerMeasurementTimestampEvenWhenForced() {
        assertFalse(WearDataSyncPolicy.shouldSaveWorkoutHeartRate(5_000L, 5_000L, 0L, 60_000L, force = true))
        assertTrue(WearDataSyncPolicy.shouldSaveWorkoutHeartRate(5_000L, 6_000L, 0L, 1_000L, force = true))
    }

    @Test
    fun workoutHeartRateHistoryRespectsThirtySecondCadence() {
        assertFalse(WearDataSyncPolicy.shouldSaveWorkoutHeartRate(5_000L, 6_000L, 1_000L, 30_999L))
        assertTrue(WearDataSyncPolicy.shouldSaveWorkoutHeartRate(5_000L, 6_000L, 1_000L, 31_000L))
    }

    @Test
    fun standaloneSensorPreviewsAreDeferredButFinalResultsAreUrgent() {
        assertFalse(WearDataSyncPolicy.shouldMarkStandaloneSnapshotUrgent(isFinal = false))
        assertTrue(WearDataSyncPolicy.shouldMarkStandaloneSnapshotUrgent(isFinal = true))
    }

    @Test
    fun rateLimitsLivePreviewsToFiveSeconds() {
        assertTrue(WearDataSyncPolicy.shouldSendPreview(0L, 10_000L))
        assertFalse(WearDataSyncPolicy.shouldSendPreview(10_000L, 14_999L))
        assertTrue(WearDataSyncPolicy.shouldSendPreview(10_000L, 15_000L))
        assertFalse(WearDataSyncPolicy.shouldSendPreview(10_000L, 9_999L))
    }

    @Test
    fun onlyMarksFinalSnapshotsUrgentAtMostEveryFiveSeconds() {
        assertFalse(WearDataSyncPolicy.shouldMarkUrgent(false, 0L, 10_000L))
        assertTrue(WearDataSyncPolicy.shouldMarkUrgent(true, 0L, 10_000L))
        assertFalse(WearDataSyncPolicy.shouldMarkUrgent(true, 10_000L, 14_999L))
        assertTrue(WearDataSyncPolicy.shouldMarkUrgent(true, 10_000L, 15_000L))
        assertFalse(WearDataSyncPolicy.shouldMarkUrgent(true, 10_000L, 9_999L))
    }

    @Test
    fun batchesHealthServicesHeartRateSyncToThirtySecondsAndRejectsClockRollback() {
        assertTrue(WearDataSyncPolicy.shouldPublishHeartRate(0L, 1_000L))
        assertFalse(WearDataSyncPolicy.shouldPublishHeartRate(1_000L, 30_999L))
        assertTrue(WearDataSyncPolicy.shouldPublishHeartRate(1_000L, 31_000L))
        assertFalse(WearDataSyncPolicy.shouldPublishHeartRate(1_000L, 999L))
    }

    @Test
    fun onlyProgressStatusUsesDeferredDataLayerSync() {
        assertFalse(WearDataSyncPolicy.shouldMarkStatusUrgent(StatusSyncPriority.PROGRESS))
        assertTrue(WearDataSyncPolicy.shouldMarkStatusUrgent(StatusSyncPriority.USER_ACTION))
        assertTrue(WearDataSyncPolicy.shouldMarkStatusUrgent(StatusSyncPriority.TERMINAL))
    }
}
