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
            return Display(
                "已收到溫度訊號",
                when {
                    researchItem?.clinicalCompare.orEmpty().contains("歷史量測時間") -> "這是較早的紀錄；單位未確認，不能當成攝氏體溫。"
                    researchItem?.clinicalCompare.orEmpty().contains("量測時間未知") -> "量測時間未確認；單位也未確認，不能當成攝氏體溫。"
                    else -> "單位未確認，不能當成攝氏體溫。"
                }
            )
        }
        return Display("待量測", "量測完成後顯示溫度結果")
    }

    private val RAW_VALUE = Regex("裝置原始值\\s+([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))")
}
