package com.jaysato.xiaomihealthbridge

internal enum class SensorDisplayState(val label: String, val exportValue: String) {
    NOT_MEASURED("這次沒有量到", "not_measured"),
    CONDITION_NOT_MET("這次尚未完成量測", "condition_not_met"),
    QUALITY_REJECTED("訊號不清楚，請重測", "rejected_quality"),
    CALIBRATION_MISSING("已收到訊號，尚無可用讀值", "calibration_missing"),
    STALE("這是較早的紀錄", "stale"),
    FUTURE_TIMESTAMP("量測時間有誤", "future_timestamp"),
    TIMESTAMP_UNKNOWN("量測時間未確認", "timestamp_unknown"),
    RAW_ONLY("已收到訊號，尚無可用讀值", "raw_or_research_data_only"),
    SIGNAL_VALID_RESEARCH("訊號可供研究參考", "signal_quality_passed_research_only"),
    DERIVED_ESTIMATE("這是估計值", "derived_estimate_not_hardware_measurement"),
    AVAILABLE("已有可用讀值", "hardware_data_available_non_diagnostic");

    companion object {
        const val APP_DISPLAY_FRESHNESS_MILLIS = 30 * 60 * 1000L

        internal fun hasTrustedTimestamp(kind: String, timestampSource: String?): Boolean =
            if (kind == "whs_heart_rate") {
                HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource)
            } else {
                HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(timestampSource)
            }

        fun from(
            samples: Int,
            status: String,
            measuredAt: Long,
            now: Long,
            freshnessMs: Long,
            kind: String = "",
            timestampSource: String? = null
        ): SensorDisplayState = when {
            measuredAt > 0L && measuredAt > now -> FUTURE_TIMESTAMP
            status in setOf("invalid_optical", "invalid_sensor_values", "rejected_quality") && measuredAt > 0L && now - measuredAt > freshnessMs -> STALE
            status in setOf("invalid_optical", "invalid_sensor_values", "rejected_quality") -> QUALITY_REJECTED
            status in setOf("needs_contact", "needs_motion", "needs_oem_mode", "needs_oem", "skipped_charging", "skipped_off_wrist", "off_wrist") && measuredAt > 0L && now - measuredAt > freshnessMs -> STALE
            status in setOf("needs_contact", "needs_motion", "needs_oem_mode", "needs_oem", "skipped_charging", "skipped_off_wrist", "off_wrist") -> CONDITION_NOT_MET
            samples > 0 && kind.isNotBlank() && !hasTrustedTimestamp(kind, timestampSource) -> TIMESTAMP_UNKNOWN
            measuredAt > 0L && now - measuredAt > freshnessMs -> STALE
            samples <= 0 -> NOT_MEASURED
            measuredAt <= 0L -> TIMESTAMP_UNKNOWN
            status !in setOf("has_data", "worn", "off_wrist", "valid_signal", "research_signal_unscaled", "research_optical", "unscaled_optical", "uncalibrated", "needs_oem", "calibrated_external_reference", "derived_estimate") -> RAW_ONLY
            samples > 0 && kind in setOf("spo2_raw", "mi_spo2", "bioz", "bioz_respiration") -> CALIBRATION_MISSING
            status in setOf("needs_oem", "uncalibrated", "research_signal_unscaled", "research_optical", "unscaled_optical") -> CALIBRATION_MISSING
            status == "derived_estimate" -> DERIVED_ESTIMATE
            status == "valid_signal" -> SIGNAL_VALID_RESEARCH
            status in setOf("has_data", "worn") -> AVAILABLE
            else -> RAW_ONLY
        }
    }
}
