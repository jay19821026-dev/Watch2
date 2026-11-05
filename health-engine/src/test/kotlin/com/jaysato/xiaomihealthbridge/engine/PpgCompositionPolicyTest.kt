package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.*
import org.junit.Test

class PpgCompositionPolicyTest {
    private val quality = PpgCompositionQualityEngine.assess(listOf(1.0, 2.0, 1.0, 3.0), 0.0, 0.0..10.0)
    private val context = ReconstructionContext(1, 0.05, 4, quality, SampleRateTrust.VERIFIED, TimestampSource.SENSOR, true, true)

    @Test fun unknownMotionAndAdcRemainUnknown() {
        val report = PpgCompositionQualityEngine.assess(listOf(1.0, null, 2.0))
        assertNull(report.motionArtifactScore)
        assertNull(report.combinedQualityScore)
        assertNull(report.clippingRatio)
        assertEquals(2.0 / 3, report.finiteRatio, 0.00001)
        assertTrue(report.reasonCodes.contains("ADC_RANGE_UNKNOWN"))
    }

    @Test fun gapSentinelIsExcludedFromQualityButRawPointCountIsPreserved() {
        val report = PpgCompositionQualityEngine.assess(
            listOf(1.0, 2.0, PpgCompositionQualityEngine.GAP_SENTINEL, 2.5, 1.5),
            motionArtifactScore = null
        )

        assertEquals(0.8, report.finiteRatio, 0.00001)
        assertEquals(0.2, report.missingRatio, 0.00001)
        assertEquals(1.5, report.amplitudeRange!!, 0.00001)
        assertTrue(report.reasonCodes.contains("EXPLICIT_MISSING_VALUES"))
    }

    @Test fun unknownMotionDoesNotBecomeZeroInCombinedQuality() {
        val waveformOnly = PpgCompositionQualityEngine.assess(listOf(1.0, 2.0, 1.0, 3.0), adcRange = 0.0..10.0)
        val noMotionEvidence = PpgCompositionQualityEngine.assess(listOf(1.0, 2.0, 1.0, 3.0), motionArtifactScore = null, adcRange = 0.0..10.0)
        val measuredNoMotion = PpgCompositionQualityEngine.assess(listOf(1.0, 2.0, 1.0, 3.0), motionArtifactScore = 0.0, adcRange = 0.0..10.0)
        val measuredMotion = PpgCompositionQualityEngine.assess(listOf(1.0, 2.0, 1.0, 3.0), motionArtifactScore = 0.5, adcRange = 0.0..10.0)
        val unknownAdc = PpgCompositionQualityEngine.assess(listOf(1.0, 2.0, 1.0, 3.0), motionArtifactScore = 0.0)

        assertEquals(waveformOnly.qualityScore, noMotionEvidence.qualityScore, 0.0)
        assertNull(noMotionEvidence.combinedQualityScore)
        assertEquals(measuredNoMotion.qualityScore, measuredNoMotion.combinedQualityScore!!, 0.0)
        assertEquals(measuredNoMotion.qualityScore * 0.5, measuredMotion.combinedQualityScore!!, 0.0)
        assertNull(unknownAdc.combinedQualityScore)
    }

    @Test fun shortGapPrefersLinearAndUnknownClockStaysIndexDomain() {
        assertEquals(ReconstructionMethod.LINEAR, ReconstructionPolicyEngine.select(context).selectedMethod)
        val result = ReconstructionPolicyEngine.select(context.copy(sampleRateTrust = SampleRateTrust.UNKNOWN))
        assertEquals("NORMALIZED_INDEX", result.numericalDomain)
        assertEquals(0.25, result.expectedConfidence, 0.0)
    }

    @Test fun mediumGapRequiresAvailableKalmanAndVerifiedRate() {
        val medium = context.copy(gapSamples = 5, gapDurationSeconds = 0.25, kalmanAvailable = true)
        assertEquals(ReconstructionMethod.KALMAN, ReconstructionPolicyEngine.select(medium).selectedMethod)
        assertEquals(ReconstructionMethod.STATISTICAL_BASELINE, ReconstructionPolicyEngine.select(medium.copy(sampleRateTrust = SampleRateTrust.UNKNOWN)).selectedMethod)
    }

    @Test fun learnedSelectionRequiresVersionAndConfidence() {
        val long = context.copy(gapSamples = 100, gapDurationSeconds = 5.0)
        assertEquals(ReconstructionMethod.STATISTICAL_BASELINE, ReconstructionPolicyEngine.select(long).selectedMethod)
        assertEquals(ReconstructionMethod.STATISTICAL_BASELINE, ReconstructionPolicyEngine.select(long.copy(learnedModelVersion = "test-model-only", learnedModelConfidence = 0.8)).selectedMethod)
        val selected = ReconstructionPolicyEngine.select(long.copy(learnedModelVersion = "test-model-only", learnedModelConfidence = 0.8, learnedModelValidatedForDomain = true))
        assertEquals(ReconstructionMethod.LEARNED, selected.selectedMethod)
        assertEquals(0.2, selected.expectedConfidence, 0.0)
    }

    @Test fun flatlineAndUnverifiedMappingCannotBecomeReconstruction() {
        val flatline = PpgCompositionQualityEngine.assess(List(8) { 1.0 }, 0.0, 0.0..10.0)
        assertEquals(0.0, flatline.qualityScore, 0.0)
        assertEquals(ReconstructionMethod.STATISTICAL_BASELINE, ReconstructionPolicyEngine.select(context.copy(quality = flatline)).selectedMethod)
        val unverified = ReconstructionPolicyEngine.select(context.copy(channelMappingVerified = false))
        assertEquals(ReconstructionMethod.LINEAR, unverified.selectedMethod)
        assertTrue(unverified.expectedConfidence < 0.2)
    }
}
