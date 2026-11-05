package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawOxygenProbePolicyTest {
    @Test
    fun rawCaptureHasBoundedLifetimeAndSensorRateFloor() {
        assertEquals(60_000L, RawOxygenProbe.MAX_CAPTURE_MILLIS)
        assertTrue(
            RawVitalsProbe.minimumSamplingPeriodUs(RawVitalsProbe.Kind.SPO2) >= 10_000
        )
    }
}
