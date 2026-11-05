package com.jaysato.xiaomihealthbridge

internal object FeatureSnapshotFieldPolicy {
    data class Measurement(val value: Float, val timestamp: Long, val timestampSource: String)

    fun acceptedMeasurement(
        field: String,
        value: Double,
        timestamp: Long,
        timestampSource: String?,
        previousTimestamp: Long,
        previousSensorTimestamp: Long,
        now: Long
    ): Measurement? {
        if (field !in setOf("heading", "altitude", "light", "temperature", "whs_heart_rate")) return null
        if (!value.isFinite() || !value.toFloat().isFinite()) return null
        if (field == "whs_heart_rate" && value !in 1.0..300.0) return null
        if (!FeatureSnapshotOrdering.shouldAccept(timestamp, previousTimestamp, previousSensorTimestamp, now)) return null
        return Measurement(
            value.toFloat(),
            timestamp,
            timestampSource?.takeIf { it.isNotBlank() } ?: "watch_sample_timestamp_source_unreported"
        )
    }
}
