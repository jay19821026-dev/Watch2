package com.jaysato.xiaomihealthbridge

import java.time.Instant

internal object HeartRateHomeEvidencePolicy {
    fun describe(sourceKind: String, measuredAt: Long, timestampSource: String?): String {
        val sourceLabel = when (sourceKind) {
            "health_services" -> "Health Services"
            "raw_sensor" -> "手錶原始心率通道"
            else -> "來源未確認"
        }
        val trusted = when (sourceKind) {
            "health_services" -> HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource)
            "raw_sensor" -> HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(timestampSource)
            else -> false
        }
        val timeLabel = when {
            measuredAt <= 0L -> "時間未知"
            trusted -> "量測時間 ${runCatching { Instant.ofEpochMilli(measuredAt).toString() }.getOrDefault("無效時間")}"
            else -> "時間欄位未確認 $measuredAt"
        }
        return "來源 $sourceLabel · $timeLabel · timestamp_source=${timestampSource?.ifBlank { "UNKNOWN" } ?: "UNKNOWN"}"
    }
}
