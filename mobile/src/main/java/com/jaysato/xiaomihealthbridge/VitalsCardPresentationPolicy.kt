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
                "高於成人安靜心率常見上限（若量測時正在休息）；活動、情緒或其他因素也會影響，建議安靜後複測。"
            value < ClinicalReferenceTables.RESTING_HR_BPM.min ->
                "低於成人安靜心率常見下限（若量測時正在休息）；睡眠、訓練狀態或藥物也可能影響。"
            else -> "落在成人安靜心率常見範圍（僅適用安靜狀態；不是疾病判定）。"
        }
    }

    fun recordedSpO2Detail(source: String?, timestamp: String?): String =
        "來源：${source?.takeIf(String::isNotBlank) ?: "未提供"} · 記錄時間：${timestamp?.takeIf(String::isNotBlank) ?: "未提供"} · 時效尚未驗證，不代表目前值。"
}
