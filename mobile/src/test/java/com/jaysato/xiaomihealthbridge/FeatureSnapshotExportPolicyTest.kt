package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSnapshotExportPolicyTest {
    @Test
    fun rejectsMissingOrImplausiblyFutureTimestamp() {
        val now = 100_000_000L

        assertFalse(FeatureSnapshotExportPolicy.hasValidTimestamp(0L, now))
        assertFalse(FeatureSnapshotExportPolicy.hasValidTimestamp(-1L, now))
        assertFalse(FeatureSnapshotExportPolicy.hasValidTimestamp(now + 300_001L, now))
    }

    @Test
    fun acceptsValidTimestampIncludingAllowedFutureClockSkew() {
        val now = 100_000_000L

        assertTrue(FeatureSnapshotExportPolicy.hasValidTimestamp(now - 86_400_000L, now))
        assertTrue(FeatureSnapshotExportPolicy.hasValidTimestamp(now, now))
        assertTrue(FeatureSnapshotExportPolicy.hasValidTimestamp(now + 300_000L, now))
    }

    @Test
    fun currentExportValuesRequireTrustedFreshSensorEventTime() {
        val now = 100_000_000L
        val source = "android_sensor_event_elapsed_realtime"

        assertEquals("fresh", FeatureSnapshotExportPolicy.freshnessState(now, source, now))
        assertEquals("stale", FeatureSnapshotExportPolicy.freshnessState(
            now - SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS - 1L, source, now
        ))
        assertEquals("timestamp_source_untrusted", FeatureSnapshotExportPolicy.freshnessState(now, null, now))
        assertEquals("future_timestamp", FeatureSnapshotExportPolicy.freshnessState(now + 1L, source, now))
        assertEquals("timestamp_missing", FeatureSnapshotExportPolicy.freshnessState(0L, source, now))
        assertEquals("fresh", FeatureSnapshotExportPolicy.measurementFreshnessState(
            now,
            HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE,
            now
        ))
        assertEquals("timestamp_source_untrusted", FeatureSnapshotExportPolicy.measurementFreshnessState(now, null, now))
        assertEquals("stale", FeatureSnapshotExportPolicy.measurementFreshnessState(
            now - HealthAnalysisFreshnessPolicy.RECENT_SENSOR_INPUT_MILLIS - 1L,
            HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE,
            now,
            HealthAnalysisFreshnessPolicy.RECENT_SENSOR_INPUT_MILLIS
        ))
        assertEquals("future_timestamp", FeatureSnapshotExportPolicy.measurementFreshnessState(
            now + 1L,
            HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE,
            now
        ))
    }
}
