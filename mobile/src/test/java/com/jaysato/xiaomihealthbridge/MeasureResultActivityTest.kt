package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureResultActivityTest {
    private val now = 100_000_000L

    @Test
    fun resultDateRequiresTrustedEpochTimestamp() {
        val trusted = MeasureResultTimestampPresentation.label(
            timestampMillis = now - 1_000L,
            source = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE,
            nowMillis = now
        )
        val sensorClock = MeasureResultTimestampPresentation.label(
            timestampMillis = now - 1_000L,
            source = "android_sensor_event_elapsed_realtime",
            nowMillis = now
        )

        assertTrue(trusted!!.matches(Regex("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}")))
        assertEquals("量測時間未確認（來源未提供或非日曆時間）", sensorClock)
        assertEquals("量測時間未確認（來源未提供或非日曆時間）", MeasureResultTimestampPresentation.label(now, null, now))
        assertEquals("量測時間異常（時間戳在未來）", MeasureResultTimestampPresentation.label(
            now + 1L, HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, now
        ))
    }

    @Test
    fun healthServicesHeartRateAcceptsOnlyFreshTrustedDatapointTime() {
        val fresh = MeasureResultValuePolicy.present(
            kind = "whs_heart_rate",
            value = "67",
            unit = "Health Services bpm",
            samples = 1,
            status = "has_data",
            measuredAtMillis = now - 1_000L,
            nowMillis = now,
            timestampSource = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        )
        val unknown = MeasureResultValuePolicy.present(
            kind = "whs_heart_rate",
            value = "67",
            unit = "Health Services bpm",
            samples = 1,
            status = "has_data",
            measuredAtMillis = now - 1_000L,
            nowMillis = now,
            timestampSource = "callback_receipt_time"
        )

        assertTrue(fresh.isCurrent)
        assertEquals("67", fresh.value)
        assertFalse(unknown.isCurrent)
        assertEquals("—", unknown.value)
    }

    @Test
    fun freshEcgFormulaIsVisibleOnlyAsResearchEstimate() {
        val result = MeasureResultValuePolicy.present(
            kind = "ecg",
            value = "72",
            unit = "bpm",
            samples = 120,
            status = "research_signal_unscaled",
            measuredAtMillis = now - 1_000L,
            nowMillis = now,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertTrue(result.isCurrent)
        assertEquals("72", result.value)
        assertEquals("研究估算 bpm", result.unit)
    }

    @Test
    fun staleEcgFormulaIsNotPresentedAsCurrent() {
        val result = MeasureResultValuePolicy.present(
            kind = "ecg",
            value = "72",
            unit = "bpm",
            samples = 120,
            status = "research_signal_unscaled",
            measuredAtMillis = now - SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS - 1L,
            nowMillis = now,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        assertFalse(result.isCurrent)
        assertEquals("—", result.value)
        assertEquals("研究估算 bpm", result.unit)
        assertEquals("資料已過期，不能代表目前量測", result.headline)
    }

    @Test
    fun unknownEcgTimestampIsNotPresentedAsCurrent() {
        val result = MeasureResultValuePolicy.present(
            kind = "ecg",
            value = "72",
            unit = "bpm",
            samples = 120,
            status = "research_signal_unscaled",
            measuredAtMillis = 0L,
            nowMillis = now
        )

        assertFalse(result.isCurrent)
        assertEquals("—", result.value)
        assertEquals("量測時間未知或無效，不能確認為目前值", result.headline)
    }

    @Test
    fun callbackFallbackTimestampIsNotPresentedAsCurrent() {
        val result = MeasureResultValuePolicy.present(
            kind = "ppg",
            value = "72",
            unit = "bpm",
            samples = 120,
            status = "valid_signal",
            measuredAtMillis = now - 1_000L,
            nowMillis = now,
            timestampSource = "callback_wall_clock_fallback"
        )

        assertFalse(result.isCurrent)
        assertEquals("—", result.value)
        assertEquals("量測時間未知或無效，不能確認為目前值", result.headline)
    }
}
