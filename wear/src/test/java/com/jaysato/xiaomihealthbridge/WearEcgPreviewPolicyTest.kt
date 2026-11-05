package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WearEcgPreviewPolicyTest {
    @Test fun decodedCoverageIsSeparateFromSignalQuality() {
        assertEquals(80.0, WearEcgPreviewPolicy.decodedCoveragePercent(80, 20)!!, 0.0)
        assertEquals(null, WearEcgPreviewPolicy.decodedCoveragePercent(0, 0))
        assertEquals(null, WearEcgPreviewPolicy.decodedCoveragePercent(-1, 3))
    }

    @Test fun decodedResearchWaveformMayContainExplicitGaps() {
        assertEquals(false, WearEcgPreviewPolicy.isResearchDecoded("decoded_research_unscaled"))
        assertEquals(false, WearEcgPreviewPolicy.isResearchDecoded("decoded_research_unscaled_with_gaps"))
        assertEquals(false, WearEcgPreviewPolicy.isResearchDecoded("sample_counter_mismatch"))
    }

    @Test fun analysisUsesMostRecentBoundedWindow() {
        val samples = (0..9).map(Int::toDouble)
        assertEquals(listOf(6.0, 7.0, 8.0, 9.0), WearEcgPreviewPolicy.analysisWindow(samples, 4))
        assertEquals(10, samples.size)
    }

    @Test fun chartDownsamplingIsBoundedAndPreservesEndpoints() {
        val samples = (0..999).map(Int::toFloat)
        val preview = WearEcgPreviewPolicy.downsampleForChart(samples, 10)
        assertEquals(10, preview.size)
        assertEquals(0f, preview.first())
        assertEquals(999f, preview.last())
        assertEquals(1_000, samples.size)
    }

    @Test fun chartDownsamplingKeepsShortGapVisibleBetweenSelectedPoints() {
        val samples = List(100) { index -> if (index in 48..50) Float.NaN else index.toFloat() }

        val preview = WearEcgPreviewPolicy.downsampleForChart(samples, 10)

        assertEquals(10, preview.size)
        assertTrue(preview.any { !it.isFinite() })
    }

    @Test fun smallInputsAreNotCopiedOrChanged() {
        val samples = listOf(1f, 2f)
        assertSame(samples, WearEcgPreviewPolicy.downsampleForChart(samples, 360))
    }
}
