package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpticalPulseDisplayPolicyTest {
    @Test
    fun rawAdcMagnitudeCannotBeUsedAsPulseEstimate() {
        assertNull(
            OpticalPulseDisplayPolicy.researchEstimateBpm(
                kind = "max_hr",
                estimateBpm = 6_003_174f,
                qualityScore = 10,
                status = "has_data",
                measuredAtMillis = 1_000,
                nowMillis = 2_000
            )
        )
    }

    @Test
    fun opticalAdcVerdictLabelsLargeRawCodesAsUncalibratedRawData() {
        val verdict = ClinicalVerdict.forOpticalAdc(
            sampleCount = 3_812,
            rawAverage = 6_003_174.0,
            estimateBpm = null,
            status = "has_data",
            wearQualified = true
        )
        assertTrue(verdict.headline.contains("不是 bpm"))
        assertTrue(verdict.findings.any { it.contains("不可解讀為生理數值") })
        assertTrue(verdict.findings.none { it.contains("6,003,174 bpm") })
    }

    @Test
    fun formulaEstimateRequiresFreshQualityQualifiedOpticalData() {
        assertEquals(
            72f,
            OpticalPulseDisplayPolicy.researchEstimateBpm(
                "max_hr", 72f, 70, "has_data", 1_000, 2_000,
                sampleRateHz = 100f,
                channelMappingVerified = true,
                timestampSource = "android_sensor_event_elapsed_realtime",
                wearQualified = true
            )
        )
        assertNull(OpticalPulseDisplayPolicy.researchEstimateBpm("max_hr", 72f, 69, "has_data", 1_000, 2_000))
        assertNull(OpticalPulseDisplayPolicy.researchEstimateBpm("lifeq_ott", 72f, 90, "has_data", 1_000, 31 * 60 * 1000L))
        assertNull(OpticalPulseDisplayPolicy.researchEstimateBpm("ppg", 72f, 90, "invalid_optical", 1_000, 2_000))
    }

    @Test
    fun callbackFallbackTimestampCannotQualifyOpticalPulseEstimate() {
        assertNull(
            OpticalPulseDisplayPolicy.researchEstimateBpm(
                "ppg", 72f, 90, "valid_signal", 1_000, 2_000,
                sampleRateHz = 100f,
                channelMappingVerified = true,
                timestampSource = "callback_wall_clock_fallback"
            )
        )
    }

    @Test
    fun missingTimestampCannotQualifyCurrentOpticalPulseEstimate() {
        assertNull(
            OpticalPulseDisplayPolicy.researchEstimateBpm(
                kind = "ppg",
                estimateBpm = 72f,
                qualityScore = 90,
                status = "research_optical",
                measuredAtMillis = 1_000,
                nowMillis = 2_000,
                sampleRateHz = 100f,
                channelMappingVerified = true,
                timestampSource = null,
                wearQualified = true
            )
        )
    }

    @Test
    fun opticalEstimateRequiresVerifiedChannelMapAndKnownSampleRate() {
        assertNull(
            OpticalPulseDisplayPolicy.researchEstimateBpm(
                "max_hr", 72f, 90, "has_data", 1_000, 2_000, sampleRateHz = 100f
            )
        )
        assertNull(
            OpticalPulseDisplayPolicy.researchEstimateBpm(
                "ppg", 72f, 90, "has_data", 1_000, 2_000,
                sampleRateHz = 0f,
                channelMappingVerified = true
            )
        )
    }

    @Test
    fun opticalEstimateRejectsUnknownOrOffWristState() {
        val unknownWear = OpticalPulseDisplayPolicy.researchEstimateBpm(
            kind = "ppg", estimateBpm = 72f, qualityScore = 90, status = "valid_signal",
            measuredAtMillis = 1_000, nowMillis = 2_000, sampleRateHz = 100f,
            channelMappingVerified = true,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )
        val offWrist = OpticalPulseDisplayPolicy.researchEstimateBpm(
            kind = "ppg", estimateBpm = 72f, qualityScore = 90, status = "valid_signal",
            measuredAtMillis = 1_000, nowMillis = 2_000, sampleRateHz = 100f,
            channelMappingVerified = true,
            timestampSource = "android_sensor_event_elapsed_realtime", wearQualified = false
        )

        assertNull(unknownWear)
        assertNull(offWrist)
    }
}
