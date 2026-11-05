package com.jaysato.xiaomihealthbridge

import java.util.Locale

data class TemperatureResearchSummary(
    val finiteSampleCount: Int,
    val medianRaw: Double,
    val minimumRaw: Double,
    val maximumRaw: Double,
    val lastSequenceValueRaw: Double,
    val lastMinusMedianRaw: Double?,
    val normalizedPosition: Double?,
    val evidenceIndex: ResearchQualityIndex
) {
    fun displayText(): String = buildString {
        append("同次事件序列（索引順序，非時間尺度）")
        append("：有限樣本 $finiteSampleCount 點")
        if (finiteSampleCount == 1) {
            append(" · 單點原始值 ${lastSequenceValueRaw.formatRaw()}（不建立基線）")
            append(" · 相對變化無法計算（至少需 2 點）")
        } else {
            append(" · 中位數 ${medianRaw.formatRaw()}")
            append(" · 範圍 ${minimumRaw.formatRaw()}–${maximumRaw.formatRaw()}")
            append(" · 最後有限樣本−中位數 ${lastMinusMedianRaw?.formatSignedRaw() ?: "無法計算（數值範圍溢位）"}")
            normalizedPosition?.let {
                append(" · 最後有限樣本區間相對位置 ${it.formatRaw()}（0–1，本次最小至最大正規化，非品質分數）")
            }
        }
        append(" · 資料完整度 ${ResearchScoreDisplay.format(evidenceIndex.score)}")
        append("（有限值／樣本數／來源／時間證據；非準確率或統計信心）")
        append(" · 分析信心未評分（原始通道單位與裝置校準未驗證）")
        append("（原始通道單位未驗證）")
    }

    companion object {
        fun calculate(
            values: List<Float>,
            sourceEvidence: Int = 35,
            timeEvidence: Int = 0
        ): TemperatureResearchSummary? = calculateRaw(values.map(Float::toDouble), sourceEvidence, timeEvidence)

        fun calculateRaw(
            values: List<Double>,
            sourceEvidence: Int = 35,
            timeEvidence: Int = 0
        ): TemperatureResearchSummary? {
            val finite = values.filter(Double::isFinite)
            if (finite.isEmpty()) return null
            val sorted = finite.sorted()
            val median = if (sorted.size % 2 == 0) {
                (sorted[sorted.size / 2 - 1] / 2.0) + (sorted[sorted.size / 2] / 2.0)
            } else sorted[sorted.size / 2]
            val min = sorted.first()
            val max = sorted.last()
            val last = finite.last()
            val range = max - min
            val delta = last - median
            val evidenceIndex = ResearchQualityIndexCalculator.calculate(
                finiteRatio = finite.size.toDouble() / values.size,
                sampleCount = finite.size,
                waveformStability = null,
                sourceEvidence = sourceEvidence,
                timeEvidence = timeEvidence
            )
            return TemperatureResearchSummary(
                finiteSampleCount = finite.size,
                medianRaw = median,
                minimumRaw = min,
                maximumRaw = max,
                lastSequenceValueRaw = last,
                lastMinusMedianRaw = delta.takeIf { finite.size >= 2 && it.isFinite() },
                normalizedPosition = if (range.isFinite() && range > 0.0) {
                    ((last - min) / range).takeIf(Double::isFinite)?.coerceIn(0.0, 1.0)
                } else null,
                evidenceIndex = evidenceIndex
            )
        }

        private fun Double.formatRaw(): String = String.format(Locale.ROOT, "%.2f", this)
        private fun Double.formatSignedRaw(): String = String.format(Locale.ROOT, "%+.2f", this)
    }
}
