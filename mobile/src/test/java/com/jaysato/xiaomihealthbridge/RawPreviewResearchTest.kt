package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.PpgCompositionQualityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RawPreviewResearchTest {
    @Test
    fun preservesMeasuredZeroAndSentinelGapAsDifferentEvidence() {
        val preview = ResearchSignalSummary.analyze(
            listOf(0.0, PpgCompositionQualityEngine.GAP_SENTINEL, 4.0),
            mappedOrderedWaveform = false,
            preserveInputOrder = true,
            missingSentinels = setOf(PpgCompositionQualityEngine.GAP_SENTINEL)
        )

        val summary = RawPreviewResearch.summarize("spo2_raw", "watch-preview", preview)

        assertEquals(3, summary.preview.values.size)
        assertEquals(2, summary.finitePreviewCount)
        assertEquals(1, summary.zeroFiniteCount)
        assertEquals(listOf("PRESENT_IN_RECEIVED_PREVIEW", "UNKNOWN_OR_GAP", "PRESENT_IN_RECEIVED_PREVIEW"), summary.pointOriginLabels)
        assertEquals(listOf(0.0, null, 1.0), summary.preview.relativeAmplitude)
        assertEquals("EVIDENCE_COMPLETENESS_NOT_ACCURACY", summary.qualityIndex.reason)
        assertNotNull(summary.qualityIndex.score)
    }

    @Test
    fun keepsTemperatureZeroAsObservedRawValueAndIndexDomain() {
        val preview = ResearchSignalSummary.analyze(
            listOf(0.0, 2.0), mappedOrderedWaveform = false, preserveInputOrder = true
        )

        val summary = RawPreviewResearch.summarize("thermometer", "watch-preview", preview)

        assertEquals(1, summary.zeroFiniteCount)
        assertEquals(2, summary.finitePreviewCount)
        assertEquals(2.0, summary.preview.indexTrend!!, 0.0)
        assertNull(summary.qualityIndex.waveformStability)
    }

    @Test
    fun rejectsUnsupportedKindsRatherThanApplyingRawPreviewPolicy() {
        val preview = ResearchSignalSummary.analyze(listOf(1.0), mappedOrderedWaveform = false)

        assertThrows(IllegalArgumentException::class.java) {
            RawPreviewResearch.summarize("ecg", "watch-preview", preview)
        }
    }

    @Test
    fun unknownSourceKeepsSourceEvidenceAtZeroWithoutDroppingFiniteValue() {
        val preview = ResearchSignalSummary.analyze(listOf(1.0), mappedOrderedWaveform = false)

        val summary = RawPreviewResearch.summarize("mi_spo2", "UNKNOWN", preview)

        assertEquals(0, summary.qualityIndex.sourceEvidence)
        assertEquals("EVIDENCE_COMPLETENESS_NOT_ACCURACY", summary.qualityIndex.reason)
        assertTrue(summary.preview.values.single() == 1.0)
    }
}
