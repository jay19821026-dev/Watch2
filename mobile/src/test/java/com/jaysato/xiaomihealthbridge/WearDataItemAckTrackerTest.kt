package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearDataItemAckTrackerTest {
    @Test
    fun routeClassificationNeverPersistsDataItemUris() {
        assertEquals("sensor_snapshot", WearDataItemAckTracker.route("/raw-sensor/ecg"))
        assertEquals("control_status", WearDataItemAckTracker.route("/control-status/current"))
        assertEquals("feature_snapshot", WearDataItemAckTracker.route("/feature-snapshot/current"))
        assertEquals("safety_alert", WearDataItemAckTracker.route("${MobileSafetyAlerts.PATH}/fall"))
        assertEquals("heart_rate", WearDataItemAckTracker.route(HeartRateDataListenerService.HEART_RATE_PATH))
        assertEquals("health_metric", WearDataItemAckTracker.route("${HeartRateDataListenerService.HEALTH_PATH_PREFIX}steps"))
        assertEquals("other", WearDataItemAckTracker.route("/unrecognized/private-id"))
    }

    @Test
    fun countersSaturateInsteadOfWrappingNegative() {
        assertEquals(1, WearDataItemAckTracker.increment(0))
        assertEquals(Int.MAX_VALUE, WearDataItemAckTracker.increment(Int.MAX_VALUE))
        assertEquals("route_sensor_snapshot_failed", WearDataItemAckTracker.routeCounterKey("sensor_snapshot", "failed"))
        assertEquals("route_other_failed", WearDataItemAckTracker.routeCounterKey("untrusted route", "failed"))
    }

    @Test
    fun deleteRetryAcceptsOnlyBoundedWearUrisForKnownAppRoutes() {
        assertTrue(WearDataItemAckRetryPolicy.isSupportedUri("wear://node/raw-sensor/ecg/1-id"))
        assertTrue(WearDataItemAckRetryPolicy.isSupportedUri("wear://node/heart-rate/1"))
        assertFalse(WearDataItemAckRetryPolicy.isSupportedUri("https://host/raw-sensor/ecg"))
        assertFalse(WearDataItemAckRetryPolicy.isSupportedUri("wear:///raw-sensor/ecg"))
        assertFalse(WearDataItemAckRetryPolicy.isSupportedUri("wear://node/private"))
        assertFalse(WearDataItemAckRetryPolicy.isSupportedUri("wear://node/${"x".repeat(WearDataItemAckRetryPolicy.MAX_URI_LENGTH + 1)}"))
    }

    @Test
    fun deleteRetryIsBoundedAndWorkIdentityDoesNotExposeUri() {
        assertFalse(WearDataItemAckRetryPolicy.shouldRetry(-1))
        repeat(WearDataItemAckRetryPolicy.MAX_AUTOMATIC_RETRIES) { attempt ->
            assertTrue(WearDataItemAckRetryPolicy.shouldRetry(attempt))
        }
        assertFalse(WearDataItemAckRetryPolicy.shouldRetry(WearDataItemAckRetryPolicy.MAX_AUTOMATIC_RETRIES))
        val uri = "wear://node/raw-sensor/ecg/secret-id"
        val name = WearDataItemAckRetryPolicy.uniqueWorkName(uri)
        assertNotEquals(uri, name)
        assertFalse(name.contains("secret-id"))
        assertEquals(name, WearDataItemAckRetryPolicy.uniqueWorkName(uri))
    }
}
