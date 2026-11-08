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
        return "多數健康成人常見參考 95–100%；這筆是歷史紀錄。來源 $label · ${timestamp ?: "時間未提供"}。"
    }
}
