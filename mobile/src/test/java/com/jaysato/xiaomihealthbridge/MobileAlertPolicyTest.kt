package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileAlertPolicyTest {
    @Test
    fun replayedAndStaleSafetyEventsAreNotRenotified() {
        val now = 2_000_000L
        assertTrue(MobileAlertPolicy.shouldProcessEvent("fall_detected", now - 1_000L, 0L, now))
        assertFalse(MobileAlertPolicy.shouldProcessEvent("fall_detected", now - 1_000L, now - 1_000L, now))
        assertFalse(MobileAlertPolicy.shouldProcessEvent("fall_detected", now - 31 * 60_000L, 0L, now))
        assertFalse(MobileAlertPolicy.shouldProcessEvent("fall_detected", now + 3 * 60_000L, 0L, now))
    }

    @Test
    fun routineAlertsAreQuietForThirtyMinutesRegardlessOfAlertType() {
        assertTrue(MobileAlertPolicy.shouldSuppressRoutine(1_000_000L, 1_000_000L + 10 * 60_000L))
        assertFalse(MobileAlertPolicy.shouldSuppressRoutine(1_000_000L, 1_000_000L + 30 * 60_000L))
        assertFalse(MobileAlertPolicy.shouldSuppressRoutine(2_000_000L, 1_000_000L))
    }

    @Test
    fun fallAndSosRemainCritical() {
        assertTrue(MobileAlertPolicy.isCritical("fall_detected"))
        assertTrue(MobileAlertPolicy.isCritical("fall_sos_requested"))
        assertFalse(MobileAlertPolicy.bypassesRoutineThrottle("fall_cancelled"))
        assertFalse(MobileAlertPolicy.isCritical("fall_cancelled"))
        assertFalse(MobileAlertPolicy.isCritical("heart_high"))
        assertFalse(MobileAlertPolicy.isCritical("irregular_rhythm"))
    }

    @Test
    fun routineNotificationsAreOptInAndStillRateLimited() {
        assertFalse(MobileAlertPolicy.shouldSendRoutine(false, 0L, 1_000_000L))
        assertTrue(MobileAlertPolicy.shouldSendRoutine(true, 0L, 1_000_000L))
        assertFalse(MobileAlertPolicy.shouldSendRoutine(true, 1_000_000L, 1_000_001L))
    }

}
