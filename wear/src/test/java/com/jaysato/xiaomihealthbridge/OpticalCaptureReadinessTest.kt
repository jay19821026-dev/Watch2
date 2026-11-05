package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpticalCaptureReadinessTest {
    private val validOpticalWaveform = List(64) { index -> 1f + (index % 16) * 3f }

    @Test
    fun acceptsCapturedRawPayloadWithoutClaimingSignalQuality() {
        assertTrue(OpticalCaptureReadiness.hasCapturedPayload(8, validOpticalWaveform, 5_000))
    }

    @Test
    fun rejectsCaptureBelowMinimumEventCount() {
        assertFalse(OpticalCaptureReadiness.hasCapturedPayload(7, validOpticalWaveform, 5_000))
    }

    @Test
    fun keepsRawCaptureEvenWhenOpticalValuesAreFillSentinels() {
        val fillOnly = List(64) { ChipCalibrationProfiles.OPTICAL_FILL }

        assertTrue(OpticalCaptureReadiness.hasCapturedPayload(8, fillOnly, 5_000))
    }

    @Test
    fun rejectsRapidSampleBurstWithoutMinimumCaptureDuration() {
        assertFalse(OpticalCaptureReadiness.hasCapturedPayload(8, validOpticalWaveform, 4_999))
    }
}
