package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthCenterMetricCandidatePolicyTest {
    @Test
    fun ppgResearchRateRequiresEveryExistingEligibilityGate() {
        val valid = candidate()
        assertEquals(72.0, valid!!.value!!, 0.0)
        assertEquals("PPG_RESEARCH", valid.source)

        assertNull(candidate(qualityScore = 49))
        assertNull(candidate(sampleRateHz = 0f))
        assertNull(candidate(channelMappingVerified = false))
        assertNull(candidate(timestampSource = "callback_receipt_time"))
        assertNull(candidate(wearQualified = false))
        assertNull(candidate(status = "research_signal_unscaled"))
        assertNull(candidate(measuredAtMillis = 0L))
        assertNull(candidate(rawEstimateBpm = "NaN"))
    }

    private fun candidate(
        rawEstimateBpm: String? = "72",
        qualityScore: Int = 70,
        status: String = "valid_signal",
        measuredAtMillis: Long = 1_000L,
        nowMillis: Long = 2_000L,
        sampleRateHz: Float = 100f,
        channelMappingVerified: Boolean = true,
        timestampSource: String? = "android_sensor_event_elapsed_realtime",
        wearQualified: Boolean = true
    ) = HealthCenterMetricCandidatePolicy.ppgResearchCandidate(
        rawEstimateBpm,
        qualityScore,
        status,
        measuredAtMillis,
        nowMillis,
        sampleRateHz,
        channelMappingVerified,
        timestampSource,
        wearQualified
    )
}
