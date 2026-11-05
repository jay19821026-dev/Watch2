package com.jaysato.xiaomihealthbridge

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgResearchSignalAnalysisTest {
    @Test
    fun identifiesSixtyHertzInterferenceAndPreservesPointCount() {
        val sampleRate = 500f
        val values = List(2_000) { index ->
            (2.0 * sin(2.0 * PI * 60.0 * index / sampleRate) +
                0.3 * sin(2.0 * PI * 1.2 * index / sampleRate)).toFloat()
        }

        val result = requireNotNull(EcgResearchSignalAnalysis.analyze(values, sampleRate))

        assertEquals(values.size, result.filtered.size)
        assertTrue(result.engineeringQualityScore in 1..99)
        assertTrue(result.engineeringQualityScore > result.rawEngineeringQualityScore)
        assertTrue(result.correctedMains60PowerRatio < result.mains60PowerRatio)
        assertTrue(result.engineeringQualityScore >= 80)
        assertTrue((result.stableWindowQualityScore ?: 0) >= 80)
        assertTrue(result.stableWindowCoverageRatio > 0.0)
        assertTrue(result.mains60PowerRatio > 0.5)
        assertTrue(result.issues.any { it.contains("60 Hz") })
    }

    @Test
    fun rejectsUnknownTimebase() {
        assertEquals(null, EcgResearchSignalAnalysis.analyze(List(200) { 1f }, 0f))
    }

    @Test
    fun stableWindowScoreIsReportedSeparatelyFromWholeCaptureScore() {
        val sampleRate = 500f
        val values = List(2_500) { index ->
            val stable = sin(2.0 * PI * 1.1 * index / sampleRate)
            val edgeArtifact = if (index < 500) 8.0 * sin(2.0 * PI * 60.0 * index / sampleRate) else 0.0
            (stable + edgeArtifact).toFloat()
        }

        val result = requireNotNull(EcgResearchSignalAnalysis.analyze(values, sampleRate))

        assertTrue((result.stableWindowQualityScore ?: 0) in 1..99)
        assertTrue(result.stableWindowCount > 0)
        assertTrue(result.stableWindowCoverageRatio in 0.0..1.0)
    }
}
