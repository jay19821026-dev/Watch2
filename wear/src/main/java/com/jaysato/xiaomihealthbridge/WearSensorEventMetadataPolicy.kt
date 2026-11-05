package com.jaysato.xiaomihealthbridge

internal object WearSensorEventMetadataPolicy {
    fun sourceTimestampPresent(timestampNanos: Long): Boolean = timestampNanos > 0L

    fun hasTrustedMonotonicSensorEventTimes(timestampsNanos: LongArray, validity: IntArray = intArrayOf()): Boolean {
        if (timestampsNanos.isEmpty() || validity.isNotEmpty() && validity.size != timestampsNanos.size) return false
        if (validity.isNotEmpty() && validity.any { it != 1 }) return false
        if (timestampsNanos.any { it <= 0L }) return false
        for (index in 1 until timestampsNanos.size) {
            if (timestampsNanos[index] <= timestampsNanos[index - 1]) return false
        }
        return true
    }

    fun shareElapsedRealtimeSensorClock(first: LongArray, firstValidity: IntArray, second: LongArray): Boolean =
        hasTrustedMonotonicSensorEventTimes(first, firstValidity) && hasTrustedMonotonicSensorEventTimes(second)

    fun rawValueFields(kind: RawVitalsProbe.Kind, rows: List<String>, status: String): Map<String, String> =
        if (supports(kind)) {
            mapOf(
                "event_raw_values_rows" to rows.joinToString(";"),
                "event_raw_values_status" to status
            )
        } else emptyMap()

    fun supports(kind: RawVitalsProbe.Kind): Boolean = kind in setOf(
        RawVitalsProbe.Kind.ECG,
        RawVitalsProbe.Kind.PPG,
        RawVitalsProbe.Kind.MAX_HEART_RATE,
        RawVitalsProbe.Kind.LIFE_Q_OTT,
        RawVitalsProbe.Kind.BIOZ,
        RawVitalsProbe.Kind.BIOZ_RESPIRATION,
        RawVitalsProbe.Kind.SPO2,
        RawVitalsProbe.Kind.MI_SPO2,
        RawVitalsProbe.Kind.BODY_TEMPERATURE,
        RawVitalsProbe.Kind.THERMOMETER
    )
}
