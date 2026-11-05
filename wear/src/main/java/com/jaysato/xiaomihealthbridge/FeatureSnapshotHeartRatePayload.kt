package com.jaysato.xiaomihealthbridge

internal data class FeatureSnapshotHeartRatePayload(
    val bpm: Double,
    val eventTimestampMillis: Long,
    val timestampSource: String
) {
    companion object {
        fun create(bpm: Double, eventTimestampMillis: Long): FeatureSnapshotHeartRatePayload? {
            if (!bpm.isFinite() || bpm !in MIN_VALID_BPM..MAX_VALID_BPM || eventTimestampMillis <= 0L) return null
            return FeatureSnapshotHeartRatePayload(
                bpm = bpm,
                eventTimestampMillis = eventTimestampMillis,
                timestampSource = HealthServicesHeartRatePayload.TIMESTAMP_SOURCE
            )
        }

        private const val MIN_VALID_BPM = 1.0
        private const val MAX_VALID_BPM = 300.0
    }
}
