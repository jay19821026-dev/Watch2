package com.jaysato.xiaomihealthbridge

data class SignalTimelineEvidence(
    val source: String,
    val estimated: Boolean
)

object SignalTimelineLabel {
    fun evidence(measuredAtMillis: Long, timestampSource: String?): SignalTimelineEvidence {
        val source = timestampSource.orEmpty().trim()
        val trustedSensorTime = measuredAtMillis > 0L &&
            HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(source)
        val trustedMeasurementTime = measuredAtMillis > 0L &&
            source == HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        return when {
            trustedSensorTime -> SignalTimelineEvidence("SENSOR_TIME", false)
            trustedMeasurementTime -> SignalTimelineEvidence("MEASUREMENT_TIME", false)
            source.uppercase() in setOf("ESTIMATED", "ESTIMATED_TIMELINE") ->
                SignalTimelineEvidence("ESTIMATED_TIMELINE", true)
            else -> SignalTimelineEvidence("NORMALIZED_INDEX", false)
        }
    }

    fun describe(
        sampleCount: Int,
        reportedSampleRateHz: Float,
        timestampSource: String,
        measuredAtMillis: Long = 0L
    ): String {
        val count = sampleCount.coerceAtLeast(0)
        val source = timestampSource.ifBlank { "UNKNOWN" }
        val timeline = evidence(measuredAtMillis, source)
        val rateLabel = if (reportedSampleRateHz.isFinite() && reportedSampleRateHz > 0f) {
            "HAL 回報取樣率 ${"%.1f".format(reportedSampleRateHz)} Hz（不代表時間軸已驗證）"
        } else {
            "取樣率未知"
        }
        return "$count 點 · ${timeline.source} · $rateLabel · 時戳來源 $source · estimated_timeline=${timeline.estimated}"
    }
}
