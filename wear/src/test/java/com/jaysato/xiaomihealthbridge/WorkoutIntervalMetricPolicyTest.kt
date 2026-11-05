package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutIntervalMetricPolicyTest {
    private val start = 1_000_000L
    private val now = 1_010_000L

    @Test
    fun acceptsPositiveMetricWithOriginalWorkoutInterval() {
        val result = WorkoutIntervalMetricPolicy.evaluate("steps", 12.0, 1_002_000L, 1_004_000L, start, now, null)
        assertTrue(result.accepted)
        assertEquals(null, result.rejection)
    }

    @Test
    fun rejectsZeroNegativeNonFiniteAndOutOfRangeValues() {
        assertReject("steps", 0.0, WorkoutIntervalMetricPolicy.Rejection.INVALID_VALUE)
        assertReject("distance", -1.0, WorkoutIntervalMetricPolicy.Rejection.INVALID_VALUE)
        assertReject("calories", Double.NaN, WorkoutIntervalMetricPolicy.Rejection.INVALID_VALUE)
        assertReject("steps", 100_001.0, WorkoutIntervalMetricPolicy.Rejection.OUT_OF_RANGE)
    }

    @Test
    fun rejectsInvalidOrOutOfSessionIntervals() {
        assertReject("steps", 1.0, WorkoutIntervalMetricPolicy.Rejection.INVALID_INTERVAL, 1_005_000L, 1_004_000L)
        assertReject("distance", 1.0, WorkoutIntervalMetricPolicy.Rejection.INVALID_INTERVAL, start - 1, now)
        assertReject("calories", 1.0, WorkoutIntervalMetricPolicy.Rejection.INVALID_INTERVAL, start, now + 60_001L)
    }

    @Test
    fun exactIntervalReplayIsDeduplicatedAndCursorRemainsBounded() {
        val previous = WorkoutIntervalMetricPolicy.Interval(1_002_000L, 1_004_000L)
        val result = WorkoutIntervalMetricPolicy.evaluate("steps", 12.0, previous.startMillis, previous.endMillis, start, now, previous)
        assertFalse(result.accepted)
        assertEquals(WorkoutIntervalMetricPolicy.Rejection.DUPLICATE, result.rejection)
    }

    @Test
    fun rejectsOverlappingAndOutOfOrderIntervalsButAllowsNextInterval() {
        val previous = WorkoutIntervalMetricPolicy.Interval(1_002_000L, 1_004_000L)
        assertEquals(
            WorkoutIntervalMetricPolicy.Rejection.OUT_OF_ORDER,
            WorkoutIntervalMetricPolicy.evaluate("distance", 2.0, 1_003_000L, 1_005_000L, start, now, previous).rejection
        )
        assertTrue(WorkoutIntervalMetricPolicy.evaluate("calories", 2.0, 1_004_000L, 1_006_000L, start, now, previous).accepted)
    }

    @Test
    fun rejectsUnsupportedMetric() {
        assertReject("heart_rate", 80.0, WorkoutIntervalMetricPolicy.Rejection.UNSUPPORTED_METRIC)
    }

    @Test
    fun workoutEndUpdateIsProcessedAfterActivityActiveFlagDrops() {
        assertTrue(WorkoutMetricDeliveryPolicy.shouldProcessMetrics(false, true, true))
        assertFalse(WorkoutMetricDeliveryPolicy.shouldProcessMetrics(false, true, false))
        assertFalse(WorkoutMetricDeliveryPolicy.shouldProcessMetrics(false, false, true))
        assertTrue(WorkoutMetricDeliveryPolicy.shouldProcessMetrics(true, false, false))
    }

    @Test
    fun deliveryRetriesAreFiniteAndUseStableIntervalIdentity() {
        assertEquals(500L, WorkoutMetricDeliveryPolicy.retryDelayAfterFailure(1))
        assertEquals(1_000L, WorkoutMetricDeliveryPolicy.retryDelayAfterFailure(2))
        assertEquals(2_000L, WorkoutMetricDeliveryPolicy.retryDelayAfterFailure(3))
        assertEquals(null, WorkoutMetricDeliveryPolicy.retryDelayAfterFailure(4))
        val path = WorkoutMetricDeliveryPolicy.intervalPath("steps", 1_002_000L, 1_004_000L)
        assertEquals(path, WorkoutMetricDeliveryPolicy.intervalPath("steps", 1_002_000L, 1_004_000L))
        assertEquals("/health/steps/1002000-1004000", path)
    }

    private fun assertReject(
        metric: String,
        value: Double,
        rejection: WorkoutIntervalMetricPolicy.Rejection,
        startMillis: Long = 1_002_000L,
        endMillis: Long = 1_004_000L
    ) {
        val result = WorkoutIntervalMetricPolicy.evaluate(metric, value, startMillis, endMillis, start, now, null)
        assertFalse(result.accepted)
        assertEquals(rejection, result.rejection)
    }
}
