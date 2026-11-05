package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.*
import org.junit.Test

class SensorEvidenceTest {
    private val clock = ClockEvidence(
        List(8) { 0L }, List(8) { (it + 1) * 1_000_000L },
        20.0, "paired engineering reference", true, true, true, false
    )
    private val provenance = SensorProvenance(
        "session", "ppg", "raw session reference", "capture-v1", null,
        0.8, "RESEARCH", TimestampSource.UNKNOWN, null, 1L
    )

    @Test fun callbacksNeverBecomeSensorClock() {
        val result = SensorClockClassifier.classify(clock)
        assertEquals(TimestampSource.ESTIMATED, result.timestampSource)
        assertFalse(result.sourceClockTrustworthy)
        assertEquals(TimestampSource.UNKNOWN, SensorClockClassifier.classify(clock.copy(nominalSampleRateHz = null)).timestampSource)
    }

    @Test fun invalidSourceTimestampsStayUnknownEvenWithVerificationFlag() {
        assertEquals(TimestampSource.UNKNOWN, SensorClockClassifier.classify(
            clock.copy(sourceClockVerified = true, sampleRateSource = null)
        ).timestampSource)
    }

    @Test fun ecgDecoderLayoutAndOrderedCallbacksPermitEstimatedButNotSensorClock() {
        val sourceTimestamps = listOf(0L, 0L, 0L)
        val callbacks = listOf(1_000_000L, 21_000_000L, 41_000_000L)
        val sampleCounts = listOf(0, 0, 0)
        val ordering = EcgClockEvidencePolicy.eventOrderingVerified(sourceTimestamps, callbacks)
        val samplesKnown = EcgClockEvidencePolicy.samplesPerEventKnown(
            sampleCounts, eventCount = 3, decodedEventCount = 3, decodedSampleCount = 30, decodedSamplesPerEvent = 10
        )

        val result = SensorClockClassifier.classify(
            ClockEvidence(
                sourceTimestamps, callbacks, 500.0, "sensor_hal_type_sampling", ordering,
                samplesKnown, sessionOrderingVerified = true, sourceClockVerified = false
            )
        )

        assertEquals(TimestampSource.ESTIMATED, result.timestampSource)
        assertFalse(result.sourceClockTrustworthy)
        val flaggedButUntrusted = SensorClockClassifier.classify(
            ClockEvidence(sourceTimestamps, callbacks, 500.0, "sensor_hal_type_sampling", ordering,
                samplesKnown, sessionOrderingVerified = true, sourceClockVerified = true)
        )
        assertEquals(TimestampSource.ESTIMATED, flaggedButUntrusted.timestampSource)
        assertFalse(flaggedButUntrusted.sourceClockTrustworthy)
    }

    @Test fun ecgDecodedSampleCountMustMatchEventLayout() {
        assertFalse(EcgClockEvidencePolicy.samplesPerEventKnown(
            listOf(0, 0), eventCount = 2, decodedEventCount = 2, decodedSampleCount = 19, decodedSamplesPerEvent = 10
        ))
        assertFalse(EcgClockEvidencePolicy.samplesPerEventKnown(
            listOf(10, 10, 10), eventCount = 3, decodedEventCount = 2, decodedSampleCount = 20, decodedSamplesPerEvent = 10
        ))
        assertFalse(EcgClockEvidencePolicy.samplesPerEventKnown(
            listOf(10, 9, 10), eventCount = 3, decodedEventCount = 3, decodedSampleCount = 30, decodedSamplesPerEvent = 10
        ))
        assertTrue(EcgClockEvidencePolicy.samplesPerEventKnown(
            listOf(10, 10, 10), eventCount = 3, decodedEventCount = 3, decodedSampleCount = 30, decodedSamplesPerEvent = 10
        ))
        assertFalse(EcgClockEvidencePolicy.eventOrderingVerified(listOf(0L, 0L), listOf(10L, 9L)))
    }

    @Test fun shortGapProducesSeparateDerivedPoints() {
        val raw = listOf(1.0, null, 3.0)
        val result = PpgShortGapReconstruction.reconstruct(raw, clock, 90.0, 0.0, provenance)
        assertEquals(2.0, result.points.single().value, 0.0)
        assertEquals(DataOrigin.RECONSTRUCTED, result.points.single().origin)
        assertEquals(TimestampSource.ESTIMATED, result.points.single().provenance.timestampSource)
        assertNull(raw[1])
        assertTrue(result.points.single().provenance.provenance.contains("raw indices"))
    }

    @Test fun rejectsUnknownRateLongGapLowQualityAndUnknownMotion() {
        assertEquals(setOf("UNKNOWN_SAMPLE_RATE"), PpgShortGapReconstruction.reconstruct(
            listOf(1.0, null, 3.0), clock.copy(nominalSampleRateHz = null), 90.0, 0.0, provenance
        ).reasonCodes)
        assertEquals(setOf("LONG_GAP"), PpgShortGapReconstruction.reconstruct(
            listOf(1.0, null, null, null, 3.0), clock, 90.0, 0.0, provenance
        ).reasonCodes)
        assertEquals(setOf("LOW_QUALITY"), PpgShortGapReconstruction.reconstruct(
            listOf(1.0, null, 3.0), clock, 20.0, 0.0, provenance
        ).reasonCodes)
        assertEquals(setOf("MOTION_NOT_ACCEPTABLE_OR_UNKNOWN"), PpgShortGapReconstruction.reconstruct(
            listOf(1.0, null, 3.0), clock, 90.0, null, provenance
        ).reasonCodes)
    }

    @Test fun neverExtrapolatesBoundaryGaps() {
        val result = PpgShortGapReconstruction.reconstruct(listOf(null, 1.0, null), clock, 90.0, 0.0, provenance)
        assertTrue(result.points.isEmpty())
        assertEquals(setOf("UNBOUNDED_GAP"), result.reasonCodes)
    }
}
