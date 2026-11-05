package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin

class BiozResearchDisplayPolicyTest {
    @Test
    fun currentWaveformMetricsTakePrecedenceOverStaleStoredMetrics() {
        val waveform = List(128) { index -> (sin(index * 0.2) * 80.0 + 100.0).toFloat() }
        val computed = BiomedicalCalibration.calibrateBioz(emptyList(), waveform)
        val stored = BiozResearchDisplayMetrics(99, 99, 88.0, 99, 99)

        val result = BiozResearchDisplayPolicy.resolve(computed, stored)

        assertEquals(computed.contactScore, result.qualityScore)
        assertEquals(computed.waveformMedianPositionIndex, result.waveformMedianPositionIndex)
        assertEquals(computed.relativeChangePercent, result.relativeChangePercent)
        assertEquals(computed.stabilityScore, result.stabilityScore)
        assertEquals(computed.confidenceScore, result.confidenceScore)
    }

    @Test
    fun staleStoredMetricsAreClearedWhenNoCurrentWaveformExists() {
        val computed = BiomedicalCalibration.calibrateBioz(emptyList(), emptyList())
        val stored = BiozResearchDisplayMetrics(67, 51, -12.0, 43, 38)

        assertEquals(
            BiozResearchDisplayMetrics(null, null, null, null, null),
            BiozResearchDisplayPolicy.resolve(computed, stored)
        )
    }

    @Test
    fun staleStoredMetricsAreClearedWhenCurrentWaveformContainsOnlyMissingValues() {
        val computed = BiomedicalCalibration.calibrateBioz(emptyList(), List(12) { Float.NaN })
        val stored = BiozResearchDisplayMetrics(67, 51, -12.0, 43, 38)

        assertEquals(
            BiozResearchDisplayMetrics(null, null, null, null, null),
            BiozResearchDisplayPolicy.resolve(computed, stored)
        )
    }

    @Test
    fun zeroEvidenceScoreIsUnratedWhileRawSamplesRemainAvailable() {
        val waveform = List(128) { 7f }
        val computed = BiomedicalCalibration.calibrateBioz(emptyList(), waveform)
        val stored = BiozResearchDisplayMetrics(67, 51, -12.0, 43, 38)

        val result = BiozResearchDisplayPolicy.resolve(computed, stored)

        assertEquals(0, computed.contactScore)
        assertEquals(128, computed.waveform.size)
        assertEquals(null, result.qualityScore)
        assertEquals(null, result.waveformMedianPositionIndex)
        assertEquals(null, result.relativeChangePercent)
    }
}
