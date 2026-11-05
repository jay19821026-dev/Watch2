package com.jaysato.xiaomihealthbridge

internal object CompetitorFormulaRowFormatter {
    fun format(
        kind: String,
        piPercent: Float?,
        rmssdMs: Float?,
        altitudeM: Float?,
        note: String?,
        eligibility: RawSensorFormulaDisplayPolicy.Eligibility,
        hasCachedFormula: Boolean
    ): String = buildString {
        append(kind)
        if (eligibility.showOpticalMetrics && piPercent?.isFinite() == true) {
            append(" · PI ${"%.2f".format(piPercent)}%")
        }
        if (eligibility.showHrvMetrics && rmssdMs?.isFinite() == true) {
            append(" · RMSSD ${"%.0f".format(rmssdMs)} ms")
        }
        if (eligibility.showEnvironmentMetrics && altitudeM?.isFinite() == true) {
            append(" · alt ${"%.1f".format(altitudeM)}m")
        }
        if (!note.isNullOrBlank() && (
                eligibility.showOpticalMetrics || eligibility.showHrvMetrics || eligibility.showEcgPulse ||
                    eligibility.showEnvironmentMetrics
            )
        ) append(" · $note")
        if (length == kind.length) {
            append(if (hasCachedFormula) " · 快取存在但資格／新鮮度未通過，未顯示數值" else " · 尚無公式資料")
        }
    }
}
