package com.jaysato.xiaomihealthbridge

internal object TemperatureHomeSummaryPolicy {
    data class Display(val value: String, val detail: String)

    fun resolve(
        verifiedCelsius: Double?,
        researchItem: HealthConclusionEngine.Item?
    ): Display {
        verifiedCelsius?.takeIf(Double::isFinite)?.let {
            return Display("%.1f °C".format(it), "已驗證攝氏單位 · 最近一次裝置溫度")
        }

        val rawValue = researchItem?.takeIf { it.available }
            ?.valueText
            ?.let { RAW_VALUE.find(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull() }
            ?.takeIf(Double::isFinite)
        if (rawValue != null) {
            val source = researchItem?.valueText?.substringAfter(" · ", "")?.takeIf(String::isNotBlank)
            val timeEvidence = researchItem?.clinicalCompare?.substringAfterLast(" · ")
                ?.takeIf(String::isNotBlank)
            return Display(
                "原始值 %.1f".format(rawValue),
                buildString {
                    append("裝置研究讀值 · 單位未驗證")
                    source?.let { append(" · 來源：$it") }
                    timeEvidence?.let { append(" · $it") }
                }
            )
        }
        return Display("待量測", "量測完成後顯示溫度結果")
    }

    private val RAW_VALUE = Regex("裝置原始值\\s+([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))")
}
