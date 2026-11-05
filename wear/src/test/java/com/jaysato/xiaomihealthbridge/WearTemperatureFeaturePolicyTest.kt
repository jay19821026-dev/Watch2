package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearTemperatureFeaturePolicyTest {
    @Test
    fun acceptsOnlyNewerValidMeasurementTimes() {
        val source = "android_sensor_event_elapsed_realtime"
        assertTrue(WearTemperatureFeaturePolicy.shouldAccept(0L, 1_000L, 1_000L, source))
        assertTrue(WearTemperatureFeaturePolicy.shouldAccept(1_000L, 2_000L, 2_000L, source))
        assertFalse(WearTemperatureFeaturePolicy.shouldAccept(2_000L, 2_000L, 2_000L, source))
        assertFalse(WearTemperatureFeaturePolicy.shouldAccept(2_000L, 1_000L, 2_000L, source))
        assertFalse(WearTemperatureFeaturePolicy.shouldAccept(0L, 0L, 2_000L, source))
        assertFalse(WearTemperatureFeaturePolicy.shouldAccept(0L, 302_001L, 2_000L, source))
        assertFalse(WearTemperatureFeaturePolicy.shouldAccept(0L, 1_000L, 1_000L, "callback_wall_clock_fallback"))
    }

    @Test
    fun temperatureAggregationMetadataReflectsActualCaptureCount() {
        assertEquals(WearTemperatureFeaturePolicy.Aggregation("probe_finite_sample_mean", 8),
            WearTemperatureFeaturePolicy.probeAggregation(true, 8))
        assertEquals(WearTemperatureFeaturePolicy.Aggregation("probe_single_finite_value", 1),
            WearTemperatureFeaturePolicy.probeAggregation(true, 1))
        assertEquals(WearTemperatureFeaturePolicy.Aggregation("probe_latest_event_value", 1),
            WearTemperatureFeaturePolicy.probeAggregation(false, 5))
        assertEquals(WearTemperatureFeaturePolicy.Aggregation("single_callback_raw_value", 1),
            WearTemperatureFeaturePolicy.directCaptureAggregation())
    }
}
