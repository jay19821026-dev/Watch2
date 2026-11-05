package com.jaysato.xiaomihealthbridge

import kotlin.math.abs

internal data class SensorEventEpochTime(val epochMillis: Long, val source: String)

internal object SensorEventEpochTimeResolver {
    fun resolve(
        eventTimestampNanos: Long,
        capturedElapsedRealtimeNanos: Long,
        epochOffsetMillis: Long,
        callbackWallClockMillis: Long
    ): SensorEventEpochTime {
        if (capturedElapsedRealtimeNanos <= 0L || epochOffsetMillis < 0L) {
            return SensorEventEpochTime(0L, "sensor_clock_basis_invalid")
        }
        val observedOffsetMillis = callbackWallClockMillis - capturedElapsedRealtimeNanos / NANOS_PER_MILLI
        if (abs(observedOffsetMillis - epochOffsetMillis) > MAX_WALL_CLOCK_OFFSET_DRIFT_MILLIS) {
            return SensorEventEpochTime(0L, "epoch_offset_drift_exceeded")
        }
        if (eventTimestampNanos <= 0L) {
            return SensorEventEpochTime(0L, "sensor_event_timestamp_missing")
        }
        if (capturedElapsedRealtimeNanos < eventTimestampNanos) {
            return SensorEventEpochTime(0L, "sensor_event_timestamp_ahead_of_capture")
        }
        return SensorEventEpochTime(
            eventTimestampNanos / NANOS_PER_MILLI + epochOffsetMillis,
            "android_sensor_event_elapsed_realtime"
        )
    }

    private const val NANOS_PER_MILLI = 1_000_000L
    private const val MAX_WALL_CLOCK_OFFSET_DRIFT_MILLIS = 1_000L
}
