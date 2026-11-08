package com.jaysato.xiaomihealthbridge

object ConsumerMetricPresentation {
    data class Display(val title: String, val value: String, val state: String)

    fun from(item: HealthConclusionEngine.Item): Display {
        val title = when (item.id) {
            "spo2" -> "血氧"
            "ecg" -> "ECG"
            "ppg" -> "脈搏"
            "bioz" -> "體成分"
            "bp" -> "血壓"
            "glucose" -> "血糖"
            else -> item.title
        }
        val physiologicalValue = item.valueText
            .replace(Regex("\\s*·\\s*已記錄.*$"), "")
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
