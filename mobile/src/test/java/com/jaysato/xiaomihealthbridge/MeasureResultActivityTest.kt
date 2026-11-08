package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureResultActivityTest {
    private val now = 100_000_000L

    @Test
    fun ppgUsesOnlyHeartRateFromItsOwnMeasurementWindow() {
        val source = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        val ppgSource = "android_sensor_event_elapsed_realtime"
        val matched = PpgSupplementalHeartRatePolicy.match(now - 60_000L, 400, ppgSource,
            78f, now - 55_000L, source, now)
        val old = PpgSupplementalHeartRatePolicy.match(now - 60_000L, 400, ppgSource,
            78f, now - 500_000L, source, now)
        val noPpg = PpgSupplementalHeartRatePolicy.match(now - 60_000L, 0, ppgSource,
            78f, now - 55_000L, source, now)
        assertEquals(78f, matched?.bpm)
        assertEquals(null, old)
        assertEquals(null, noPpg)
    }

    @Test
    fun ppgWithMissingSensorClockCanShowNearbyIndependentHeartRate() {
        val source = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        val nearby = PpgSupplementalHeartRatePolicy.match(
            ppgMeasuredAt = 0L, ppgEventCount = 8,
            ppgTimestampSource = "sensor_event_timestamp_missing", bpm = 82f,
            heartRateMeasuredAt = now - 35_000L, timestampSource = source, now = now,
            sessionStartedAt = now - 80_000L, publishedAt = now - 60_000L
        )
        val stale = PpgSupplementalHeartRatePolicy.match(
            ppgMeasuredAt = 0L, ppgEventCount = 8,
            ppgTimestampSource = "sensor_event_timestamp_missing", bpm = 82f,
            heartRateMeasuredAt = now - 700_000L, timestampSource = source, now = now,
            sessionStartedAt = now - 80_000L, publishedAt = now - 60_000L
        )
        assertEquals(82f, nearby?.bpm)
        assertEquals(null, stale)
    }

    @Test
    fun typedHeartRateHasPlainLanguageContextWithoutClaimingRhythmDiagnosis() {
        assertTrue(HeartRateMeaningPolicy.explain(55f).contains("偏低"))
        assertTrue(HeartRateMeaningPolicy.explain(75f).contains("參考範圍"))
        assertTrue(HeartRateMeaningPolicy.explain(115f).contains("偏高"))
        assertTrue(HeartRateMeaningPolicy.explain(115f).contains("再測"))
        assertTrue(HeartRateMeaningPolicy.explain(75f).contains("活動時的範圍不同"))
    }

    @Test
    fun rawEventCountIsNeverShownAsHealthResult() {
        val result = MeasureResultValuePolicy.present(
            kind = "ecg", value = "45", unit = "原始資料點／事件", samples = 45,
            status = "research_signal_unscaled", measuredAtMillis = 0L, nowMillis = now
        )
        assertFalse(result.isCurrent)
        assertEquals("—", result.value)
        assertEquals("這次沒有可顯示的健康讀值", result.headline)
    }

    @Test
    fun oldPulseEstimateAndRawCountAreHidden() {
        val staleTime = now - SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS - 1L
        val estimate = MeasureResultValuePolicy.present("ppg", "72", "研究估算 bpm", 45,
            "research_optical", staleTime, now, "android_sensor_event_elapsed_realtime")
        val count = MeasureResultValuePolicy.present("ppg", "45", "原始資料點／事件", 45,
            "research_optical", staleTime, now, "android_sensor_event_elapsed_realtime")
        assertEquals("—", estimate.value)
        assertEquals("—", count.value)
        assertFalse(count.isCurrent)
    }

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
        assertEquals("量測時間未確認", sensorClock)
        assertEquals("量測時間未確認", MeasureResultTimestampPresentation.label(now, null, now))
        assertEquals("量測時間未確認", MeasureResultTimestampPresentation.label(
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
        assertEquals("量測時間未確認", result.headline)
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
        assertEquals("量測時間未確認", result.headline)
    }

    @Test
    fun legacyOpticalBloodPressureNeverDisplaysCachedMillimetersOfMercury() {
        val result = MeasureResultValuePolicy.present(
            kind = "optical_bp",
            value = "128/82",
            unit = "mmHg",
            samples = 120,
            status = "has_data",
            measuredAtMillis = now - 1_000L,
            nowMillis = now,
            timestampSource = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        )

        assertFalse(result.isCurrent)
        assertEquals("—", result.value)
        assertEquals("", result.unit)
        assertEquals("手錶血壓量測不可用", result.headline)
    }
}
