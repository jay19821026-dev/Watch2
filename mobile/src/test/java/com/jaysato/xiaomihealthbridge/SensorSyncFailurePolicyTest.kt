package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorSyncFailurePolicyTest {
    private val now = 2_000_000L

    @Test fun matchingRecentRejectionIsVisible() {
        assertTrue(SensorSyncFailurePolicy.isRecentForKind("ecg", "ecg", now - 1_000L, now))
    }

    @Test fun otherSensorRejectionDoesNotLeakIntoCard() {
        assertFalse(SensorSyncFailurePolicy.isRecentForKind("ecg", "ppg", now - 1_000L, now))
    }

    @Test fun staleOrFutureRejectionIsIgnored() {
        assertFalse(SensorSyncFailurePolicy.isRecentForKind("ecg", "ecg", now - 31 * 60_000L, now))
        assertFalse(SensorSyncFailurePolicy.isRecentForKind("ecg", "ecg", now + 1L, now))
    }
}
