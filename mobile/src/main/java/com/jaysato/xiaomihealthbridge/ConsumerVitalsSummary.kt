package com.jaysato.xiaomihealthbridge

internal object ConsumerVitalsSummary {
    data class Copy(val headline: String, val detail: String)

    fun oxygen(percent: Double): Copy = if (percent < 95.0) {
        Copy("血氧 ${"%.0f".format(percent)}%：偏低", "常見參考 95–100%；可在靜止後重測。")
    } else {
        Copy("血氧 ${"%.0f".format(percent)}%：正常範圍", "常見參考 95–100%；這是量測當時的紀錄。")
    }

    fun heartRate(bpm: Double): Copy = Copy(
        "心率 ${"%.0f".format(bpm)} bpm：${heartRateLabel(bpm)}",
        when {
            bpm > 100.0 -> "高於成人靜息參考 60–100 bpm；若當時在活動，休息後再測。"
            bpm < 60.0 -> "低於成人靜息參考 60–100 bpm；睡眠或規律運動可能影響數值。"
            else -> "在成人靜息參考 60–100 bpm 內；這筆未確認為靜息量測。"
        }
    )

    fun heartRateLabel(bpm: Double): String = when {
        bpm > 100.0 -> "偏高"
        bpm < 60.0 -> "偏低"
        else -> "正常範圍"
    }

    fun from(report: HealthConclusionEngine.Report): Copy {
        val oxygenPercent = report.items.firstOrNull {
            it.id == "spo2" && it.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL
        }?.valueText?.substringBefore('%')?.trim()?.toDoubleOrNull()
        val heart = report.items.firstOrNull {
            it.id == "hr" && it.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL
        }?.valueText?.substringBefore("bpm")?.trim()?.toDoubleOrNull()
        if (oxygenPercent != null && oxygenPercent >= 70.0 && oxygenPercent < 95.0) return oxygen(oxygenPercent)
        if (heart != null && heart in 30.0..220.0 && heart !in 60.0..100.0) return heartRate(heart)
        if (oxygenPercent != null && oxygenPercent in 95.0..100.0) return oxygen(oxygenPercent)
        if (heart != null && heart in 30.0..220.0) return heartRate(heart)
        val lead = AnalysisLeadSummary.from(report.items)
        return Copy(lead.headline, lead.nextAction)
    }
}
