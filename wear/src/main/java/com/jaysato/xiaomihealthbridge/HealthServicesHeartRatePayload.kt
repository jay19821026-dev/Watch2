package com.jaysato.xiaomihealthbridge

internal data class HealthServicesHeartRatePayload(
    val bpm: Long,
    val eventTimestampMillis: Long,
    val timestampSource: String
) {
    companion object {
        const val TIMESTAMP_SOURCE = "health_services_datapoint_epoch"

        fun create(bpm: Long, eventTimestampMillis: Long): HealthServicesHeartRatePayload? =
            if (bpm in MIN_VALID_BPM..MAX_VALID_BPM && eventTimestampMillis > 0L) {
                HealthServicesHeartRatePayload(bpm, eventTimestampMillis, TIMESTAMP_SOURCE)
            } else {
                null
            }

        private const val MIN_VALID_BPM = 1L
        private const val MAX_VALID_BPM = 300L
    }
}
