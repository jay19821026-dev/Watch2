package com.jaysato.xiaomihealthbridge

internal object VitalsUnavailableReason {
    fun describe(
        status: String,
        rawSampleCount: Int,
        reason: String,
        measuredAt: Long,
        now: Long,
        freshnessMillis: Long = 24 * 60 * 60 * 1000L,
        rawEventCount: Int = 0
    ): String {
        val sampleCount = rawSampleCount.coerceAtLeast(0)
        val eventCount = rawEventCount.coerceAtLeast(0)
        val detail = reason.trim().takeIf(String::isNotEmpty)
        val receivedSamples = if (sampleCount > 0) "已收到 $sampleCount 筆原始樣本" else null
        val receivedEvents = if (eventCount > 0) "已收到 $eventCount 個原始事件，但沒有可解析樣本" else null
        val receivedRaw = receivedSamples ?: receivedEvents
        val base = when {
            status in setOf("needs_oem_mode", "needs_oem") -> "需手錶原廠量測模式，尚未取得此項資料"
            status in setOf("needs_contact", "off_wrist", "skipped_off_wrist") -> "手錶未確認穩定貼合，未採用此項資料"
            status == "skipped_charging" -> "手錶充電時略過此項量測"
            status == "needs_motion" -> "此感測器需走動或動作觸發"
            measuredAt > 0L && (measuredAt > now || now - measuredAt > freshnessMillis) -> "最近資料已過期"
            status in setOf("invalid_optical", "invalid_sensor_values", "rejected_quality") ->
                listOfNotNull(receivedRaw, "訊號品質或數值格式未通過").joinToString("；")
            status in setOf("research_signal_unscaled", "research_optical", "unscaled_optical", "uncalibrated", "calibration_missing") ->
                listOfNotNull(receivedRaw, "只有原始研究資料，缺少裝置單位／校準，不能輸出此生理數值").joinToString("；")
            status == "no_events" || status == "not_measured" -> if (sampleCount > 0) {
                "$receivedSamples；未解析成有效的此項數值"
            } else if (eventCount > 0) {
                "$receivedEvents；未解析成有效的此項數值"
            } else {
                "本次量測沒有收到感測事件"
            }
            status == "has_data" -> listOfNotNull(receivedRaw, "資料已收到，但沒有可用的此項換算值").joinToString("；")
            receivedRaw != null -> "$receivedRaw；目前沒有可信的此項換算值"
            else -> "目前沒有可用的近期量測值"
        }
        return if (detail == null || detail in base) base else "$base · $detail"
    }
}
