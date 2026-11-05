package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawWaveformDisplayPolicyTest {
    @Test
    fun opticalFillValueIsTreatedAsGapForPpgAndSpo2() {
        assertTrue(RawWaveformDisplayPolicy.isGap(524287f, "ppg"))
        assertTrue(RawWaveformDisplayPolicy.isGap(524287f, "spo2 raw / a.u."))
        assertTrue(RawWaveformDisplayPolicy.isGap(524287f, "mi_spo2"))
        assertFalse(RawWaveformDisplayPolicy.isGap(524287f, "bioz"))
    }

    @Test
    fun finiteZeroRemainsARealDisplayedSample() {
        assertFalse(RawWaveformDisplayPolicy.isGap(0f, "spo2 raw / a.u."))
        assertTrue(RawWaveformDisplayPolicy.isGap(Float.NaN, "spo2 raw / a.u."))
    }
}
