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
        if (recorded && item.id == "bp") {
            val numbers = item.valueText.substringBefore("mmHg").split('/').mapNotNull { it.trim().toDoubleOrNull() }
            if (numbers.size == 2) {
                val category = OpticalBloodPressureApi.categorize(numbers[0], numbers[1])
                return "單次袖帶讀值：${OpticalBloodPressureApi.categoryLabel(category)}。可與近期紀錄比較。"
            }
            return "這筆血壓紀錄格式有誤，請重新檢查。"
        }
        val researchValue = researchValue(item)
        if (researchValue != null) return when (item.id) {
            "sleep" -> "這是近 7 日已記錄的睡眠時段，不等於實際睡眠時數。"
            "pressure" -> if (item.valueText.contains("歷史"))
                "這是較早的環境氣壓紀錄，不代表目前氣壓、血壓或實際海拔。"
                else "這是手錶量到的環境氣壓，不是血壓或實際海拔。"
            "hrv" -> when (item.level) {
                HealthConclusionEngine.Level.GOOD -> "落在程式使用的研究參考範圍；可與自己的近期紀錄比較。"
                HealthConclusionEngine.Level.WATCH -> "低於程式使用的研究參考範圍；可在休息後重測。"
                else -> "這是心率變化的研究估算，可與自己的近期紀錄比較。"
            }
            "ppg" -> "這是光學訊號推估的脈率；可對照手錶心率紀錄。"
            "ecg" -> "這是研究估算心率；這次仍沒有可確認的心律分類。"
            else -> "這是參考資料，可查看近期變化。"
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
            "ecg" -> "心電圖"
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
                item.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL &&
                value.contains(Regex("\\d")) &&
                    listOf("%", "bpm", "ms", "mmHg", "°C", "kg", "kcal", "步", "小時", "km", "hPa", "mL").any(value::contains) &&
                    listOf("事件", "樣本", "原始", "解碼", "點").none(value::contains)
            }
        val researchValue = researchValue(item)
        val value = when {
            physiologicalValue != null -> physiologicalValue
            researchValue != null -> researchValue
            item.id == "glucose" -> "未支援"
            item.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RESEARCH_SIGNAL && item.available -> "重新量測"
            else -> "待量測"
        }
        val state = when {
            item.id == "glucose" -> "此裝置無量測功能"
            researchValue != null -> when (item.id) {
                "ecg" -> "心律未判定"
                "sleep" -> "已記錄"
                "pressure" -> if (item.valueText.contains("歷史")) "歷史紀錄" else "已記錄"
                else -> "研究估算"
            }
            item.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RESEARCH_SIGNAL ->
                if (item.available) "尚無健康結果" else "尚無結果"
            else -> when (item.userState) {
                HealthConclusionEngine.UserState.NORMAL -> "正常"
                HealthConclusionEngine.UserState.ATTENTION -> "需留意"
                HealthConclusionEngine.UserState.RESEARCH -> "尚無健康結果"
                HealthConclusionEngine.UserState.UNAVAILABLE -> "尚無結果"
            }
        }
        return Display(title, value, state)
    }

    private fun researchValue(item: HealthConclusionEngine.Item): String? {
        if (!item.available || item.evidenceCategory != HealthConclusionEngine.EvidenceCategory.RESEARCH_SIGNAL) return null
        val text = item.valueText
        fun captured(pattern: String): String? = Regex(pattern).find(text)?.groupValues?.getOrNull(1)
        return when (item.id) {
            "sleep" -> captured("^近 7 日\\s+([0-9]+(?:\\.[0-9]+)?)\\s*小時紀錄區間")?.let { "$it 小時 · 7 日紀錄" }
            "pressure" -> captured("^([0-9]+(?:\\.[0-9]+)?)\\s*hPa")?.let { "$it hPa" }
            "hrv" -> captured("RMSSD\\s+([0-9]+(?:\\.[0-9]+)?)\\s*ms")?.let { "$it ms · 研究估算" }
            "ppg", "ecg" -> captured("^([0-9]+(?:\\.[0-9]+)?)\\s*bpm")?.let { "$it bpm · 研究估算" }
            else -> null
        }
    }
}
