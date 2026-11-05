package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalTimelineLabelTest {
    @Test
    fun missingOrInvalidRateUsesIndexDomainInsteadOfZeroHertz() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { rate ->
            val label = SignalTimelineLabel.describe(128, rate, "")
            assertTrue(label.contains("NORMALIZED_INDEX"))
            assertTrue(label.contains("128 點"))
            assertTrue(label.contains("UNKNOWN"))
            assertFalse(label.contains("0.0 Hz"))
        }
    }

    @Test
    fun reportedRateIsNotLabeledAsVerifiedClock() {
        val label = SignalTimelineLabel.describe(256, 25f, "SENSOR_TIMESTAMP")
        assertTrue(label.contains("256 點"))
        assertTrue(label.contains("HAL 回報取樣率 25.0 Hz"))
        assertTrue(label.contains("NORMALIZED_INDEX"))
        assertTrue(label.contains("SENSOR_TIMESTAMP"))
        assertTrue(label.contains("estimated_timeline=false"))
        assertTrue(label.contains("不代表時間軸已驗證"))
    }

    @Test
    fun rateDoesNotTurnNormalizedOrEstimatedIndexIntoSensorTime() {
        val indexLabel = SignalTimelineLabel.describe(256, 25f, "UNKNOWN")
        assertTrue(indexLabel.contains("NORMALIZED_INDEX"))
        assertTrue(indexLabel.contains("回報取樣率 25.0 Hz"))
        assertTrue(indexLabel.contains("不代表時間軸已驗證"))
        assertTrue(indexLabel.contains("estimated_timeline=false"))

        val estimatedLabel = SignalTimelineLabel.describe(256, 25f, "ESTIMATED")
        assertTrue(estimatedLabel.contains("ESTIMATED_TIMELINE"))
        assertTrue(estimatedLabel.contains("estimated_timeline=true"))
        assertFalse(estimatedLabel.contains("SENSOR_TIME"))
    }

    @Test
    fun onlySensorClockWithMeasurementTimeUsesSensorTimeline() {
        val trusted = SignalTimelineLabel.evidence(1_700_000_000_000L, "android_sensor_event_elapsed_realtime")
        assertTrue(trusted.source == "SENSOR_TIME")
        assertFalse(trusted.estimated)

        val missingTime = SignalTimelineLabel.evidence(0L, "android_sensor_event_elapsed_realtime")
        assertTrue(missingTime.source == "NORMALIZED_INDEX")
        assertFalse(missingTime.estimated)
    }

    @Test
    fun healthServicesTimestampIsMeasurementTimeAndEstimatedSourceMustBeExplicit() {
        val healthServices = SignalTimelineLabel.evidence(
            1_700_000_000_000L,
            HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        )
        assertTrue(healthServices.source == "MEASUREMENT_TIME")
        assertFalse(healthServices.estimated)

        assertTrue(SignalTimelineLabel.evidence(0L, "rate_estimated_by_hal").source == "NORMALIZED_INDEX")
        assertTrue(SignalTimelineLabel.evidence(0L, "ESTIMATED_TIMELINE").estimated)
    }
}
