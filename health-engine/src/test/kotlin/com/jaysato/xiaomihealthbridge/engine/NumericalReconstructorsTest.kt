package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.*
import org.junit.Test

class NumericalReconstructorsTest {
    @Test fun linearAndHermiteSplineHonorEndpointsAndLinearSignal() {
        assertEquals(2.0, LinearInterpolator.interpolate(1.0, 3.0, 0.5), 0.0)
        assertEquals(1.0, SplineInterpolator.interpolate(1.0, 3.0, 2.0, 2.0, 0.0, 1.0), 0.0)
        assertEquals(3.0, SplineInterpolator.interpolate(1.0, 3.0, 2.0, 2.0, 1.0, 1.0), 0.0)
        assertEquals(2.0, SplineInterpolator.interpolate(1.0, 3.0, 2.0, 2.0, 0.5, 1.0), 0.0)
    }

    @Test fun kalmanMarksFilteredAndImputedWithoutChangingRaw() {
        val raw = listOf(null, 1.0, 2.0, null, null, 3.0)
        val output = KalmanReconstructor.reconstructIndexDomain(raw, KalmanParameters(0.1, 0.5, 1.0, 2))
        assertNull(raw[3])
        assertEquals(listOf(1, 2, 3, 4, 5), output.map { it.index })
        assertEquals(DataOrigin.IMPUTED, output[2].origin)
        assertEquals(DataOrigin.FILTERED, output.last().origin)
        assertTrue(output[3].variance > output[2].variance)
        assertTrue(output.all { it.value.isFinite() })
    }

    @Test fun longGapStopsPredictionAndResetsAtNextObservation() {
        val output = KalmanReconstructor.reconstructIndexDomain(listOf(1.0, null, null, null, 4.0), KalmanParameters(0.1, 0.5, 1.0, 1))
        assertEquals(listOf(0, 1, 4), output.map { it.index })
        assertEquals(4.0, output.last().value, 0.0)
        assertTrue(KalmanReconstructor.reconstructIndexDomain(listOf(null, Double.NaN), KalmanParameters(0.1, 0.5, 1.0, 1)).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidNumericalInputIsRejected() {
        LinearInterpolator.interpolate(Double.NaN, 1.0, 0.5)
    }
}
