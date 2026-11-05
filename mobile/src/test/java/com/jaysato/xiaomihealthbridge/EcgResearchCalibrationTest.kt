package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EcgResearchCalibrationTest {
    @Test
    fun malformedPersistedCalibrationScalarsAreRejected() {
        assertNull(EcgResearchProfileStore.parseFiniteStoredScalar(null))
        assertNull(EcgResearchProfileStore.parseFiniteStoredScalar("corrupt"))
        assertNull(EcgResearchProfileStore.parseFiniteStoredScalar("NaN"))
        assertNull(EcgResearchProfileStore.parseFiniteStoredScalar("Infinity"))
        assertEquals(0.0025, EcgResearchProfileStore.parseFiniteStoredScalar("0.0025")!!, 1e-12)
    }

    @Test
    fun researchProfileRequiresFivePairsAndSuccessfulHeldOutValidation() {
        val threePointFit = EcgResearchCalibration.fitRawToMillivolts(
            listOf(100.0, 200.0, 300.0), listOf(0.5, 1.0, 1.5)
        )
        val fivePointFit = EcgResearchCalibration.fitRawToMillivolts(
            listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            listOf(0.5, 1.0, 1.5, 2.0, 2.5)
        )
        val validation = EcgResearchCalibration.validateWithLeaveOneOut(
            listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            listOf(0.5, 1.0, 1.5, 2.0, 2.5)
        )
        assertFalse(EcgResearchCalibration.canSaveResearchProfile(
            threePointFit,
            EcgResearchCalibration.LeaveOneOutValidation(null, 0, false, "too few")
        ))
        assertTrue(EcgResearchCalibration.canSaveResearchProfile(fivePointFit, validation))
        assertFalse(EcgResearchCalibration.canSaveResearchProfile(
            fivePointFit,
            validation.copy(usable = false)
        ))
    }

    @Test
    fun fitsReferenceAmplitudeWithoutCallingItOemCalibration() {
        val fit = EcgResearchCalibration.fitRawToMillivolts(
            raw = listOf(100.0, 200.0, 300.0),
            referenceMv = listOf(0.5, 1.0, 1.5)
        )
        assertTrue(fit.usable)
        assertEquals(0.005, fit.slopeMvPerRaw, 1e-9)
        assertEquals(0.0, fit.offsetMv, 1e-9)
    }

    @Test
    fun rejectsIncompleteOrDegenerateReferencePairs() {
        assertFalse(EcgResearchCalibration.fitRawToMillivolts(listOf(1.0, 2.0, 3.0), listOf(1.0, 2.0)).usable)
        assertFalse(EcgResearchCalibration.fitRawToMillivolts(listOf(1.0, 2.0, 3.0), listOf(1.0, 1.0, 1.0)).usable)
        assertFalse(EcgResearchCalibration.fitRawToMillivolts(listOf(1.0, Double.NaN, 3.0), listOf(1.0, 2.0, 3.0)).usable)
        val tooMany = List(EcgResearchCalibration.MAX_RESEARCH_CALIBRATION_POINTS + 1) { it.toDouble() }
        assertFalse(EcgResearchCalibration.fitRawToMillivolts(tooMany, tooMany).usable)
        assertFalse(EcgResearchCalibration.validateWithLeaveOneOut(tooMany, tooMany).usable)
    }

    @Test
    fun validatesExternalAmplitudeWithHeldOutPairs() {
        val validation = EcgResearchCalibration.validateWithLeaveOneOut(
            raw = listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            referenceMv = listOf(0.5, 1.0, 1.5, 2.0, 2.5)
        )
        assertTrue(validation.usable)
        assertEquals(0.0, validation.rmseMv!!, 1e-9)
        assertEquals(5, validation.heldOutPoints)
    }

    @Test
    fun validatesAgainstASeparateHoldoutSet() {
        val validation = EcgResearchCalibration.validateIndependentHoldout(
            trainRaw = listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            trainReferenceMv = listOf(0.5, 1.0, 1.5, 2.0, 2.5),
            holdoutRaw = listOf(150.0, 350.0, 450.0, 250.0, 400.0),
            holdoutReferenceMv = listOf(0.75, 1.75, 2.25, 1.25, 2.0),
            maximumAllowedRmseMv = 0.01
        )
        assertTrue(validation.usable)
        assertTrue(validation.passed)
        assertEquals(0.0, validation.rmseMv!!, 1e-9)
        assertEquals(5, validation.heldOutPoints)
    }

    @Test
    fun independentHoldoutMustMeetPredefinedRmseTolerance() {
        val validation = EcgResearchCalibration.validateIndependentHoldout(
            trainRaw = listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            trainReferenceMv = listOf(0.5, 1.0, 1.5, 2.0, 2.5),
            holdoutRaw = listOf(150.0, 350.0, 450.0, 250.0, 400.0),
            holdoutReferenceMv = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
            maximumAllowedRmseMv = 0.01
        )
        assertTrue(validation.usable)
        assertFalse(validation.passed)
        assertTrue(validation.rmseMv!! > validation.maximumAllowedRmseMv!!)
    }

    @Test
    fun independentHoldoutUsesAppCeilingEvenWhenReferenceRangeIsWide() {
        val raw = listOf(0.0, 1.0, 2.0, 3.0, 4.0)
        val validation = EcgResearchCalibration.validateIndependentHoldout(
            trainRaw = raw,
            trainReferenceMv = raw,
            holdoutRaw = raw,
            holdoutReferenceMv = raw,
            maximumAllowedRmseMv = 0.1
        )

        assertFalse(validation.usable)
        assertTrue(validation.note.contains("0.05 mV"))
    }

    @Test
    fun independentHoldoutRejectsOversizedInput() {
        val oversized = List(EcgResearchCalibration.MAX_RESEARCH_CALIBRATION_POINTS + 1) { it.toDouble() }
        val validation = EcgResearchCalibration.validateIndependentHoldout(
            trainRaw = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
            trainReferenceMv = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
            holdoutRaw = oversized,
            holdoutReferenceMv = oversized,
            maximumAllowedRmseMv = 0.01
        )
        assertFalse(validation.usable)
        assertFalse(validation.passed)
    }

    @Test
    fun independentHoldoutRejectsInsufficientOrInvalidPairs() {
        assertFalse(EcgResearchCalibration.validateIndependentHoldout(
            trainRaw = listOf(1.0, 2.0, 3.0, 4.0),
            trainReferenceMv = listOf(1.0, 2.0, 3.0, 4.0),
            holdoutRaw = listOf(1.0, 2.0, 3.0, 4.0),
            holdoutReferenceMv = listOf(1.0, 2.0, 3.0, 4.0),
            maximumAllowedRmseMv = 0.01
        ).usable)
        assertFalse(EcgResearchCalibration.validateIndependentHoldout(
            trainRaw = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
            trainReferenceMv = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
            holdoutRaw = listOf(1.0, Double.NaN, 3.0, 4.0, 5.0),
            holdoutReferenceMv = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
            maximumAllowedRmseMv = 0.01
        ).usable)
    }

    @Test
    fun independentHoldoutRejectsUserRelaxedRmseAndRawExtrapolation() {
        val relaxed = EcgResearchCalibration.validateIndependentHoldout(
            trainRaw = listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            trainReferenceMv = listOf(0.5, 1.0, 1.5, 2.0, 2.5),
            holdoutRaw = listOf(150.0, 250.0, 350.0, 450.0, 400.0),
            holdoutReferenceMv = listOf(0.75, 1.25, 1.75, 2.25, 2.0),
            maximumAllowedRmseMv = 0.2
        )
        assertFalse(relaxed.usable)
        assertTrue(relaxed.note.contains("不得超過"))

        val extrapolated = EcgResearchCalibration.validateIndependentHoldout(
            trainRaw = listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            trainReferenceMv = listOf(0.5, 1.0, 1.5, 2.0, 2.5),
            holdoutRaw = listOf(150.0, 250.0, 350.0, 450.0, 550.0),
            holdoutReferenceMv = listOf(0.75, 1.25, 1.75, 2.25, 2.75),
            maximumAllowedRmseMv = 0.01
        )
        assertFalse(extrapolated.usable)
        assertTrue(extrapolated.note.contains("拒絕外插"))
    }

    @Test
    fun researchMvProfileRequiresIndependentPassingHoldout() {
        val unverified = EcgResearchProfileStore.Profile(
            slopeMvPerRaw = 0.005,
            offsetMv = 0.0,
            rmseMv = 0.0,
            validationRmseMv = 0.0,
            maximumAllowedRmseMv = 0.01,
            points = 5,
            validatedPoints = 5,
            independentlyValidated = false,
            inputEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING,
            source = "test reference",
            createdAtMillis = 1L,
            referenceInstrument = "reference ECG simulator",
            protocolId = "protocol-1",
            sensorProfileFingerprint = "a".repeat(64),
            trainingCaptureId = "training-1",
            holdoutCaptureId = "holdout-1",
            trainingRawMinimum = 0.0,
            trainingRawMaximum = 1000.0
            ,trainingRaw = listOf(0.0, 250.0, 500.0, 750.0, 1000.0)
            ,trainingReferenceMv = listOf(0.0, 1.25, 2.5, 3.75, 5.0)
            ,holdoutRaw = listOf(100.0, 300.0, 500.0, 700.0, 900.0)
            ,holdoutReferenceMv = listOf(0.5, 1.5, 2.5, 3.5, 4.5)
            ,maximumPairSkewNanos = 5_000_000L
            ,trainingRawTimestampsNanos = listOf(1_000_000_000L, 2_000_000_000L, 3_000_000_000L, 4_000_000_000L, 5_000_000_000L)
            ,trainingReferenceTimestampsNanos = listOf(1_000_000_000L, 2_000_000_000L, 3_000_000_000L, 4_000_000_000L, 5_000_000_000L)
            ,holdoutRawTimestampsNanos = listOf(10_000_000_000L, 11_000_000_000L, 12_000_000_000L, 13_000_000_000L, 14_000_000_000L)
            ,holdoutReferenceTimestampsNanos = listOf(10_000_000_000L, 11_000_000_000L, 12_000_000_000L, 13_000_000_000L, 14_000_000_000L)
        )
        assertFalse(unverified.isEligibleForResearchMv)
        assertTrue(unverified.apply(listOf(100f, 200f)).isEmpty())

        val failedHoldout = unverified.copy(
            independentlyValidated = true,
            validationRmseMv = 0.02
        )
        assertFalse(failedHoldout.isEligibleForResearchMv)
        assertTrue(failedHoldout.apply(listOf(100f, 200f)).isEmpty())

        val validated = unverified.copy(independentlyValidated = true, profileId = "validated-profile")
        assertTrue(validated.isEligibleForResearchMv)
        assertFalse(validated.copy(slopeMvPerRaw = validated.slopeMvPerRaw * 2.0).isEligibleForResearchMv)
        assertFalse(validated.copy(offsetMv = validated.offsetMv + 0.1).isEligibleForResearchMv)
        assertFalse(validated.copy(validationRmseMv = validated.validationRmseMv + 0.1).isEligibleForResearchMv)
        assertTrue(validated.isBoundToSensorProfile("a".repeat(64)))
        assertFalse(validated.isBoundToSensorProfile(null))
        assertFalse(validated.isBoundToSensorProfile("b".repeat(64)))
        assertFalse(validated.isBoundToSensorProfile("a".repeat(64), "other-profile"))
        assertTrue(EcgResearchProfileActivationPolicy.matches(
            validated, "ecg_external_reference_mv", "validated-profile", "a".repeat(64)
        ))
        assertFalse(EcgResearchProfileActivationPolicy.matches(
            validated, "ecg_external_reference_mv", "other-profile", "a".repeat(64)
        ))
        assertFalse(EcgResearchProfileActivationPolicy.matches(
            validated, "ecg_external_reference_mv", "validated-profile", "b".repeat(64)
        ))
        assertFalse(EcgResearchProfileActivationPolicy.matches(
            validated, "research_signal_unscaled", "validated-profile", "a".repeat(64)
        ))
        assertTrue(com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine
            .validateCalibration(validated.toCoreCalibrationProfile()).allowed)
        assertEquals(listOf(0.5f, 1.0f), validated.apply(listOf(100f, 200f)))
        assertTrue(validated.apply(listOf(100f, 2000f)).isEmpty())
        assertFalse(validated.copy(holdoutCaptureId = "training-1").isEligibleForResearchMv)
        assertFalse(validated.copy(referenceInstrument = "").isEligibleForResearchMv)
        assertFalse(validated.copy(protocolId = "").isEligibleForResearchMv)
        assertFalse(validated.copy(maximumPairSkewNanos = 0L).isEligibleForResearchMv)
        assertFalse(validated.copy(trainingReferenceTimestampsNanos = emptyList()).isEligibleForResearchMv)
        assertFalse(validated.copy(holdoutRawTimestampsNanos = validated.trainingRawTimestampsNanos).isEligibleForResearchMv)
        assertFalse(validated.copy(holdoutReferenceTimestampsNanos =
            validated.holdoutReferenceTimestampsNanos.map { it + 10_000_000L }).isEligibleForResearchMv)
        assertEquals(listOf(0.5f, 1.0f), validated.apply(listOf(100f, 200f)))
        assertTrue(validated.apply(listOf(100f, 1001f)).isEmpty())
        assertTrue(validated.apply(listOf(100f, Float.NaN)).isEmpty())
        assertFalse(validated.copy(slopeMvPerRaw = 0.006).isEligibleForResearchMv)
        assertFalse(validated.copy(holdoutReferenceMv = validated.holdoutReferenceMv.map { it + 0.1 })
            .isEligibleForResearchMv)
    }

    @Test
    fun independentValidationUsesSeparateHoldoutAndPredefinedThreshold() {
        val trainRaw = listOf(100.0, 200.0, 300.0, 400.0, 500.0)
        val trainMv = listOf(0.5, 1.0, 1.5, 2.0, 2.5)
        val holdoutRaw = listOf(150.0, 350.0, 450.0, 250.0, 400.0)
        val passing = EcgResearchCalibration.validateIndependentHoldout(
            trainRaw, trainMv, holdoutRaw,
            listOf(0.75, 1.75, 2.25, 1.25, 2.0), 0.01
        )
        assertTrue(passing.passed)

        val failing = EcgResearchCalibration.validateIndependentHoldout(
            trainRaw, trainMv, holdoutRaw,
            listOf(1.0, 2.0, 3.0, 4.0, 5.0), 0.01
        )
        assertFalse(failing.passed)
    }

    @Test
    fun refusesToCallThreeFitPointsIndependentValidation() {
        val validation = EcgResearchCalibration.validateWithLeaveOneOut(
            raw = listOf(100.0, 200.0, 300.0),
            referenceMv = listOf(0.5, 1.0, 1.5)
        )
        assertFalse(validation.usable)
    }

    @Test
    fun estimatesTimestampRateAndJitter() {
        val result = EcgResearchCalibration.estimateSampleRate(listOf(0L, 10L, 20L, 30L, 40L))
        assertTrue(result.usable)
        assertEquals(100.0, result.hz!!, 1e-9)
        assertEquals(0.0, result.jitterMs!!, 1e-9)
    }

    @Test
    fun estimatesHalEventRateFromNanosecondTimestamps() {
        val result = EcgResearchCalibration.estimateEventRateNs(
            listOf(1_000_000_000L, 1_002_000_000L, 1_004_000_000L, 1_006_000_000L)
        )
        assertTrue(result.usable)
        assertEquals(500.0, result.hz!!, 1e-9)
        assertEquals(2.0, result.medianIntervalMs!!, 1e-9)
        assertEquals(0.0, result.jitterMs!!, 1e-9)
    }

    @Test
    fun estimatesPackedPointRateWithoutUsingWholeMeasurementDuration() {
        val result = EcgResearchCalibration.estimatePackedPointRateNs(
            listOf(1_000_000_000L, 1_020_000_000L, 1_040_000_000L, 1_060_000_000L),
            listOf(10, 10, 10, 10),
            EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        )
        assertTrue(result.usable)
        assertEquals(500.0, result.hz!!, 1e-9)
    }

    @Test
    fun rejectsMissingPackedPointMetadata() {
        val result = EcgResearchCalibration.estimatePackedPointRateNs(
            listOf(1_000_000_000L, 1_020_000_000L, 1_040_000_000L), listOf(10),
            EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        )
        assertTrue(!result.usable)
    }

    @Test
    fun invalidEventTimestampCannotBecomeAValidInterval() {
        val result = EcgResearchCalibration.estimatePackedPointRateNs(
            listOf(-1L, 1_000_000_000L, 1_020_000_000L, 1_040_000_000L),
            listOf(10, 10, 10, 10),
            EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        )
        assertTrue(result.usable)
        assertEquals(500.0, result.hz!!, 1e-9)
    }

    @Test
    fun monotonicNegativeVendorTimestampsStillProvideValidDeltas() {
        val result = EcgResearchCalibration.estimatePackedPointRateNs(
            listOf(-40_000_060_000_000L, -40_000_040_000_000L, -40_000_020_000_000L, -40_000_000_000_000L),
            listOf(10, 10, 10, 10),
            EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        )
        assertTrue(result.usable)
        assertEquals(500.0, result.hz!!, 1e-9)
        assertEquals(20.0, result.medianIntervalMs!!, 1e-9)
    }

    @Test
    fun refusesPointRateForUnmappedSensorEventValuesEvenWhenCountsArePlausible() {
        val result = EcgResearchCalibration.estimatePackedPointRateNs(
            listOf(1_000_000_000L, 1_020_000_000L, 1_040_000_000L, 1_060_000_000L),
            listOf(10, 10, 10, 10),
            "hal_event_values_unmapped_preview"
        )
        assertTrue(!result.usable)
        assertEquals(null, result.hz)
    }
}
