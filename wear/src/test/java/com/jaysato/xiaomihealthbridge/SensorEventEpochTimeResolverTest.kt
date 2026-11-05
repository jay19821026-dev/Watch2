package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorEventEpochTimeResolverTest {
    @Test
    fun negativeEventTimestampStaysMissingWithoutReceiptTimeFallback() {
        val result = SensorEventEpochTimeResolver.resolve(
            -1L, 5_000_000_000L, 1_700_000_000_000L, 1_700_000_005_000L
        )

        assertEquals(0L, result.epochMillis)
        assertEquals("sensor_event_timestamp_missing", result.source)
    }

    @Test
    fun acceptsEventTimestampEqualToCapture() {
        val result = SensorEventEpochTimeResolver.resolve(
            5_000_000_000L, 5_000_000_000L, 1_700_000_000_000L, 1_700_000_005_000L
        )

        assertEquals(1_700_000_005_000L, result.epochMillis)
        assertEquals("android_sensor_event_elapsed_realtime", result.source)
    }

    @Test
    fun rejectsEventTimestampOneNanosecondAheadOfCapture() {
        val result = SensorEventEpochTimeResolver.resolve(
            5_000_000_001L, 5_000_000_000L, 1_700_000_000_000L, 1_700_000_005_000L
        )

        assertEquals(0L, result.epochMillis)
        assertEquals("sensor_event_timestamp_ahead_of_capture", result.source)
    }

    @Test
    fun laterCaptureWithMatchingWallClockDoesNotMakePastEventAhead() {
        val atCallbackEntry = SensorEventEpochTimeResolver.resolve(
            4_000_000_000L, 5_000_000_000L, 1_700_000_000_000L, 1_700_000_005_000L
        )
        val afterCallbackDelay = SensorEventEpochTimeResolver.resolve(
            4_000_000_000L, 7_000_000_000L, 1_700_000_000_000L, 1_700_000_007_000L
        )

        assertEquals(1_700_000_004_000L, atCallbackEntry.epochMillis)
        assertEquals("android_sensor_event_elapsed_realtime", atCallbackEntry.source)
        assertEquals(atCallbackEntry, afterCallbackDelay)
    }

    @Test
    fun validInvalidValidResolverCallsRemainIndependent() {
        val firstValid = SensorEventEpochTimeResolver.resolve(
            4_000_000_000L, 5_000_000_000L, 1_700_000_000_000L, 1_700_000_005_000L
        )
        val invalid = SensorEventEpochTimeResolver.resolve(
            -1L, 6_000_000_000L, 1_700_000_000_000L, 1_700_000_006_000L
        )
        val nextValid = SensorEventEpochTimeResolver.resolve(
            6_000_000_000L, 7_000_000_000L, 1_700_000_000_000L, 1_700_000_007_000L
        )

        assertEquals(1_700_000_004_000L, firstValid.epochMillis)
        assertEquals("android_sensor_event_elapsed_realtime", firstValid.source)
        assertEquals(0L, invalid.epochMillis)
        assertEquals("sensor_event_timestamp_missing", invalid.source)
        assertEquals(1_700_000_006_000L, nextValid.epochMillis)
        assertEquals("android_sensor_event_elapsed_realtime", nextValid.source)
    }

    @Test
    fun convertsSensorEventTimestampFromElapsedRealtimeUsingCapturedEpochOffset() {
        val result = SensorEventEpochTimeResolver.resolve(
            eventTimestampNanos = 4_000_000_000L,
            capturedElapsedRealtimeNanos = 5_000_000_000L,
            epochOffsetMillis = 1_700_000_000_000L,
            callbackWallClockMillis = 1_700_000_005_000L
        )
        assertEquals(1_700_000_004_000L, result.epochMillis)
        assertEquals("android_sensor_event_elapsed_realtime", result.source)
    }

    @Test
    fun invalidOrFutureEventClockStaysUnknownInsteadOfUsingCallbackTime() {
        val missing = SensorEventEpochTimeResolver.resolve(0L, 5_000_000_000L, 1_700_000_000_000L, 1_700_000_005_000L)
        assertEquals(0L, missing.epochMillis)
        assertEquals("sensor_event_timestamp_missing", missing.source)

        val future = SensorEventEpochTimeResolver.resolve(6_000_000_000L, 5_000_000_000L, 1_700_000_000_000L, 1_700_000_005_000L)
        assertEquals(0L, future.epochMillis)
        assertEquals("sensor_event_timestamp_ahead_of_capture", future.source)
    }

    @Test
    fun withholdsEpochTimeWhenWallClockOffsetJumpsForwardOrBackward() {
        listOf(1_700_000_010_000L, 1_700_000_000_000L).forEach { callbackWallClockMillis ->
            val result = SensorEventEpochTimeResolver.resolve(
                eventTimestampNanos = 4_000_000_000L,
                capturedElapsedRealtimeNanos = 5_000_000_000L,
                epochOffsetMillis = 1_700_000_000_000L,
                callbackWallClockMillis = callbackWallClockMillis
            )

            assertEquals(0L, result.epochMillis)
            assertEquals("epoch_offset_drift_exceeded", result.source)
        }
    }

    @Test
    fun acceptsOffsetDriftAtTheOneSecondTolerance() {
        val result = SensorEventEpochTimeResolver.resolve(
            eventTimestampNanos = 4_000_000_000L,
            capturedElapsedRealtimeNanos = 5_000_000_000L,
            epochOffsetMillis = 1_700_000_000_000L,
            callbackWallClockMillis = 1_700_000_006_000L
        )

        assertEquals(1_700_000_004_000L, result.epochMillis)
        assertEquals("android_sensor_event_elapsed_realtime", result.source)
    }
}
