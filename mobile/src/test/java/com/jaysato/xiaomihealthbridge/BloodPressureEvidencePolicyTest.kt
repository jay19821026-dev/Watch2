package com.jaysato.xiaomihealthbridge

import org.junit.Assert.*
import org.junit.Test

class BloodPressureEvidencePolicyTest {
    private val now = BloodPressureEvidencePolicy.MAX_AGE_MS + 10_000L
    private val manual = BloodPressureEvidencePolicy.Evidence(
        120.0, 80.0, now - 100L, "manual", BloodPressureEvidencePolicy.Origin.MANUAL_CUFF, "cuff"
    )
    private val hc = manual.copy(
        measuredAt = now, source = "source.app", origin = BloodPressureEvidencePolicy.Origin.HEALTH_CONNECT,
        measurementMethod = null
    )

    @Test fun selectsNewestWithOriginalProvenanceWithoutMutatingManual() {
        assertEquals(hc, BloodPressureEvidencePolicy.newestValid(listOf(manual, hc), now))
        assertEquals(now - 100L, manual.measuredAt)
        val newerManual = manual.copy(measuredAt = now + 1)
        assertEquals(newerManual, BloodPressureEvidencePolicy.newestValid(listOf(hc, newerManual), now + 1))
    }

    @Test fun rejectsInvalidNewerRecordsAndFallsBack() {
        val invalid = listOf(
            hc.copy(systolic = Double.NaN), hc.copy(diastolic = Double.POSITIVE_INFINITY),
            hc.copy(systolic = 69.0), hc.copy(systolic = 251.0),
            hc.copy(diastolic = 34.0), hc.copy(diastolic = 151.0),
            hc.copy(systolic = 80.0, diastolic = 80.0), hc.copy(source = " "),
            hc.copy(measuredAt = now + 1), hc.copy(measuredAt = 0)
        )
        invalid.forEach { assertEquals(manual, BloodPressureEvidencePolicy.newestValid(listOf(manual, it), now)) }
    }

    @Test fun acceptsInclusiveAgeAndValueBoundaries() {
        assertTrue(BloodPressureEvidencePolicy.isValid(hc.copy(measuredAt = now - BloodPressureEvidencePolicy.MAX_AGE_MS), now))
        assertFalse(BloodPressureEvidencePolicy.isValid(hc.copy(measuredAt = now - BloodPressureEvidencePolicy.MAX_AGE_MS - 1), now))
        assertTrue(BloodPressureEvidencePolicy.isValid(hc.copy(systolic = 70.0, diastolic = 35.0), now))
        assertTrue(BloodPressureEvidencePolicy.isValid(hc.copy(systolic = 250.0, diastolic = 150.0), now))
        assertNull(BloodPressureEvidencePolicy.newestValid(emptyList(), now))
    }

    @Test fun hcMethodNeverImpliesClinicalValidationAndTiesPreferManual() {
        assertTrue(hc.researchOnly)
        assertTrue(hc.copy(measurementMethod = "cuff").researchOnly)
        assertFalse(manual.researchOnly)
        val tiedManual = manual.copy(measuredAt = now)
        assertEquals(tiedManual, BloodPressureEvidencePolicy.newestValid(listOf(hc, tiedManual), now))
        assertEquals(tiedManual, BloodPressureEvidencePolicy.newestValid(listOf(tiedManual, hc), now))
    }
}
