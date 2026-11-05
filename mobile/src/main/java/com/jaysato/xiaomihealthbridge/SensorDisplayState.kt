package com.jaysato.xiaomihealthbridge

internal enum class SensorDisplayState(val label: String, val exportValue: String) {
    NOT_MEASURED("未收到感測事件", "not_measured"),
    CONDITION_NOT_MET("量測條件未滿足", "condition_not_met"),
    QUALITY_REJECTED("已收到訊號，但品質未通過", "rejected_quality"),
    CALIBRATION_MISSING("已收到原始資料，但缺少裝置校準", "calibration_missing"),
    STALE("非近期掃描資料", "stale"),
    FUTURE_TIMESTAMP("時間戳位於未來，無法確認新鮮度", "future_timestamp"),
    TIMESTAMP_UNKNOWN("已有資料，但時間戳缺失或來源不可信", "timestamp_unknown"),
    RAW_ONLY("已收到原始資料（研究用途，非生理量測值）", "raw_or_research_data_only"),
    SIGNAL_VALID_RESEARCH("訊號品質通過（研究用途，非醫療校準）", "signal_quality_passed_research_only"),
    DERIVED_ESTIMATE("推估資料（非此感測器實測）", "derived_estimate_not_hardware_measurement"),
    AVAILABLE("已收到資料（非疾病診斷）", "hardware_data_available_non_diagnostic");

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
