package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.PpgCompositionQualityEngine
import org.junit.Assert.*
import org.junit.Test

class ResearchSignalSummaryTest {
    @Test fun fillsOnlySupportedShortInteriorGapsWithoutMutatingRaw() {
        val raw = listOf(1.0, null, 3.0, null, null, null, 7.0)
        val result = ResearchSignalSummary.analyze(raw, true)
        assertEquals(2.0, result.values[1]!!, 0.0)
        assertEquals("IMPUTED", result.origins[1])
        assertNull(result.values[3])
        assertNull(raw[1])
        assertEquals(1, result.reconstructionCount)
    }
    @Test fun unknownEncodingDoesNotBecomeWaveformOrTrend() {
        val result = ResearchSignalSummary.analyze(listOf(1.0, Double.NaN, 3.0), false)
        assertNull(result.values[1])
        assertNull(result.indexTrend)
        assertNull(result.meanAbsoluteDifference)
        assertEquals(2.0 / 3, result.measuredRatio, 1e-9)
    }
    @Test fun linearSignalHasExpectedIndexTrend() {
        assertEquals(2.0, ResearchSignalSummary.analyze(listOf(1.0, 3.0, 5.0), true).indexTrend!!, 1e-9)
    }
    @Test fun orderedUnmappedResearchSignalGetsIndexTrendWithoutGapImputation() {
        val raw = listOf(1.0, null, 5.0, 7.0)
        val result = ResearchSignalSummary.analyze(
            raw,
            mappedOrderedWaveform = false,
            preserveInputOrder = true
        )

        assertNull(result.values[1])
        assertEquals("UNKNOWN", result.origins[1])
        assertEquals(2.0, result.indexTrend!!, 1e-9)
        assertEquals(2.0, result.meanAbsoluteDifference!!, 1e-9)
        assertEquals(0, result.reconstructionCount)
    }
    @Test fun unknownTimebaseStillProvidesDescriptiveRawStatisticsWithoutTrend() {
        val result = ResearchSignalSummary.analyze(listOf(2.0, 4.0, 6.0), false)
        assertEquals(4.0, result.mean!!, 1e-9)
        assertEquals(kotlin.math.sqrt(8.0 / 3.0), result.standardDeviation!!, 1e-9)
        assertNull(result.indexTrend)
        assertNull(result.meanAbsoluteDifference)
        assertEquals(0, result.reconstructionCount)
    }

    @Test fun relativeAmplitudeUsesIndexDomainAndKeepsRealZeroAsMeasured() {
        val raw = listOf(0.0, 5.0, 10.0, null)
        val result = ResearchSignalSummary.analyze(raw, mappedOrderedWaveform = false)

        assertEquals(listOf(0.0, 0.5, 1.0, null), result.relativeAmplitude)
        assertEquals("MEASURED", result.origins[0])
        assertEquals(0.0, result.values[0]!!, 0.0)
        assertNull(result.values[3])
        assertEquals(ResearchSignalSummary.DescriptiveSupport.LOW, result.descriptiveSupport)
    }

    @Test fun ppgGapSentinelRemainsAnIndexedGapInSummaryAndRelativeWaveform() {
        val result = ResearchSignalSummary.analyze(
            listOf(1.0, PpgCompositionQualityEngine.GAP_SENTINEL, 3.0),
            mappedOrderedWaveform = false,
            missingSentinels = setOf(PpgCompositionQualityEngine.GAP_SENTINEL)
        )

        assertEquals(2.0 / 3.0, result.measuredRatio, 1e-9)
        assertEquals(listOf(0.0, null, 1.0), result.relativeAmplitude)
        assertEquals("UNKNOWN", result.origins[1])
        assertEquals(0, result.reconstructionCount)
    }

    @Test fun constantSignalMapsToMidpointAndEmptySignalHasNoSupport() {
        val flat = ResearchSignalSummary.analyze(List(8) { 4.0 }, mappedOrderedWaveform = false)
        val empty = ResearchSignalSummary.analyze(emptyList(), mappedOrderedWaveform = false)

        assertTrue(flat.relativeAmplitude.all { it == 0.5 })
        assertEquals(ResearchSignalSummary.DescriptiveSupport.MODERATE, flat.descriptiveSupport)
        assertEquals(ResearchSignalSummary.DescriptiveSupport.NONE, empty.descriptiveSupport)
        assertTrue(empty.relativeAmplitude.isEmpty())
    }

    @Test fun highSupportDescribesOnlyThePreviewEvidence() {
        val result = ResearchSignalSummary.analyze(List(64) { it.toDouble() }, mappedOrderedWaveform = false)

        assertEquals(ResearchSignalSummary.DescriptiveSupport.HIGH, result.descriptiveSupport)
        assertTrue(ResearchSignalSummary.describe(result).contains("不代表生理準確度"))
        assertTrue(ResearchSignalSummary.describe(result).contains("P10 0.10 · 中位 0.50 · P90 0.90"))
        assertNull(result.indexTrend)
    }
}
