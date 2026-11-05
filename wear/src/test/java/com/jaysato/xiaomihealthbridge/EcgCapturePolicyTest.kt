package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgCapturePolicyTest {
    @Test
    fun noEventsStopAfterBoundedStartupWait() {
        assertNull(EcgCapturePolicy.idleStopReason(0, EcgCapturePolicy.NO_CALLBACK_TIMEOUT_MILLIS - 1))
        assertEquals("no_callback_timeout", EcgCapturePolicy.idleStopReason(0, EcgCapturePolicy.NO_CALLBACK_TIMEOUT_MILLIS))
    }

    @Test
    fun stalledSignalStopsButKeepsPartialCapture() {
        assertNull(EcgCapturePolicy.idleStopReason(12, EcgCapturePolicy.SIGNAL_QUIET_TIMEOUT_MILLIS - 1))
        assertEquals("signal_stalled", EcgCapturePolicy.idleStopReason(12, EcgCapturePolicy.SIGNAL_QUIET_TIMEOUT_MILLIS))
    }

    @Test
    fun dedicatedSessionHasBoundedMaximumDuration() {
        assertEquals(60_000L, EcgCapturePolicy.MAX_CAPTURE_MILLIS)
    }

    @Test
    fun onlyTimeLimitCanAutoContinueWithinBound() {
        assertEquals(3, EcgCapturePolicy.MAX_AUTO_SEGMENTS)
        assertTrue(EcgCapturePolicy.canAutoContinue("time_limit_reached", 1))
        assertTrue(EcgCapturePolicy.canAutoContinue("time_limit_reached", 2))
        assertFalse(EcgCapturePolicy.canAutoContinue("signal_stalled", 1))
        assertFalse(EcgCapturePolicy.canAutoContinue("time_limit_reached", 3))
    }
}
