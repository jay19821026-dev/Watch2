package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

class EcgCoreResearchEngineTest {
    private val sensorFingerprint = "a".repeat(64)
    private fun pairingEvidence(trainingCount: Int, holdoutCount: Int) = EcgCalibrationPairingEvidence(
        maximumPairSkewNanos = 1_000_000L,
        trainingRawTimestampsNanos = (0 until trainingCount).map { 1_000_000_000L + it * 10_000_000L },
        trainingReferenceTimestampsNanos = (0 until trainingCount).map { 1_000_000_000L + it * 10_000_000L },
        holdoutRawTimestampsNanos = (0 until holdoutCount).map { 10_000_000_000L + it * 10_000_000L },
        holdoutReferenceTimestampsNanos = (0 until holdoutCount).map { 10_000_000_000L + it * 10_000_000L }
    )

    private fun input(clock: ClockEvidence, mapped: Boolean = true) = EcgCoreInput(
        rawValues = List(1_500) { index ->
            listOf(100, 600, 1_100).sumOf { peak ->
                val offset = (index - peak).toDouble()
                exp(-(offset * offset) / (2.0 * 4.0 * 4.0)) * 1_000.0
            }
        },
        clock = clock, waveformMappingVerified = mapped, qualityScore = 80, wearQualified = true,
        waveformEncoding = EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING,
        sensorProfileFingerprint = sensorFingerprint
    )

    @Test fun peakCandidatesSupportBothPolaritiesAndApplyRefractoryPeriod() {
        val values = MutableList(850) { 0.0 }
        for (peak in listOf(100, 350, 600)) {
            for (offset in -12..12) {
                val amplitude = exp(-(offset * offset) / (2.0 * 4.0 * 4.0))
                values[peak + offset] = if (peak == 350) -amplitude else amplitude
            }
        }

        assertEquals(listOf(100, 350, 600), EcgCoreResearchEngine.detectPeaks(values, 500.0))
    }

    @Test fun broadPeriodicWaveIsNotCountedAsSharpRPeakCandidates() {
        val values = List(1_000) { index -> kotlin.math.sin(index * Math.PI * 2.0 / 250.0) }

        assertTrue(EcgCoreResearchEngine.detectPeaks(values, 500.0).isEmpty())
    }

    @Test fun missingClockUsesIndexAndNeverClaimsRateOrMv() {
        val result = EcgCoreResearchEngine.analyze(input(ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false)))
        assertEquals(EcgTimeline.NORMALIZED_INDEX, result.timeline)
        assertTrue(result.relativeWaveform.isNotEmpty())
        assertEquals(listOf(100, 600, 1_100), result.rPeakIndices)
        assertEquals(listOf(500, 500), result.rPeakIndexDistancesSamples)
        assertTrue(result.rrIntervalsMs.isEmpty())
        assertNull(result.heartRateBpm)
        assertNull(result.researchMv)
        assertTrue("SOURCE_TIMESTAMP_MISSING" in result.reasonCodes)
    }

    @Test fun unmappedOrLowQualityInputDoesNotEmitPeakCandidates() {
        val clock = ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false)
        val unmapped = EcgCoreResearchEngine.analyze(input(clock, mapped = false))
        val lowQuality = EcgCoreResearchEngine.analyze(input(clock).copy(qualityScore = 49))
        val notWorn = EcgCoreResearchEngine.analyze(input(clock).copy(wearQualified = false))

        assertTrue(unmapped.rPeakIndices.isEmpty())
        assertTrue("R_PEAK_CANDIDATES_BLOCKED_MAPPING_UNVERIFIED" in unmapped.reasonCodes)
        assertTrue(lowQuality.rPeakIndices.isEmpty())
        assertTrue("R_PEAK_CANDIDATES_BLOCKED_LOW_QUALITY" in lowQuality.reasonCodes)
        assertTrue(notWorn.rPeakIndices.isEmpty())
        assertTrue("R_PEAK_CANDIDATES_BLOCKED_WEAR_NOT_QUALIFIED" in notWorn.reasonCodes)
        listOf(unmapped, lowQuality, notWorn).forEach { result ->
            assertTrue(result.rrIntervalsMs.isEmpty())
            assertNull(result.heartRateBpm)
        }
    }

    @Test fun shortFiniteEcgPreservesRelativeWaveformWithoutDerivedPhysiology() {
        val clock = ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false)
        val samples = listOf(2.0, 4.0, 6.0, Double.NaN, 8.0)

        val result = EcgCoreResearchEngine.analyze(input(clock).copy(rawValues = samples))

        assertEquals(samples.size, result.relativeWaveform.size)
        assertTrue(result.relativeWaveform[0] < result.relativeWaveform[2])
        assertTrue(result.relativeWaveform[3].isNaN())
        assertEquals(EcgTimeline.NORMALIZED_INDEX, result.timeline)
        assertEquals("LIMITED_RESEARCH_OUTPUT", result.status)
        assertTrue("INSUFFICIENT_FINITE_SAMPLES" in result.reasonCodes)
        assertTrue("NONFINITE_SAMPLE_GAP" in result.reasonCodes)
        assertTrue(result.rPeakIndices.isEmpty())
        assertTrue(result.rrIntervalsMs.isEmpty())
        assertTrue(result.confidence < 0.8)
        assertNull(result.heartRateBpm)
        assertNull(result.researchMv)
    }

    @Test fun flatlineEcgPreservesZeroRelativeWaveformAndDoesNotInventResults() {
        val clock = ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false)

        val result = EcgCoreResearchEngine.analyze(input(clock).copy(rawValues = List(20) { 0.0 }))

        assertEquals(List(20) { 0.0 }, result.relativeWaveform)
        assertEquals("LIMITED_RESEARCH_OUTPUT", result.status)
        assertEquals(0.0, result.confidence, 0.0)
        assertTrue("FLATLINE" in result.reasonCodes)
        assertTrue(result.rPeakIndices.isEmpty())
        assertTrue(result.rrIntervalsMs.isEmpty())
        assertNull(result.heartRateBpm)
        assertNull(result.researchMv)
    }

    @Test fun noFiniteEcgEvidenceStillProducesNoWaveform() {
        val clock = ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false)

        val result = EcgCoreResearchEngine.analyze(input(clock).copy(rawValues = List(8) { Double.NaN }))

        assertTrue(result.relativeWaveform.isEmpty())
        assertTrue("NO_FINITE_SAMPLES" in result.reasonCodes)
    }

    @Test fun finiteSampleThresholdBoundaryKeepsShortOutputLimited() {
        val clock = ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false)
        val below = EcgCoreResearchEngine.analyze(input(clock).copy(rawValues = List(31) { it.toDouble() }))
        val at = EcgCoreResearchEngine.analyze(input(clock).copy(rawValues = List(32) { it.toDouble() }))

        assertEquals("LIMITED_RESEARCH_OUTPUT", below.status)
        assertEquals(31, below.relativeWaveform.size)
        assertEquals("RESEARCH_OUTPUT", at.status)
        assertEquals(32, at.relativeWaveform.size)
        assertNull(below.heartRateBpm)
        assertNull(at.heartRateBpm)
    }

    @Test fun extremeFiniteValuesNormalizeWithoutOverflow() {
        val clock = ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false)
        val samples = listOf(-Double.MAX_VALUE, 0.0, Double.MAX_VALUE)

        val result = EcgCoreResearchEngine.analyze(input(clock).copy(rawValues = samples))

        assertTrue(result.relativeWaveform.all(Double::isFinite))
        assertEquals(listOf(-500.0, 0.0, 500.0), result.relativeWaveform)
        assertEquals("LIMITED_RESEARCH_OUTPUT", result.status)
    }

    @Test fun sparseFiniteEvidenceRetainsFullIndexAndMissingMask() {
        val clock = ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false)
        val samples = MutableList(128) { Double.NaN }.apply {
            this[10] = 2.0
            this[11] = 4.0
        }

        val result = EcgCoreResearchEngine.analyze(input(clock).copy(rawValues = samples))

        assertEquals(samples.size, result.relativeWaveform.size)
        assertTrue(result.relativeWaveform.take(10).all(Double::isNaN))
        assertEquals(2, result.relativeWaveform.count(Double::isFinite))
        assertEquals("LIMITED_RESEARCH_OUTPUT", result.status)
        assertTrue(result.confidence < 0.02)
        assertTrue(result.rPeakIndices.isEmpty())
        assertNull(result.heartRateBpm)
        assertNull(result.researchMv)
    }

    @Test fun calibrationRequiresIndependentValidationAndProfileId() {
        assertFalse(EcgCoreResearchEngine.validateCalibration(null).allowed)
        val raw = (0 until 10).map(Int::toDouble)
        val reference = raw.map { it * 0.002 + 0.15 }
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(raw, reference, raw, reference,
            "p", "same", "same", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.01, pairingEvidence(raw.size, raw.size)))
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(raw, reference, raw, reference.map { it + 0.2 },
            "p", "train", "holdout", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.01, pairingEvidence(raw.size, raw.size)))
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(raw, reference, raw, reference,
            "p", "train", "holdout", "unknown", 0.01, pairingEvidence(raw.size, raw.size)))
        val invalidTiming = pairingEvidence(raw.size, raw.size).copy(
            maximumPairSkewNanos = 0L
        )
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(raw, reference, raw, reference,
            "p", "train", "holdout", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.01, invalidTiming))
        val excessiveClockOffset = pairingEvidence(raw.size, raw.size).copy(
            maximumPairSkewNanos = 1_000_000_000L,
            trainingReferenceTimestampsNanos = pairingEvidence(raw.size, raw.size)
                .trainingReferenceTimestampsNanos.map { it + 100_000_000L },
            holdoutReferenceTimestampsNanos = pairingEvidence(raw.size, raw.size)
                .holdoutReferenceTimestampsNanos.map { it + 100_000_000L }
        )
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(raw, reference, raw, reference,
            "p", "train", "holdout", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.01, excessiveClockOffset))
        val overlappingCapture = pairingEvidence(raw.size, raw.size).let { valid ->
            valid.copy(
                holdoutRawTimestampsNanos = valid.trainingRawTimestampsNanos,
                holdoutReferenceTimestampsNanos = valid.trainingReferenceTimestampsNanos
            )
        }
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(raw, reference, raw, reference,
            "p", "train", "holdout", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.01, overlappingCapture))
        val excessivePairSkew = pairingEvidence(raw.size, raw.size).copy(
            holdoutReferenceTimestampsNanos = pairingEvidence(raw.size, raw.size)
                .holdoutReferenceTimestampsNanos.map { it + 2_000_000L }
        )
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(raw, reference, raw, reference,
            "p", "train", "holdout", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.01, excessivePairSkew))
        assertTrue(EcgCoreResearchEngine.validateCalibration(profile()).allowed)
    }

    @Test fun pairedReferenceDataCreatesValidatedProfile() {
        assertEquals("verified_ecg_samples_v1", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING)
        val trainRaw = (0 until 10).map { it.toDouble() }
        val trainReference = trainRaw.map { it * 0.002 + 0.15 }
        val holdoutRaw = (3..8).map { it.toDouble() + 0.5 }
        val holdoutReference = holdoutRaw.map { it * 0.002 + 0.15 }
        val fitted = EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw, trainReference, holdoutRaw, holdoutReference, "paired-ecg",
            "train-session", "holdout-session", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.0001,
            pairingEvidence(trainRaw.size, holdoutRaw.size), sensorFingerprint
        )
        assertNotNull(fitted)
        assertTrue(EcgCoreResearchEngine.validateCalibration(fitted).allowed)
        assertEquals("RESEARCH_HOLDOUT_GATE_PASSED", EcgCoreResearchEngine.validateCalibration(fitted).reason)
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw, trainReference, holdoutRaw, holdoutReference.map { it + 0.1 }, "paired-ecg",
            "train-session", "holdout-session", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.0001,
            pairingEvidence(trainRaw.size, holdoutRaw.size), sensorFingerprint
        ))
    }

    @Test fun calibrationRejectsRepeatedPairedSamplesEvenWhenTheirTimestampsAreShifted() {
        val raw = (0 until 10).map(Int::toDouble)
        val reference = raw.map { it * 0.002 + 0.15 }
        val validTimes = pairingEvidence(raw.size, raw.size)
        val shiftedHoldoutTimes = validTimes.copy(
            holdoutRawTimestampsNanos = validTimes.holdoutRawTimestampsNanos.map { it + 1_000_000_000_000L },
            holdoutReferenceTimestampsNanos = validTimes.holdoutReferenceTimestampsNanos.map { it + 1_000_000_000_000L }
        )

        val profile = EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw = raw,
            trainReferenceMv = reference,
            holdoutRaw = raw.toList(),
            holdoutReferenceMv = reference.toList(),
            profileId = "replayed-capture",
            trainingDatasetId = "training-id",
            holdoutDatasetId = "different-holdout-id",
            inputEncoding = EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING,
            maxHoldoutRmseMv = 0.01,
            pairingEvidence = shiftedHoldoutTimes,
            sensorProfileFingerprint = sensorFingerprint
        )

        assertNull(profile)
    }

    @Test fun calibrationRejectsReversedReplayOfPairedSamples() {
        val raw = (0 until 10).map(Int::toDouble)
        val reference = raw.map { it * 0.002 + 0.15 }

        val profile = EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw = raw,
            trainReferenceMv = reference,
            holdoutRaw = raw.reversed(),
            holdoutReferenceMv = reference.reversed(),
            profileId = "reversed-replay",
            trainingDatasetId = "training-id",
            holdoutDatasetId = "different-holdout-id",
            inputEncoding = EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING,
            maxHoldoutRmseMv = 0.01,
            pairingEvidence = pairingEvidence(raw.size, raw.size),
            sensorProfileFingerprint = sensorFingerprint
        )

        assertNull(profile)
    }

    @Test fun verifiedClockAllowsResearchRrButNotUnvalidatedMv() {
        val timestamps = (1..601).map { it.toLong() * 2_000_000L }
        val result = EcgCoreResearchEngine.analyze(input(ClockEvidence(
            timestamps, timestamps, 500.0, EcgCoreResearchEngine.HAL_SAMPLE_RATE_SOURCE, true, true, true, true
        )))
        assertEquals(EcgTimeline.SENSOR, result.timeline)
        assertEquals(EcgCoreResearchEngine.MIN_RR_INTERVALS_FOR_RESEARCH_HR, result.rrIntervalsMs.size)
        assertNotNull(result.heartRateBpm)
        assertNull(result.researchMv)
    }

    @Test fun verifiedSensorTimestampsWithoutSampleRateProvenanceDoNotUnlockRate() {
        val timestamps = (1..601).map { it.toLong() * 2_000_000L }
        val result = EcgCoreResearchEngine.analyze(input(ClockEvidence(
            timestamps, timestamps, 500.0, null, true, true, true, true
        )))

        assertEquals(EcgTimeline.SENSOR, result.timeline)
        assertEquals(listOf(100, 600, 1_100), result.rPeakIndices)
        assertTrue(result.rrIntervalsMs.isEmpty())
        assertNull(result.heartRateBpm)
        assertTrue("SAMPLE_RATE_SOURCE_MISSING" in result.reasonCodes)
    }

    @Test fun verifiedSensorTimestampsWithUnsupportedSampleRateSourceDoNotUnlockRate() {
        val timestamps = (1..601).map { it.toLong() * 2_000_000L }
        val result = EcgCoreResearchEngine.analyze(input(ClockEvidence(
            timestamps, timestamps, 500.0, "ai-estimated-rate", true, true, true, true
        )))

        assertEquals(listOf(100, 600, 1_100), result.rPeakIndices)
        assertTrue(result.rrIntervalsMs.isEmpty())
        assertNull(result.heartRateBpm)
        assertTrue("SAMPLE_RATE_SOURCE_UNSUPPORTED" in result.reasonCodes)
    }

    @Test fun nonFiniteSampleGapKeepsContiguousSegmentRrButNotSparseHeartRateOrMv() {
        val timestamps = (1..1_501).map { it.toLong() * 2_000_000L }
        val samples = input(ClockEvidence(
            timestamps, timestamps, 500.0, EcgCoreResearchEngine.HAL_SAMPLE_RATE_SOURCE, true, true, true, true
        )).rawValues.toMutableList().apply { this[250] = Double.NaN }

        val result = EcgCoreResearchEngine.analyze(
            input(ClockEvidence(timestamps, timestamps, 500.0, EcgCoreResearchEngine.HAL_SAMPLE_RATE_SOURCE, true, true, true, true))
                .copy(rawValues = samples),
            profile()
        )

        assertEquals(listOf(1_000.0), result.rrIntervalsMs)
        assertNull(result.heartRateBpm)
        assertNull(result.researchMv)
        assertEquals(EcgTimeline.NORMALIZED_INDEX, result.timeline)
        assertEquals(listOf(100, 600, 1_100), result.rPeakIndices)
        assertTrue("NONFINITE_SAMPLE_GAP" in result.reasonCodes)
        assertTrue("RR_FROM_CONTIGUOUS_SEGMENTS_ONLY" in result.reasonCodes)
    }

    @Test fun missingTailDoesNotDiscardCompleteTrustedRrIntervalsBeforeGap() {
        val timestamps = (1..1_501).map { it.toLong() * 2_000_000L }
        val clock = ClockEvidence(timestamps, timestamps, 500.0, EcgCoreResearchEngine.HAL_SAMPLE_RATE_SOURCE, true, true, true, true)
        val waveform = input(clock).rawValues.toMutableList().apply { this[1_250] = Double.NaN }

        val result = EcgCoreResearchEngine.analyze(input(clock).copy(rawValues = waveform))

        assertEquals(listOf(1_000.0, 1_000.0), result.rrIntervalsMs)
        assertEquals(60.0, result.heartRateBpm!!, 0.01)
        assertEquals(EcgTimeline.NORMALIZED_INDEX, result.timeline)
        assertNull(result.researchMv)
        assertTrue("RR_FROM_CONTIGUOUS_SEGMENTS_ONLY" in result.reasonCodes)
    }

    @Test fun nonFiniteGapsPreservePeakIndicesButNeverCreateCrossGapDistances() {
        val waveform = input(ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false)).rawValues
            .toMutableList().apply { this[750] = Double.NaN }
        val result = EcgCoreResearchEngine.analyze(
            input(ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false))
                .copy(rawValues = waveform)
        )

        assertEquals(waveform.size, result.relativeWaveform.size)
        assertTrue(result.relativeWaveform[750].isNaN())
        assertTrue(result.rPeakIndices.containsAll(listOf(100, 600, 1_100)))
        assertEquals(listOf(500), result.rPeakIndexDistancesSamples)
        assertTrue(result.rrIntervalsMs.isEmpty())
        assertNull(result.heartRateBpm)
    }

    @Test fun estimatedClockOrUnverifiedMappingNeverUnlocksRate() {
        val timestamps = (1..601).map { it.toLong() * 2_000_000L }
        val estimated = EcgCoreResearchEngine.analyze(input(ClockEvidence(
            emptyList(), timestamps, 500.0, "sensor_hal_type_sampling", true, true, true, false
        )))
        assertEquals(EcgTimeline.ESTIMATED, estimated.timeline)
        assertTrue(estimated.rrIntervalsMs.isEmpty())
        assertNull(estimated.heartRateBpm)

        val unverifiedMapping = EcgCoreResearchEngine.analyze(
            input(ClockEvidence(timestamps, timestamps, 500.0, "sensor_hal_type_sampling", true, true, true, true), mapped = false)
        )
        assertTrue(unverifiedMapping.rrIntervalsMs.isEmpty())
        assertNull(unverifiedMapping.heartRateBpm)
    }

    @Test fun matchingValidatedReferenceProfileProvidesResearchMappingButNotRateEvidence() {
        val withMapping = EcgCoreResearchEngine.analyze(input(
            ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false), mapped = true
        ), profile())
        assertNotNull(withMapping.researchMv)
        val withoutMapping = EcgCoreResearchEngine.analyze(input(
            ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false), mapped = false
        ), profile())
        assertNotNull(withoutMapping.researchMv)
        assertTrue(withoutMapping.rrIntervalsMs.isEmpty())
        assertNull(withoutMapping.heartRateBpm)
        assertTrue(withoutMapping.rrIntervalsMs.isEmpty())
        assertNull(withoutMapping.heartRateBpm)

        val referenceBound = EcgCoreResearchEngine.analyze(
            input(ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false), mapped = true)
                .copy(waveformEncoding = EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING),
            profile()
        )
        assertNotNull(referenceBound.researchMv)

        val verifiedMapping = EcgCoreResearchEngine.analyze(
            input(ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false), mapped = true),
            profile()
        )
        assertNotNull(verifiedMapping.researchMv)

        val wrongEncoding = EcgCoreResearchEngine.analyze(
            input(ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false), mapped = true)
                .copy(waveformEncoding = "different_raw_format"),
            profile()
        )
        assertNull(wrongEncoding.researchMv)
        assertTrue("WAVEFORM_ENCODING_MISMATCH" in wrongEncoding.reasonCodes)

        val missingFingerprint = EcgCoreResearchEngine.analyze(
            input(ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false))
                .copy(sensorProfileFingerprint = null),
            profile()
        )
        assertNull(missingFingerprint.researchMv)
        assertTrue("SENSOR_PROFILE_MISMATCH" in missingFingerprint.reasonCodes)
    }

    @Test fun calibrationRejectsRawValuesOutsideTrainingDomain() {
        val trainRaw = (-5..5).map(Int::toDouble)
        val profile = EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw, trainRaw.map { it * 0.02 },
            listOf(-4.5, -3.5, -2.5, -1.5, -0.5), listOf(-0.09, -0.07, -0.05, -0.03, -0.01),
            "range-profile", "train", "holdout", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.001,
            pairingEvidence(trainRaw.size, 5), sensorFingerprint
        )!!
        val result = EcgCoreResearchEngine.analyze(input(
            ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false)
        ), profile)
        assertNull(result.researchMv)
        assertTrue("RAW_VALUE_OUTSIDE_TRAINING_DOMAIN" in result.reasonCodes)
    }

    @Test fun researchMvRejectsValuesThatOverflowDownstreamFloatWaveform() {
        val slope = java.lang.Math.scalb(1.0, 128)
        val trainRaw = listOf(-2.0, -1.0, 0.0, 1.0, 2.0)
        val trainMv = trainRaw.map { it * slope }
        val holdoutRaw = listOf(-1.5, -0.5, 0.5, 1.5, 1.75)
        val profile = EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw, trainMv, holdoutRaw, holdoutRaw.map { it * slope },
            "float-overflow", "train", "holdout", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.01,
            pairingEvidence(trainRaw.size, holdoutRaw.size), sensorFingerprint
        )!!
        val result = EcgCoreResearchEngine.analyze(
            input(ClockEvidence(emptyList(), emptyList(), null, null, true, true, true, false))
                .copy(rawValues = List(64) { if (it % 16 == 8) 1.0 else 0.0 }),
            profile
        )

        assertNull(result.researchMv)
        assertTrue("RESEARCH_MV_FLOAT_RANGE_OVERFLOW" in result.reasonCodes)
    }

    @Test fun calibrationRejectsPermissiveRmseAndHoldoutExtrapolation() {
        val trainRaw = (-10..10 step 2).map(Int::toDouble)
        val trainMv = trainRaw.map { it * 0.01 }
        val inRangeRaw = listOf(-8.0, -6.0, -4.0, 4.0, 6.0)
        val inRangeMv = inRangeRaw.map { it * 0.01 }
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw, trainMv, inRangeRaw, inRangeMv,
            "permissive", "train", "holdout", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.02,
            pairingEvidence(trainRaw.size, inRangeRaw.size), sensorFingerprint
        ))
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw, trainMv, listOf(-12.0, -8.0, -4.0, 4.0, 8.0),
            listOf(-0.12, -0.08, -0.04, 0.04, 0.08),
            "extrapolated", "train", "holdout", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.0001,
            pairingEvidence(trainRaw.size, 5), sensorFingerprint
        ))
    }

    @Test fun calibrationRejectsUserSelectedAbsoluteRmseAboveResearchCeiling() {
        val trainRaw = (0..10).map(Int::toDouble)
        val trainMv = trainRaw.map { it }
        val holdoutRaw = (1..5).map(Int::toDouble)
        val holdoutMv = holdoutRaw.map { it }
        assertNull(EcgCoreResearchEngine.fitValidatedCalibration(
            trainRaw, trainMv, holdoutRaw, holdoutMv,
            "wide-range-permissive", "train", "holdout", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.5,
            pairingEvidence(trainRaw.size, holdoutRaw.size), sensorFingerprint
        ))
    }

    private fun profile() : EcgCalibrationProfile {
        val raw = (-1_000..1_000 step 250).map(Int::toDouble)
        val holdout = (-875..875 step 250).map(Int::toDouble)
        return EcgCoreResearchEngine.fitValidatedCalibration(
            raw, raw.map { it * 0.002 + 0.15 },
            holdout, holdout.map { it * 0.002 + 0.15 },
            "profile", "train-session", "holdout-session", EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING, 0.01,
            pairingEvidence(raw.size, holdout.size), sensorFingerprint
        )!!
    }
}
