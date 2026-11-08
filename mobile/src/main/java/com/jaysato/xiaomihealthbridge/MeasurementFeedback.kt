package com.jaysato.xiaomihealthbridge

internal data class MeasurementFeedback(val headline: String, val guidance: String, val receivedData: Boolean)

internal object MeasurementFeedbackFactory {
    fun create(status: String?, samples: Int, hasReceivedData: Boolean = samples > 0): MeasurementFeedback {
        val dataReceived = hasReceivedData && status !in setOf(
            "missing", "no_events", "not_measured", "skipped_charging", "derived_estimate",
            "needs_contact", "needs_motion", "needs_oem_mode", "error"
        )
        val headline = when {
            status == "derived_estimate" -> "這次只有參考估算"
            status == "off_wrist" -> "手錶沒有戴好"
            status == "invalid_optical" -> "這次訊號不清楚"
            !dataReceived -> "這次沒有可顯示的量測值"
            status in setOf("research_signal_unscaled", "research_optical", "unscaled_optical") -> "這次沒有可顯示的讀值"
            status == "calibrated_external_reference" -> "已有參考讀值"
            else -> "量測已完成"
        }
        val guidance = when (status) {
            "skipped_charging" -> "為減少耗電與避免充電時接觸量測，本次已略過。拔除充電器、戴妥手錶後再試。"
            "off_wrist" -> "手錶判定未貼合手腕；清潔感測窗並戴緊一格，再重新量測。"
            "needs_contact" -> "請戴好手錶；若量測心電圖，請依畫面指示接觸電極後重測。"
            "needs_motion" -> "此感測器是動作觸發型；依提示抬腕或走動後重試。"
            "needs_oem_mode" -> "請先在手錶原廠健康功能中啟用該量測模式，再返回重試。"
            "no_events", "not_measured" -> "這次沒有量到讀值；戴好手錶並保持靜止後重測。"
            "missing" -> "目前無法從這支手錶讀取此項結果。"
            "derived_estimate" -> "這是依其他紀錄估算的參考值，並非這次直接量到的結果。"
            "invalid_optical" -> "戴好手錶、保持手腕靜止後再量一次。"
            "research_signal_unscaled", "research_optical", "unscaled_optical" -> "請使用手錶原廠量測頁重測，再查看結果。"
            "calibrated_external_reference" -> "這是外部參考量測的紀錄。"
            else -> if (dataReceived) "請查看下方讀值與說明。"
                else "請戴好手錶並重新量測。"
        }
        return MeasurementFeedback(headline, guidance, dataReceived)
    }
}
