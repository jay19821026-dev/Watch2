package com.jaysato.xiaomihealthbridge

object ConsumerMetricPresentation {
    data class Display(val title: String, val value: String, val state: String)

    fun explanation(item: HealthConclusionEngine.Item): String {
        val recorded = item.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL
        if (recorded && item.id == "spo2") {
            val percent = item.valueText.substringBefore('%').trim().toDoubleOrNull()
            return if (percent != null && percent in 70.0..100.0) ConsumerVitalsSummary.oxygen(percent).detail
                else "請重新量測血氧。"
        }
        if (recorded && item.id == "hr") {
            val bpm = item.valueText.substringBefore("bpm").trim().toDoubleOrNull()
            return if (bpm == null) "尚無心率讀值。" else
                "${ConsumerVitalsSummary.heartRateLabel(bpm)}。成人安靜時常見參考為 60–100 bpm；活動時不同。"
        }
        if (item.level == HealthConclusionEngine.Level.RESEARCH || item.level == HealthConclusionEngine.Level.UNAVAILABLE) {
            return when (item.id) {
                "ecg" -> "這次沒有可解讀的心律結果；請在手錶重新量測。"
                "ppg", "hr", "hrv" -> "這次沒有心率相關讀值；請戴好手錶後重測。"
                "spo2" -> "這次沒有血氧讀值；請戴好手錶後重測。"
                "bioz" -> "這次沒有可顯示的體成分結果；可在手錶重測。"
                "glucose" -> "這支手錶沒有血糖量測功能。"
                else -> "這次沒有可顯示的讀值；可重新量測。"
            }
        }
        return when (item.level) {
            HealthConclusionEngine.Level.GOOD -> "本次讀值落在參考範圍。"
            HealthConclusionEngine.Level.WATCH -> "這項讀值建議再次觀察。"
            HealthConclusionEngine.Level.ATTENTION -> "這項讀值偏離參考範圍，建議重測確認。"
            else -> "可查看讀值與近期變化。"
        }
    }

    fun from(item: HealthConclusionEngine.Item): Display {
        val title = when (item.id) {
            "spo2" -> "血氧"
            "ecg" -> "ECG"
            "ppg" -> "脈搏"
            "bioz" -> "體成分"
            "temp" -> "腕部溫度"
            "bp" -> "血壓"
            "glucose" -> "血糖"
            else -> item.title
        }
        val physiologicalValue = item.valueText
            .replace(Regex("\\s*·\\s*已記錄.*$"), "")
            .replace(Regex("\\s*·\\s*歷史紀錄.*$"), "")
            .takeIf { value ->
                value.contains(Regex("\\d")) &&
                    listOf("%", "bpm", "ms", "mmHg", "°C", "kg", "kcal", "步", "小時", "km", "hPa", "mL").any(value::contains) &&
                    listOf("事件", "樣本", "原始", "解碼", "點").none(value::contains)
            }
        val value = when {
            item.level == HealthConclusionEngine.Level.RESEARCH &&
                item.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RESEARCH_SIGNAL -> "重新量測"
            physiologicalValue != null -> physiologicalValue
            item.id == "glucose" -> "未支援"
            else -> "待量測"
        }
        val state = when {
            item.id == "glucose" -> "此裝置無量測功能"
            else -> when (item.userState) {
                HealthConclusionEngine.UserState.NORMAL -> "正常"
                HealthConclusionEngine.UserState.ATTENTION -> "需留意"
                HealthConclusionEngine.UserState.RESEARCH -> "尚無健康結果"
                HealthConclusionEngine.UserState.UNAVAILABLE -> "尚無結果"
            }
        }
        return Display(title, value, state)
    }
}
