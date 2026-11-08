package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            trusted -> "量測時間 ${runCatching { Instant.ofEpochMilli(measuredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")) }.getOrDefault("無效時間")}"
            else -> "量測時間未確認"
        }
        return "來源 $sourceLabel · $timeLabel"
    }
}
