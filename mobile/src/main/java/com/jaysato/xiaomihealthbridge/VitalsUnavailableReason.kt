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
        return when {
            status in setOf("needs_oem_mode", "needs_oem") -> "請先在手錶原廠量測頁完成量測，再回來查看結果。"
            status in setOf("needs_contact", "off_wrist", "skipped_off_wrist") -> "手錶未貼合手腕；戴好後重新量測。"
            status == "skipped_charging" -> "請先拔除充電器、戴好手錶後再量測。"
            status == "needs_motion" -> "請依手錶提示走動後再查看結果。"
            measuredAt > 0L && (measuredAt > now || now - measuredAt > freshnessMillis) -> "這筆紀錄已過期；請重新量測。"
            status in setOf("invalid_optical", "invalid_sensor_values", "rejected_quality") ->
                "這次訊號不清楚；戴好手錶並保持靜止，再量一次。"
            status in setOf("research_signal_unscaled", "research_optical", "unscaled_optical", "uncalibrated", "calibration_missing") ->
                "這次沒有取得可顯示的讀值；請使用手錶原廠量測頁重測。"
            status == "has_data" || rawSampleCount > 0 || rawEventCount > 0 -> "這次沒有取得可顯示的讀值；請重新量測。"
            else -> "目前沒有量測結果；請在手錶上量測。"
        }
    }
}
