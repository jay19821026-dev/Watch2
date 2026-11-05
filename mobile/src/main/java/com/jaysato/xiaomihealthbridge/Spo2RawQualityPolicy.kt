package com.jaysato.xiaomihealthbridge

internal object Spo2RawQualityPolicy {
    data class Assessment(
        val score: Int?,
        val status: String,
        val finiteSampleCount: Int,
        val totalSampleCount: Int,
        val finiteCoveragePercent: Int?,
        val qualitySampleCount: Int
    )

    fun assess(waveform: List<Float>, _sampleRateHz: Float): Assessment {
        val finite = waveform.filter { it.isFinite() && it != 524287f }
        val finiteCoverage = if (waveform.isEmpty()) null else
            (finite.size * 100.0 / waveform.size).toInt().coerceIn(0, 100)
        if (finite.size < 16) {
            return Assessment(null, "not_scored_insufficient_samples", finite.size, waveform.size, finiteCoverage, 0)
        }
        val segments = splitFiniteSegments(waveform).filter { it.size >= 8 }
        if (segments.isEmpty()) {
            return Assessment(null, "not_scored_insufficient_contiguous_samples", finite.size, waveform.size, finiteCoverage, 0)
        }
        val maximumStep = segments.flatMap { it.zipWithNext() }.maxOfOrNull { (left, right) ->
            kotlin.math.abs(right.toDouble() - left.toDouble())
        } ?: 0.0
        if (!maximumStep.isFinite() || maximumStep > Float.MAX_VALUE.toDouble()) {
            return Assessment(null, "not_scored_numeric_range", finite.size, waveform.size, finiteCoverage, 0)
        }
        val calculatedScore = segments.sumOf { segment ->
            SensorHealthAlgorithms.waveformQuality(segment, 0f, "optical_raw").score.toDouble() * segment.size
        }.div(segments.sumOf { it.size }).toInt().coerceIn(0, 100)
        val status = when {
            calculatedScore == 0 -> "not_scored_zero_quality"
            finite.size == waveform.size -> "scored_index_domain"
            else -> "scored_index_domain_segmented"
        }
        return Assessment(
            calculatedScore.takeIf { it > 0 }, status, finite.size, waveform.size, finiteCoverage,
            segments.sumOf { it.size }
        )
    }

    fun summary(assessment: Assessment): String {
        val coverage = assessment.finiteCoveragePercent?.let { "${it}%" } ?: "未計算（無事件）"
        val samples = "有效樣本 ${assessment.finiteSampleCount}/${assessment.totalSampleCount}（$coverage）"
        return if (assessment.score != null) {
            val scoredCoverage = if (assessment.qualitySampleCount < assessment.finiteSampleCount) {
                " · 品質評分涵蓋 ${assessment.qualitySampleCount}/${assessment.finiteSampleCount} 個有效點"
            } else ""
            val scoreKind = "索引域波形形狀指數（未使用時間／頻率特徵）"
            "原始 SpO₂ 光學通道 · $scoreKind ${ResearchScoreDisplay.format(assessment.score)} · $samples$scoredCoverage · 尚無可信百分比"
        } else {
            val reason = when (assessment.status) {
                "not_scored_insufficient_samples", "not_scored_insufficient_contiguous_samples" -> "連續有效樣本不足"
                "not_scored_numeric_range" -> "數值範圍不適合品質運算"
                "not_scored_zero_quality" -> "波形品質分數為 0，依規則顯示未評分"
                else -> "尚無可計算波形"
            }
            "原始 SpO₂ 光學通道 · $samples · 形狀品質未評分（$reason） · 尚無可信百分比"
        }
    }

    private fun splitFiniteSegments(waveform: List<Float>): List<List<Float>> {
        val segments = mutableListOf<List<Float>>()
        var current = mutableListOf<Float>()
        waveform.forEach { value ->
            if (value.isFinite() && value != 524287f) {
                current += value
            } else if (current.isNotEmpty()) {
                segments += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) segments += current
        return segments
    }
}
