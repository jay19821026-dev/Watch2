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
                    listOf("%", "bpm", "ms", "mmHg", "°C", "kg", "kcal").any(value::contains) &&
                    listOf("事件", "樣本", "原始", "解碼", "點").none(value::contains)
            }
        val value = when {
            physiologicalValue != null -> physiologicalValue
            item.id == "glucose" -> "未支援"
            item.available && item.valueText.isNotBlank() && item.valueText !in setOf("—", "不可用") -> item.valueText
            else -> "待量測"
        }
        val state = when {
            item.id == "glucose" -> "此裝置無量測功能"
            else -> when (item.userState) {
                HealthConclusionEngine.UserState.NORMAL -> "正常"
                HealthConclusionEngine.UserState.ATTENTION -> "需留意"
                HealthConclusionEngine.UserState.RESEARCH -> "研究資料"
                HealthConclusionEngine.UserState.UNAVAILABLE -> "尚無結果"
            }
        }
        return Display(title, value, state)
    }
}
