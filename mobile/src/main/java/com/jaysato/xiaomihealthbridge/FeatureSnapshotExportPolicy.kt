package com.jaysato.xiaomihealthbridge

internal object FeatureSnapshotExportPolicy {
    private const val MAX_FUTURE_SKEW_MILLIS = 5 * 60 * 1000L

    fun freshnessState(
        timestampMillis: Long,
        timestampSource: String?,
        nowMillis: Long,
        maxAgeMillis: Long = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS
    ): String = when {
        timestampMillis <= 0L -> "timestamp_missing"
        timestampMillis > nowMillis -> "future_timestamp"
        !HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(timestampSource) -> "timestamp_source_untrusted"
        nowMillis - timestampMillis > maxAgeMillis -> "stale"
        else -> "fresh"
    }

    fun measurementFreshnessState(
        timestampMillis: Long,
        timestampSource: String?,
        nowMillis: Long,
        maxAgeMillis: Long = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS
    ): String = when {
        timestampMillis <= 0L -> "timestamp_missing"
        timestampMillis > nowMillis -> "future_timestamp"
        !HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource) -> "timestamp_source_untrusted"
        nowMillis - timestampMillis > maxAgeMillis -> "stale"
        else -> "fresh"
    }

    fun hasValidTimestamp(timestampMillis: Long, nowMillis: Long): Boolean =
        timestampMillis > 0L && timestampMillis <= nowMillis + MAX_FUTURE_SKEW_MILLIS
}
