package com.jaysato.xiaomihealthbridge

internal object HeartRatePresentationPolicy {
    const val WEAR_STATE_FRESHNESS_MILLIS = 5 * 60 * 1000L
    const val HOME_HISTORY_WINDOW_MILLIS = 24 * 60 * 60 * 1000L

    fun homeTimestampLabel(measuredAt: Long, now: Long): String {
        val measuredDate = java.time.Instant.ofEpochMilli(measuredAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val today = java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val format = if (measuredDate == today) {
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        } else {
            java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT,
                java.text.DateFormat.SHORT
            )
        }
        return format.format(java.util.Date(measuredAt))
    }

    fun visibleHealthServicesBpm(
        bpm: Float,
        measuredAt: Long,
        timestampSource: String?,
        now: Long,
        freshnessMillis: Long
    ): Float? {
        val age = now - measuredAt
        return bpm.takeIf {
            it.isFinite() && it in 30f..220f &&
                HealthAnalysisFreshnessPolicy.isFreshMeasurement(measuredAt, timestampSource, now, freshnessMillis)
        }
    }

    fun visibleRawSensorBpm(
        bpm: Float,
        status: String,
        measuredAt: Long,
        timestampSource: String?,
        now: Long
    ): Float? = bpm.takeIf {
        it.isFinite() && it in 30f..220f && status in setOf("has_data", "valid_signal") &&
            HealthAnalysisFreshnessPolicy.isFreshSensor(
                measuredAt, timestampSource, now, HOME_HISTORY_WINDOW_MILLIS
            )
    }

    fun visibleDetailBpm(
        kind: String,
        bpm: Float,
        status: String,
        measuredAt: Long,
        timestampSource: String?,
        now: Long
    ): Float? {
        val validStatus = status in setOf("has_data", "valid_signal", "worn", "derived_estimate")
        val fresh = if (kind == "whs_heart_rate") {
            HealthAnalysisFreshnessPolicy.isFreshMeasurement(
                measuredAt, timestampSource, now, HOME_HISTORY_WINDOW_MILLIS
            )
        } else {
            HealthAnalysisFreshnessPolicy.isFreshSensor(
                measuredAt, timestampSource, now, HOME_HISTORY_WINDOW_MILLIS
            )
        }
        return bpm.takeIf { it.isFinite() && it in 30f..220f && validStatus && fresh }
    }

    fun detailTimeDescription(kind: String, measuredAt: Long, timestampSource: String?, now: Long): String {
        val trusted = if (kind == "whs_heart_rate") {
            HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource)
        } else {
            HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(timestampSource)
        }
        val age = now - measuredAt
        return when {
            measuredAt <= 0L || !trusted -> "量測時間未確認；時間來源 ${timestampSource ?: "UNKNOWN"}"
            age < 0L -> "量測時間戳異常；時間來源 $timestampSource"
            age > WEAR_STATE_FRESHNESS_MILLIS ->
                "歷史讀值（約 ${age / 60_000L} 分鐘前）· 時間來源 $timestampSource"
            else -> "近期讀值 · 時間來源 $timestampSource"
        }
    }

    fun detailValueLabel(bpm: Float?): String = bpm
        ?.takeIf { it.isFinite() && it in 30f..220f }
        ?.let { "讀值 ${"%.0f".format(it)} bpm" }
        ?: "讀值無法判定（數值／來源時間／新鮮度未通過）"

    fun wearContext(
        wornKnown: Boolean,
        worn: Boolean,
        measuredAt: Long,
        now: Long,
        timestampSource: String?
    ): String {
        val wearAge = now - measuredAt
        return when {
            !wornKnown || measuredAt <= 0L || wearAge !in 0..WEAR_STATE_FRESHNESS_MILLIS ||
                !HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(timestampSource) -> "佩戴狀態未知"
            !worn -> "離腕訊號不一致，請確認佩戴"
            else -> "離腕感測器回報佩戴"
        }
    }
}
