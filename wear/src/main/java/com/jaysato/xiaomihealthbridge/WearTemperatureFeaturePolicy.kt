package com.jaysato.xiaomihealthbridge

internal object WearTemperatureFeaturePolicy {
    data class Aggregation(val method: String, val sampleCount: Int)

    private const val MAX_FUTURE_SKEW_MILLIS = 5 * 60 * 1000L

    fun probeAggregation(usedFiniteMean: Boolean, finiteSampleCount: Int): Aggregation {
        val count = finiteSampleCount.coerceAtLeast(0)
        return when {
            usedFiniteMean && count > 1 -> Aggregation("probe_finite_sample_mean", count)
            usedFiniteMean && count == 1 -> Aggregation("probe_single_finite_value", 1)
            else -> Aggregation("probe_latest_event_value", 1)
        }
    }

    fun directCaptureAggregation(): Aggregation = Aggregation("single_callback_raw_value", 1)

    fun shouldAccept(currentTimestamp: Long, candidateTimestamp: Long, now: Long, timestampSource: String): Boolean =
        timestampSource == "android_sensor_event_elapsed_realtime" &&
        candidateTimestamp > currentTimestamp &&
            candidateTimestamp > 0L &&
            candidateTimestamp <= now + MAX_FUTURE_SKEW_MILLIS
}
