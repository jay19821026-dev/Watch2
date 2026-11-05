package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalReferenceCalibrationTest {
    @Test
    fun reportsSignedBiasAbsoluteErrorAndAgreementLimits() {
        val report = ExternalReferenceCalibration.summarizeErrors(listOf(-1.0, 0.0, 1.0))!!
        assertEquals(2.0 / 3.0, report.mae, 1e-9)
        assertEquals(kotlin.math.sqrt(2.0 / 3.0), report.rmse, 1e-9)
        assertEquals(0.0, report.bias, 1e-9)
        assertEquals(-1.96, report.lowerAgreementLimit, 1e-9)
        assertEquals(1.96, report.upperAgreementLimit, 1e-9)
        assertTrue(ExternalReferenceCalibration.summarizeErrors(listOf(Double.NaN, 1.0)) == null)
    }

    @Test
    fun recoversSlopeOffsetAndIndependentRmse() {
        val raw = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)
        val reference = raw.map { it * 2.5 - 1.25 }
        val result = ExternalReferenceCalibration.fit(raw, reference)

        assertTrue(result.usable)
        assertEquals(2.5, result.slope, 1e-9)
        assertEquals(-1.25, result.offset, 1e-9)
        assertEquals(0.0, result.fitRmse, 1e-9)
        assertEquals(0.0, result.validationRmse!!, 1e-9)
        assertEquals(8.75, result.apply(4.0)!!, 1e-9)
    }

    @Test
    fun requiresFivePairsForIndependentValidation() {
        val result = ExternalReferenceCalibration.fit(
            listOf(1.0, 2.0, 3.0, 4.0),
            listOf(2.0, 4.0, 6.0, 8.0)
        )
        assertFalse(result.usable)
    }

    @Test
    fun rejectsConstantRawOrReference() {
        assertFalse(ExternalReferenceCalibration.fit(List(5) { 1.0 }, List(5) { it.toDouble() }).usable)
        assertFalse(ExternalReferenceCalibration.fit(List(5) { it.toDouble() }, List(5) { 1.0 }).usable)
    }

    @Test
    fun rejectsNonFiniteValues() {
        val result = ExternalReferenceCalibration.fit(
            listOf(0.0, 1.0, 2.0, 3.0, Double.NaN),
            listOf(0.0, 1.0, 2.0, 3.0, 4.0)
        )
        assertFalse(result.usable)
    }
}
