package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthAnalysisContractTest {
    @Test
    fun zeroIsAValidMeasuredValueAndNotMissingEvidence() {
        val result = metric(
            state = HealthResultState.NORMAL,
            value = 0.0,
            unit = "count",
            calibration = CalibrationState.NOT_APPLICABLE
        )

        assertEquals(0.0, result.value!!, 0.0)
    }

    @Test
    fun missingValueRemainsDistinctFromZero() {
        val result = metric(
            state = HealthResultState.UNAVAILABLE,
            value = null,
            rawValue = null,
            unit = null,
            calibration = CalibrationState.UNKNOWN,
            evidenceSupport = EvidenceSupport.INSUFFICIENT,
            reasonCodes = setOf(HealthReasonCode.NO_EVIDENCE)
        )

        assertNull(result.value)
        assertNull(result.rawValue)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unavailableEvidenceCannotBeNormal() {
        metric(
            state = HealthResultState.NORMAL,
            evidenceSupport = EvidenceSupport.INSUFFICIENT,
            value = null,
            unit = null
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun timebaseCannotBeClaimedWithoutSource() {
        metric(source = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun estimatedEvidenceCannotBePresentedAsNormal() {
        metric(origin = EvidenceOrigin.MODEL_ESTIMATE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun physicalUnitRequiresVerifiedCalibration() {
        metric(calibration = CalibrationState.UNVERIFIED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unavailableResultRequiresReason() {
        metric(
            state = HealthResultState.UNAVAILABLE,
            evidenceSupport = EvidenceSupport.INSUFFICIENT,
            reasonCodes = emptySet()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun sessionRejectsDuplicateMetricIds() {
        val result = metric()
        HealthSessionResult(
            sessionId = "session-1",
            startedAtEpochMillis = 1_000L,
            endedAtEpochMillis = 2_000L,
            results = listOf(result, result),
            algorithmVersion = "test-1"
        )
    }

    private fun metric(
        state: HealthResultState = HealthResultState.NORMAL,
        origin: EvidenceOrigin = EvidenceOrigin.MEASURED,
        timebase: EvidenceTimebase = EvidenceTimebase.SENSOR,
        calibration: CalibrationState = CalibrationState.VERIFIED_DEVICE,
        value: Double? = 1.0,
        unit: String? = "unit",
        rawValue: Double? = null,
        rawUnit: String? = null,
        source: String? = "test-sensor",
        quality: Double? = 0.9,
        evidenceSupport: EvidenceSupport = EvidenceSupport.SUPPORTED,
        reasonCodes: Set<HealthReasonCode> = emptySet(),
        algorithmVersion: String = "test-1",
        modelVersion: String? = null,
        measuredAtEpochMillis: Long? = null
    ) = HealthMetricResult(
        metricId = "test.metric",
        state = state,
        origin = origin,
        timebase = timebase,
        calibration = calibration,
        value = value,
        unit = unit,
        rawValue = rawValue,
        rawUnit = rawUnit,
        source = source,
        quality = quality,
        evidenceSupport = evidenceSupport,
        reasonCodes = reasonCodes,
        algorithmVersion = algorithmVersion,
        modelVersion = modelVersion,
        measuredAtEpochMillis = measuredAtEpochMillis
    )
}
