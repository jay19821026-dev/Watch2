package com.jaysato.xiaomihealthbridge

import org.junit.Assert.*
import org.junit.Test

class ResearchQualityIndexTest {
    @Test
    fun placeholderSensorSourcesDoNotReceiveSourceEvidence() {
        listOf(null, "", " / ", "unknown", "UNKNOWN_SOURCE", "not_reported", "unavailable").forEach {
            assertEquals(0, ResearchSourceEvidence.score(it))
        }
        assertEquals(80, ResearchSourceEvidence.score("MAX86178 sensor channel"))
    }

    @Test
    fun availableEvidenceProducesExplainableScore() {
        val result = ResearchQualityIndexCalculator.calculate(1.0, 16, null, 80, 60)
        assertNotNull(result.score)
        assertEquals("EVIDENCE_COMPLETENESS_NOT_ACCURACY", result.reason)
    }

    @Test
    fun noObservationHasNoScoreInsteadOfInventedZeroMeasurement() {
        val result = ResearchQualityIndexCalculator.calculate(0.0, 0, null, 0, 0)
        assertNull(result.score)
        assertEquals("NO_FINITE_OBSERVATION", result.reason)
    }

    @Test
    fun rawEventsWithoutFiniteSamplesRemainUnscored() {
        val result = ResearchQualityIndexCalculator.calculate(
            finiteRatio = 0.0,
            sampleCount = 99,
            waveformStability = null,
            sourceEvidence = 100,
            timeEvidence = 100
        )

        assertNull(result.score)
        assertEquals(20, result.sampleEvidence)
        assertEquals("NO_FINITE_OBSERVATION", result.reason)
    }

    @Test
    fun missingFiniteRatioCannotProduceEvidenceScore() {
        val result = ResearchQualityIndexCalculator.calculate(
            finiteRatio = null,
            sampleCount = 128,
            waveformStability = 90,
            sourceEvidence = 100,
            timeEvidence = 100
        )

        assertNull(result.score)
        assertEquals("NO_FINITE_OBSERVATION", result.reason)
    }
}
