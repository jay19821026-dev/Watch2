package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EcgHalRatePolicyTest {
    @Test
    fun convertsOnlyValidHalSamplingPeriodsToBoundedPointRate() {
        assertEquals(500f, EcgHalRatePolicy.sampleRateHz(0.002) ?: Float.NaN, 0.001f)
        assertNull(EcgHalRatePolicy.sampleRateHz(null))
        assertNull(EcgHalRatePolicy.sampleRateHz(Double.NaN))
        assertNull(EcgHalRatePolicy.sampleRateHz(0.0))
        assertNull(EcgHalRatePolicy.sampleRateHz(-0.002))
        assertNull(EcgHalRatePolicy.sampleRateHz(0.0001))
    }
}
