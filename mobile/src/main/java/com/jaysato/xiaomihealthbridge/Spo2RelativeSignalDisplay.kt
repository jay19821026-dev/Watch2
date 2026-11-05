package com.jaysato.xiaomihealthbridge

internal object Spo2RelativeSignalDisplay {
    const val MAX_DISPLAY_POINTS = 8192
    private const val FILL_SENTINEL = 524287f

    fun describe(waveform: List<Float>): String {
        val preview = waveform.take(MAX_DISPLAY_POINTS)
        val input = preview.map { value ->
            value.takeIf { it.isFinite() && it != FILL_SENTINEL }?.toDouble()
        }
        val summary = ResearchSignalSummary.analyze(
            input = input,
            mappedOrderedWaveform = false,
            preserveInputOrder = true
        )
        val finite = input.filterNotNull()
        val finiteRatio = if (input.isEmpty()) 0.0 else finite.size * 100.0 / input.size
        val mean = finite.takeIf { it.isNotEmpty() }?.average()
        return buildString {
            append("原始光學碼值描述統計（索引順序；單位未驗證）")
            append("\n有限原始值 ${"%.1f".format(java.util.Locale.ROOT, finiteRatio)}%")
            mean?.let { append(" · 原始均值 ${"%.3f".format(java.util.Locale.ROOT, it)}") }
            append("\n相對幅值 0–1")
            append("\n短缺口插補 ${summary.reconstructionCount} 點")
            append(ResearchSignalSummary.describe(summary))
            if (waveform.size > preview.size) {
                append("\n統計預覽截取 ${preview.size}/${waveform.size} 點")
            }
            append("\n不換算 SpO₂ 百分比、脈率或臨床值。")
        }
    }
}
