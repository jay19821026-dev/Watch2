package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalScoreAvailabilityTest {
    @Test
    fun absentOrUncalibratedDataIsNotAZeroQualityMeasurement() {
        assertFalse(ClinicalVerdict.forRawSpO2(0, "not_measured", null).qualityScoreAvailable)
        assertFalse(ClinicalVerdict.forRawSpO2(100, "research_signal_unscaled", null).qualityScoreAvailable)
        assertFalse(ClinicalVerdict.forTemperature(null, 0, "not_measured", null).qualityScoreAvailable)
    }

    @Test
    fun zeroQualityScoresRenderAsUnratedWithoutChangingRawCounts() {
        val bioz = ClinicalVerdict.forBioz(
            qualityScore = 0, impedanceOhm = null, resistanceOhm = null,
            reactanceOhm = null, hydrationIndex = null,
            status = "research_signal_unscaled", rawSampleCount = 0, hasReceivedSignal = true
        )
        val oxygen = ClinicalVerdict.forRawSpO2(8, "has_data", null, researchEvidenceScore = 0)
        val ecg = ClinicalVerdict.forEcg(
            qualityScore = 0, sampleCount = 0, sampleRate = 0f,
            status = "research_signal_unscaled", heartRateBpm = null,
            rrCount = 0, wearQualified = true,
            waveformEncoding = "ecg_baseline_relative_unitless"
        )

        listOf(bioz, oxygen, ecg).forEach { verdict ->
            assertNull(verdict.score)
            assertFalse(verdict.findings.any { it.contains("0/100") })
        }
        assertTrue(bioz.findings.any { it.contains("未評分") })
        assertTrue(oxygen.findings.any { it.contains("未評分") })
        assertTrue(ecg.findings.any { it.contains("未評分") })
        assertTrue(bioz.findings.any { it.contains("原始波形點數 0") })
        assertTrue(ecg.findings.any { it.contains("樣本 0 點") })
    }

    @Test
    fun temperatureUnknownEvidenceScoreIsNotShownAsZero() {
        val result = ClinicalVerdict.forTemperature(35.0, 0, "has_data", null)
        assertNull(result.score)
        assertTrue(result.findings.any { it.contains("未評分") })
        assertFalse(result.findings.any { it.contains("0/100") || it.contains("score=0") })
        assertTrue(result.findings.any { it.contains("樣本 0") })
    }

    @Test
    fun unscoredSuiteHasNoNumericScore() {
        val result = ClinicalVerdict.forSuite(0, 36, null, null, null, null, null)
        assertNull(result.score)
        assertFalse(result.qualityScoreAvailable)
        assertTrue(result.findings.any { it.contains("尚無可計分通道") })
    }

    @Test
    fun rawOxygenCompletenessIsNotPresentedAsSignalQualityOrAccuracy() {
        val result = ClinicalVerdict.forRawSpO2(
            40, "research_signal_unscaled", true, researchEvidenceScore = 68
        )
        assertTrue(result.findings.any { it.contains("資料完整度指數 68/100") })
        assertFalse(result.findings.any { it.contains("研究波形品質") })
        assertFalse(result.qualityScoreAvailable)
    }

    @Test
    fun scoreFormatterNeverPresentsZeroAsRatedQuality() {
        assertTrue(ResearchScoreDisplay.format(0).contains("未評分"))
        assertTrue(ResearchScoreDisplay.format(0.0).contains("未評分"))
        assertTrue(ResearchScoreDisplay.format(73).contains("73/100"))
        assertTrue(ResearchScoreDisplay.format(73.25).contains("73.3/100"))
    }
}
