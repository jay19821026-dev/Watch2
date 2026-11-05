package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.*
import org.junit.Test

class FullCoverageSignalTest {
    private val provenance = SensorProvenance(
        "session", "ppg", "single-channel raw reference", "capture-v1", null,
        0.8, "RESEARCH", TimestampSource.UNKNOWN, null, 1L
    )
    private fun compose(raw: List<Double?>) = FullCoverageSignalComposer.composeIndexDomain(raw, provenance, true, true, true)

    @Test fun rawMissingPreservedAndAnalysisFinite() {
        val raw = listOf(1.0, null, Double.NaN, 4.0)
        val result = (compose(raw) as FullCoverageResult.Complete).signal
        assertNull(raw[1])
        assertTrue(raw[2]!!.isNaN())
        assertTrue(result.points.all { it.value.isFinite() })
        assertEquals(2.0, result.points[1].value, 0.00001)
        assertEquals(DataOrigin.IMPUTED, result.points[1].origin)
        assertFalse(result.points[1].measured)
        assertEquals(0.5, result.originRatios[DataOrigin.MEASURED]!!, 0.0)
        assertEquals(0.5, result.originRatios[DataOrigin.IMPUTED]!!, 0.0)
        assertEquals(0.0, result.originRatios[DataOrigin.SYNTHETIC_ESTIMATE]!!, 0.0)
    }

    @Test fun longAndBoundaryGapsAreLowConfidenceStatisticalImputationNotModelOutput() {
        val signal = (compose(listOf(null, 1.0, null, null, null, 3.0, null)) as FullCoverageResult.Complete).signal
        assertEquals("session-median-index-domain-v1", signal.points[2].method)
        assertEquals(0.05, signal.points[2].provenance.confidence, 0.0)
        assertNull(signal.points[2].provenance.modelVersion)
        assertEquals(1, signal.points[2].nearestMeasuredDistance)
        assertEquals(TimestampSource.UNKNOWN, signal.points[2].provenance.timestampSource)
        assertEquals(SignalPurposeGate.FAIL, signal.hrv)
        assertEquals(SignalPurposeGate.FAIL, signal.rateEstimation)
        assertEquals(SignalPurposeGate.FAIL, signal.physiologicalConversion)
    }

    @Test fun refusesAllMissingAndUnmappedChannels() {
        assertTrue(compose(listOf(null, Double.NaN)) is FullCoverageResult.Unavailable)
        val unverified = FullCoverageSignalComposer.composeIndexDomain(listOf(1.0, null), provenance, true, true, false)
        assertTrue(unverified is FullCoverageResult.Complete)
        assertEquals(DataOrigin.IMPUTED, (unverified as FullCoverageResult.Complete).signal.points.last().origin)
    }

    @Test fun optInAndRepeatability() {
        val raw = listOf(1.0, null, 3.0)
        assertTrue(FullCoverageSignalComposer.composeIndexDomain(raw, provenance, false, true, true) is FullCoverageResult.Unavailable)
        assertEquals(compose(raw), compose(raw))
    }

    @Test fun gapDetectionDoesNotInventMissingIndices() {
        assertEquals(listOf(SignalGap(0, 1), SignalGap(2, 4)), GapDetector.detect(listOf(null, 1.0, Double.POSITIVE_INFINITY, null)))
    }
}
