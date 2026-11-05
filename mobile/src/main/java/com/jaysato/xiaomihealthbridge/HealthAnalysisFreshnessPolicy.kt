package com.jaysato.xiaomihealthbridge

internal object HealthAnalysisFreshnessPolicy {
    const val RECENT_SENSOR_INPUT_MILLIS = 10 * 60 * 1000L
    const val HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE = "health_services_datapoint_epoch"

    fun resolveStoredMeasurementTime(canonicalTime: Long, legacyAliasTime: Long = 0L): Long =
        canonicalTime.takeIf { it > 0L } ?: legacyAliasTime.takeIf { it > 0L } ?: 0L

    fun isFresh(measuredAt: Long, now: Long, maxAgeMillis: Long = RECENT_SENSOR_INPUT_MILLIS): Boolean =
        measuredAt > 0L && now >= measuredAt && maxAgeMillis >= 0L && now - measuredAt <= maxAgeMillis

    fun hasTrustedSensorTimestamp(source: String?): Boolean =
        source == "android_sensor_event_elapsed_realtime"

    fun hasTrustedMeasurementTimestamp(source: String?): Boolean =
        hasTrustedSensorTimestamp(source) || source == HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE

    fun isFreshSensor(
        measuredAt: Long,
        timestampSource: String?,
        now: Long,
        maxAgeMillis: Long = RECENT_SENSOR_INPUT_MILLIS
    ): Boolean = hasTrustedSensorTimestamp(timestampSource) && isFresh(measuredAt, now, maxAgeMillis)

    fun isFreshMeasurement(
        measuredAt: Long,
        timestampSource: String?,
        now: Long,
        maxAgeMillis: Long = RECENT_SENSOR_INPUT_MILLIS
    ): Boolean = hasTrustedMeasurementTimestamp(timestampSource) && isFresh(measuredAt, now, maxAgeMillis)
}
