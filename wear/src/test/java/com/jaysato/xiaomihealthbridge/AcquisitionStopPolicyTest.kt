package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class AcquisitionStopPolicyTest {
    @Test
    fun explicitTimeoutIsNotReportedAsSuccessfulCapture() {
        assertEquals(
            "time_limit_reached",
            AcquisitionStopPolicy.resolve("time_limit_reached", hasSamples = true, emptyStatus = "no_events")
        )
    }

    @Test
    fun candidateTimeoutKeepsPartialCaptureDistinct() {
        assertEquals(
            "candidate_timeout",
            AcquisitionStopPolicy.resolve("candidate_timeout", hasSamples = true, emptyStatus = "no_events")
        )
    }

    @Test
    fun ordinaryStopStillDistinguishesSamplesFromNoEvents() {
        assertEquals(
            "stopped_with_samples",
            AcquisitionStopPolicy.resolve(null, hasSamples = true, emptyStatus = "no_events")
        )
        assertEquals(
            "no_events",
            AcquisitionStopPolicy.resolve(null, hasSamples = false, emptyStatus = "no_events")
        )
    }
}
