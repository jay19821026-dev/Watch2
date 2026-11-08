package com.jaysato.xiaomihealthbridge

internal data class MeasurementFeedback(val headline: String, val guidance: String, val receivedData: Boolean)

internal object MeasurementFeedbackFactory {
    fun create(status: String?, samples: Int, hasReceivedData: Boolean = samples > 0): MeasurementFeedback {
        val dataReceived = hasReceivedData && status !in setOf(
            "missing", "no_events", "not_measured", "skipped_charging", "derived_estimate",
            "needs_contact", "needs_motion", "needs_oem_mode", "error"
        )
        val headline = when {
            status == "derived_estimate" -> "僅有推估資料"
            status == "off_wrist" -> "有資料，但偵測為離腕"
            status == "invalid_optical" -> "光學訊號品質不足"
            !dataReceived -> "這次沒有可顯示的量測值"
            status in setOf("research_signal_unscaled", "research_optical", "unscaled_optical") -> "已收到研究訊號"
            status == "calibrated_external_reference" -> "已取得外部參考校準資料"
            else -> "已收到感測資料"
        }
        val guidance = when (status) {
            "skipped_charging" -> "為減少耗電與避免充電時接觸量測，本次已略過。拔除充電器、戴妥手錶後再試。"
            "off_wrist" -> "手錶判定未貼合手腕；清潔感測窗並戴緊一格，再重新量測。"
            "needs_contact" -> "此感測通道需要皮膚／電極接觸；依畫面指示貼合感測器後重試。"
            "needs_motion" -> "此感測器是動作觸發型；依提示抬腕或走動後重試。"
            "needs_oem_mode" -> "請先在手錶原廠健康功能中啟用該量測模式，再返回重試。"
            "no_events", "not_measured" -> "量測期間感測器沒有回報事件。確認已佩戴、解除充電並開啟原廠量測模式後再試。"
            "missing" -> "手錶尚未提供此感測通道；可重試公開量測，但不會以其他感測器偽裝補值。"
            "derived_estimate" -> "這是由其他資料推算的估值，不是此感測器的實測。已避免把它當成量測成功或醫療讀值。"
            "invalid_optical" -> "保持手腕靜止、讓感測窗貼合皮膚，避開強光後重試。訊號不合格時不推算心率或 HRV。"
            "research_signal_unscaled", "research_optical", "unscaled_optical" -> "收到原始／研究訊號，但缺少手錶通道映射或校準；不會把它當成醫療讀值。"
            "calibrated_external_reference" -> "資料依外部參考校準顯示，並非手錶原廠臨床校準或疾病診斷。"
            else -> if (dataReceived) "請以原廠健康功能與醫療器材讀值交叉確認；此資料不構成疾病診斷。"
                else "可按下重新量測；確認佩戴接觸、原廠量測模式與手錶同步。"
        }
        return MeasurementFeedback(headline, guidance, dataReceived)
    }
}
