package com.jaysato.xiaomihealthbridge

internal object VitalsCardPresentationPolicy {
    fun heartRateHeadline(liveBpm: Double?, fallbackValue: String?): String {
        val bpmConclusion = fallbackValue?.takeIf {
            it.isNotBlank() && (it.contains("bpm", ignoreCase = true) || it.contains("次/分"))
        }
        return liveBpm?.takeIf(Double::isFinite)?.let { "%.0f bpm".format(it) }
            ?: bpmConclusion
            ?: "待有效心率"
    }

    fun recordedSpO2Headline(valueText: String?): String =
        valueText?.removeSuffix(" · 已記錄")?.takeIf(String::isNotBlank) ?: "待記錄"

    fun heartRateReference(bpm: Double?): String {
        val value = bpm?.takeIf { it.isFinite() } ?: return "心率參考：沒有可用讀值"
        return when {
            value !in ClinicalReferenceTables.PLAUSIBLE_HR_BPM.min..ClinicalReferenceTables.PLAUSIBLE_HR_BPM.max ->
                "讀值超出穿戴式心率合理參考窗；請確認佩戴並重新量測。"
            value > ClinicalReferenceTables.RESTING_HR_BPM.max ->
                "成人靜息參考 60–100 bpm；這筆高於上限。若量測時正在活動，先休息再量一次。"
            value < ClinicalReferenceTables.RESTING_HR_BPM.min ->
                "成人靜息參考 60–100 bpm；這筆低於下限。睡眠、訓練與藥物可能影響心率。"
            else -> "成人靜息參考 60–100 bpm；這筆落在範圍內。活動時的參考不同。"
        }
    }

    fun recordedSpO2Detail(source: String?, timestamp: String?): String {
        val label = when {
            source?.contains("xiaomi", ignoreCase = true) == true -> "Mi Fitness"
            source.isNullOrBlank() -> "來源未提供"
            else -> source
        }
        return "這筆是歷史紀錄。來源 $label · ${timestamp ?: "時間未提供"}。"
    }

    fun oxygenInterpretation(percent: Double?): String {
        val value = percent?.takeIf { it.isFinite() && it in 70.0..100.0 }
            ?: return "沒有可比較的血氧百分比"
        return if (value >= 95.0) {
            "在常見參考範圍內（95–100%）。這筆數值表示量測當時的血氧比例。"
        } else {
            "低於常見參考範圍（95–100%）。先保持靜止、戴緊手錶重測；若持續偏低或身體不適，請尋求醫療評估。"
        }
    }
}
