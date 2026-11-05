package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveHeartRatePolicyTest {
    @Test
    fun throttleSurvivesServiceRecreationAtAndBeforeIntervalBoundary() {
        val lastSent = 1_000_000L
        val interval = 90_000L

        assertFalse(PassiveHeartRatePolicy.shouldSend(lastSent + interval - 1, lastSent, interval))
        assertTrue(PassiveHeartRatePolicy.shouldSend(lastSent + interval, lastSent, interval))
    }

    @Test
    fun invalidOrOutOfOrderTimestampsAreRejected() {
        assertFalse(PassiveHeartRatePolicy.shouldSend(0L, 0L, 90_000L))
        assertFalse(PassiveHeartRatePolicy.shouldSend(1_000L, 2_000L, 1L))
        assertFalse(PassiveHeartRatePolicy.shouldSend(2_000L, 1_000L, -1L))
    }

    @Test
    fun firstValidReadingIsAllowed() {
        assertTrue(PassiveHeartRatePolicy.shouldSend(1_000_000L, 0L, 90_000L))
    }

    @Test
    fun continuousModeAllowsEachNewTimestampButRejectsDuplicates() {
        assertTrue(PassiveHeartRatePolicy.shouldSend(2_000L, 1_000L, 0L))
        assertFalse(PassiveHeartRatePolicy.shouldSend(1_000L, 1_000L, 0L))
    }
}
