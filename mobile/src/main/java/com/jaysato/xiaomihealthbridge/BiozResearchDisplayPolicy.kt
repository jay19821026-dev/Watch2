package com.jaysato.xiaomihealthbridge

data class BiozResearchDisplayMetrics(
    val qualityScore: Int?,
    val waveformMedianPositionIndex: Int?,
    val relativeChangePercent: Double?,
    val stabilityScore: Int?,
    val confidenceScore: Int?
)

object BiozResearchDisplayPolicy {
    fun resolve(
        computed: BiomedicalCalibration.BiozResult,
        stored: BiozResearchDisplayMetrics
    ): BiozResearchDisplayMetrics {
        if (computed.waveform.isEmpty()) {
            return BiozResearchDisplayMetrics(null, null, null, null, null)
        }
        return BiozResearchDisplayMetrics(
            qualityScore = computed.contactScore.takeIf { it in 1..100 },
            waveformMedianPositionIndex = computed.waveformMedianPositionIndex,
            relativeChangePercent = computed.relativeChangePercent?.takeIf(Double::isFinite),
            stabilityScore = computed.stabilityScore?.coerceIn(0, 100),
            confidenceScore = computed.confidenceScore?.takeIf { it in 1..100 }
        )
    }
}
