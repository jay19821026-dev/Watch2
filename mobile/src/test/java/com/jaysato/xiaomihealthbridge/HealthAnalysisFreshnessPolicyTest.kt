package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthAnalysisFreshnessPolicyTest {
    @Test
    fun canonicalPerSensorTimeWinsAndLegacyAliasIsOnlyFallback() {
        assertTrue(HealthAnalysisFreshnessPolicy.resolveStoredMeasurementTime(900L, 800L) == 900L)
        assertTrue(HealthAnalysisFreshnessPolicy.resolveStoredMeasurementTime(0L, 800L) == 800L)
        assertTrue(HealthAnalysisFreshnessPolicy.resolveStoredMeasurementTime(0L) == 0L)
    }

    @Test
    fun distinguishesMissingFreshBoundaryStaleAndFutureTimes() {
        val now = 1_000_000L
        assertFalse(HealthAnalysisFreshnessPolicy.isFresh(0L, now))
        assertTrue(HealthAnalysisFreshnessPolicy.isFresh(now, now))
        assertTrue(HealthAnalysisFreshnessPolicy.isFresh(now - 600_000L, now))
        assertFalse(HealthAnalysisFreshnessPolicy.isFresh(now - 600_001L, now))
        assertFalse(HealthAnalysisFreshnessPolicy.isFresh(now + 1L, now))
    }

    @Test
    fun sensorFreshnessRequiresRealSensorEventClockSource() {
        val now = 1_000_000L
        assertTrue(HealthAnalysisFreshnessPolicy.isFreshSensor(now, "android_sensor_event_elapsed_realtime", now))
        assertFalse(HealthAnalysisFreshnessPolicy.isFreshSensor(now, "callback_wall_clock_fallback", now))
        assertFalse(HealthAnalysisFreshnessPolicy.isFreshSensor(now, "watch_sample_timestamp_source_unreported", now))
        assertFalse(HealthAnalysisFreshnessPolicy.isFreshSensor(now, null, now))
    }

    @Test
    fun healthServicesTimestampRequiresExplicitDataPointProvenance() {
        val now = 1_000_000L
        val source = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        assertTrue(HealthAnalysisFreshnessPolicy.isFreshMeasurement(now, source, now))
        assertFalse(HealthAnalysisFreshnessPolicy.isFreshMeasurement(now, null, now))
        assertFalse(HealthAnalysisFreshnessPolicy.isFreshMeasurement(now + 1L, source, now))
        assertFalse(HealthAnalysisFreshnessPolicy.isFreshMeasurement(now - 600_001L, source, now))
    }

    @Test
    fun featureSnapshotCallbackTimeCannotMakeEnvironmentalSensorValueLookFresh() {
        val now = 1_000_000L
        assertFalse(
            HealthAnalysisFreshnessPolicy.isFreshSensor(
                now,
                "watch_sample_timestamp_source_unreported",
                now
            )
        )
        assertFalse(
            HealthAnalysisFreshnessPolicy.isFreshSensor(
                now - 24 * 60 * 60 * 1000L - 1L,
                "android_sensor_event_elapsed_realtime",
                now
            )
        )
    }
}
