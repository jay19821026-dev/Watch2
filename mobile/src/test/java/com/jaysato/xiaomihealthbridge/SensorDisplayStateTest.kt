package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorDisplayStateTest {
    @Test
    fun healthServicesHeartRateUsesItsTrustedDatapointTimestamp() {
        val now = 10_000L
        assertEquals(
            SensorDisplayState.AVAILABLE,
            SensorDisplayState.from(
                samples = 1,
                status = "has_data",
                measuredAt = now - 500L,
                now = now,
                freshnessMs = 1_000L,
                kind = "whs_heart_rate",
                timestampSource = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
            )
        )
        assertEquals(
            SensorDisplayState.TIMESTAMP_UNKNOWN,
            SensorDisplayState.from(1, "has_data", now - 500L, now, 1_000L,
                "whs_heart_rate", "callback_receipt_time")
        )
    }

    @Test
    fun distinguishesNotMeasuredQualityCalibrationStaleAndRawOnly() {
        val now = 10_000L
        assertEquals(SensorDisplayState.NOT_MEASURED, SensorDisplayState.from(0, "not_measured", now, now, 1_000))
        assertEquals(SensorDisplayState.QUALITY_REJECTED, SensorDisplayState.from(20, "invalid_optical", now, now, 1_000))
        assertEquals(SensorDisplayState.QUALITY_REJECTED, SensorDisplayState.from(20, "rejected_quality", now, now, 1_000))
        assertEquals(SensorDisplayState.CONDITION_NOT_MET, SensorDisplayState.from(0, "needs_oem_mode", now, now, 1_000))
        assertEquals(SensorDisplayState.CONDITION_NOT_MET, SensorDisplayState.from(0, "needs_oem", now, now, 1_000))
        assertEquals(SensorDisplayState.CONDITION_NOT_MET, SensorDisplayState.from(0, "skipped_off_wrist", now, now, 1_000))
        assertEquals(SensorDisplayState.CALIBRATION_MISSING, SensorDisplayState.from(20, "research_signal_unscaled", now, now, 1_000))
        assertEquals(SensorDisplayState.CALIBRATION_MISSING, SensorDisplayState.from(20, "research_optical", now, now, 1_000))
        assertEquals(SensorDisplayState.STALE, SensorDisplayState.from(20, "has_data", now - 1_001, now, 1_000))
        assertEquals(SensorDisplayState.RAW_ONLY, SensorDisplayState.from(20, "unknown_status", now, now, 1_000))
        assertEquals(SensorDisplayState.SIGNAL_VALID_RESEARCH, SensorDisplayState.from(20, "valid_signal", now, now, 1_000))
        assertEquals(SensorDisplayState.AVAILABLE, SensorDisplayState.from(20, "has_data", now, now, 1_000))
        val trustedEventTime = "android_sensor_event_elapsed_realtime"
        assertEquals(SensorDisplayState.CALIBRATION_MISSING, SensorDisplayState.from(512, "has_data", now, now, 1_000, "spo2_raw", trustedEventTime))
        assertEquals(SensorDisplayState.CALIBRATION_MISSING, SensorDisplayState.from(512, "has_data", now, now, 1_000, "mi_spo2", trustedEventTime))
        assertEquals(SensorDisplayState.CALIBRATION_MISSING, SensorDisplayState.from(512, "has_data", now, now, 1_000, "bioz", trustedEventTime))
        assertEquals(SensorDisplayState.CALIBRATION_MISSING, SensorDisplayState.from(512, "has_data", now, now, 1_000, "bioz_respiration", trustedEventTime))
        assertEquals(SensorDisplayState.STALE, SensorDisplayState.from(512, "has_data", now - 1_001, now, 1_000, "spo2_raw", trustedEventTime))
        assertEquals(SensorDisplayState.RAW_ONLY, SensorDisplayState.from(512, "sync_error", now, now, 1_000, "spo2_raw", trustedEventTime))
        assertEquals(SensorDisplayState.DERIVED_ESTIMATE, SensorDisplayState.from(20, "derived_estimate", now, now, 1_000))
    }

    @Test
    fun futureTimestampsHaveAnExplicitInvalidState() {
        val state = SensorDisplayState.from(1, "has_data", 10_001, 10_000, 1_000)
        assertEquals(SensorDisplayState.FUTURE_TIMESTAMP, state)
        assertEquals("future_timestamp", state.exportValue)
        assertEquals("future_timestamp", SensorSuiteExportPolicy.freshnessReason(state, 10_001, "ppg", "android_sensor_event_elapsed_realtime"))
        assertEquals(false, SensorSuiteExportPolicy.countsAsFreshRawData(true, state))
    }

    @Test
    fun callbackWallClockFallbackIsNotPresentedAsFreshSensorData() {
        val state = SensorDisplayState.from(
            samples = 24,
            status = "has_data",
            measuredAt = 10_000,
            now = 10_000,
            freshnessMs = 30_000,
            kind = "ppg",
            timestampSource = "callback_wall_clock_fallback"
        )
        assertEquals(SensorDisplayState.TIMESTAMP_UNKNOWN, state)
    }

    @Test
    fun unmetMeasurementConditionIsNotHiddenByUnknownTimestampProvenance() {
        listOf("off_wrist", "skipped_off_wrist", "skipped_charging", "needs_contact").forEach { status ->
            assertEquals(
                SensorDisplayState.CONDITION_NOT_MET,
                SensorDisplayState.from(
                    samples = 4,
                    status = status,
                    measuredAt = 100_000L,
                    now = 100_000L,
                    freshnessMs = 30_000L,
                    kind = "ppg"
                )
            )
        }
    }

    @Test
    fun missingTimestampProvenanceIsNotMisclassifiedAsRecentOrStale() {
        val now = 100_000L
        assertEquals(
            SensorDisplayState.TIMESTAMP_UNKNOWN,
            SensorDisplayState.from(24, "has_data", now - 60_000L, now, 30_000L, "ppg")
        )
        assertEquals(
            SensorDisplayState.TIMESTAMP_UNKNOWN,
            SensorDisplayState.from(24, "has_data", now, now, 30_000L, "ppg")
        )
    }

    @Test
    fun untrustedTimestampDoesNotImplyAnEventWasReceived() {
        val state = SensorDisplayState.from(
            samples = 0,
            status = "not_measured",
            measuredAt = 0,
            now = 10_000,
            freshnessMs = 30_000,
            kind = "ppg",
            timestampSource = "watch_sample_timestamp_missing"
        )
        assertEquals(SensorDisplayState.NOT_MEASURED, state)
    }

    @Test
    fun readingsOlderThanTheSharedFreshnessWindowAreStale() {
        val now = 100_000_000L
        assertEquals(
            SensorDisplayState.STALE,
            SensorDisplayState.from(512, "has_data", now - 7 * 60 * 60 * 1_000L, now, SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS, "body_temperature_raw", "android_sensor_event_elapsed_realtime")
        )
    }

    @Test
    fun invalidOpticalQualityTakesPrecedenceOverMissingCalibration() {
        assertEquals(SensorDisplayState.QUALITY_REJECTED, SensorDisplayState.from(512, "invalid_optical", 10_000, 10_000, 1_000, "spo2_raw"))
    }

    @Test
    fun invalidRawSignalIsNotDisplayedAsMissingWhenNoUsableSamplesWereParsed() {
        val now = 10_000L
        assertEquals(SensorDisplayState.QUALITY_REJECTED, SensorDisplayState.from(0, "invalid_optical", now, now, 1_000, "lifeq_ott"))
        assertEquals(SensorDisplayState.QUALITY_REJECTED, SensorDisplayState.from(0, "invalid_sensor_values", now, now, 1_000, "thermometer"))
        assertEquals(SensorDisplayState.QUALITY_REJECTED, SensorDisplayState.from(0, "rejected_quality", now, now, 1_000, "ecg"))
        assertEquals(SensorDisplayState.QUALITY_REJECTED, SensorDisplayState.from(0, "invalid_optical", 0L, now, 1_000, "lifeq_ott"))
        assertEquals(SensorDisplayState.STALE, SensorDisplayState.from(0, "invalid_optical", 1_000L, now, 1_000, "lifeq_ott"))
    }

    @Test
    fun staleTimestampTakesPrecedenceOverContactStateWhenOldSamplesExist() {
        assertEquals(SensorDisplayState.STALE, SensorDisplayState.from(3, "off_wrist", 8_000, 10_000, 1_000))
        assertEquals(SensorDisplayState.CONDITION_NOT_MET, SensorDisplayState.from(0, "off_wrist", 0, 10_000, 1_000))
    }

    @Test
    fun expiredNoEventRowsAreStaleWhileCurrentNoEventRowsExplainTheCondition() {
        assertEquals(SensorDisplayState.STALE, SensorDisplayState.from(0, "needs_motion", 8_000, 10_000, 1_000))
        assertEquals(SensorDisplayState.CONDITION_NOT_MET, SensorDisplayState.from(0, "needs_motion", 9_500, 10_000, 1_000))
        assertEquals(SensorDisplayState.CONDITION_NOT_MET, SensorDisplayState.from(0, "needs_motion", 0, 10_000, 1_000))
    }

    @Test
    fun missingFutureAndExpiredSamplesNeverCountAsFreshOrQualified() {
        val now = 10_000_000L
        listOf(
            0L to SensorDisplayState.TIMESTAMP_UNKNOWN,
            now + 1L to SensorDisplayState.FUTURE_TIMESTAMP,
            (now - SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS - 1L) to SensorDisplayState.STALE
        ).forEach { (measuredAt, expected) ->
            val state = SensorDisplayState.from(
                samples = 20,
                status = "valid_signal",
                measuredAt = measuredAt,
                now = now,
                freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS
            )

            assertEquals(expected, state)
            assertEquals(false, SensorSuiteExportPolicy.countsAsFresh(state))
            assertEquals(false, SensorSuiteExportPolicy.countsAsAcceptedQuality(state, 99))
        }
    }

    @Test
    fun missingTimestampIsNotMisreportedAsStaleOrFresh() {
        val state = SensorDisplayState.from(4, "has_data", 0L, 10_000L, 1_000L)
        assertEquals(SensorDisplayState.TIMESTAMP_UNKNOWN, state)
        assertEquals("missing_timestamp", SensorSuiteExportPolicy.freshnessReason(
            state, 0L, "ppg", "android_sensor_event_elapsed_realtime"
        ))
        assertEquals(false, SensorSuiteExportPolicy.countsAsFreshRawData(true, state))
        assertEquals(false, SensorSuiteExportPolicy.countsAsFresh(state))
    }

    @Test
    fun untrustedPresentTimestampIsNotReportedWithinFreshnessWindow() {
        val state = SensorDisplayState.from(
            samples = 4,
            status = "has_data",
            measuredAt = 9_500L,
            now = 10_000L,
            freshnessMs = 1_000L,
            kind = "ppg",
            timestampSource = "callback_wall_clock_fallback"
        )

        assertEquals(SensorDisplayState.TIMESTAMP_UNKNOWN, state)
        assertEquals("timestamp_source_untrusted", SensorSuiteExportPolicy.freshnessReason(
            state, 9_500L, "ppg", "callback_wall_clock_fallback"
        ))
        assertEquals(false, SensorSuiteExportPolicy.countsAsFreshRawData(true, state))
    }

    @Test
    fun negativeTimeWithTrustedSourceIsNotReportedAsMissing() {
        val source = "android_sensor_event_elapsed_realtime"
        val state = SensorDisplayState.from(
            samples = 4,
            status = "has_data",
            measuredAt = -1L,
            now = 10_000L,
            freshnessMs = 1_000L,
            kind = "ppg",
            timestampSource = source
        )

        assertEquals(SensorDisplayState.TIMESTAMP_UNKNOWN, state)
        assertEquals("invalid_timestamp", SensorSuiteExportPolicy.freshnessReason(state, -1L, "ppg", source))
    }

    @Test
    fun rejectedUncalibratedAndRawOnlyStatesDoNotInflateFreshOrQualityCounts() {
        listOf(
            SensorDisplayState.QUALITY_REJECTED,
            SensorDisplayState.CALIBRATION_MISSING,
            SensorDisplayState.RAW_ONLY,
            SensorDisplayState.CONDITION_NOT_MET,
            SensorDisplayState.NOT_MEASURED
        ).forEach { state ->
            assertEquals(false, SensorSuiteExportPolicy.countsAsFresh(state))
            assertEquals(false, SensorSuiteExportPolicy.countsAsAcceptedQuality(state, 99))
        }
    }

    @Test
    fun freshCountRequiresFreshClassifiedHardwareDataAndQualityThreshold() {
        assertEquals(true, SensorSuiteExportPolicy.countsAsFresh(SensorDisplayState.AVAILABLE))
        assertEquals(true, SensorSuiteExportPolicy.countsAsFresh(SensorDisplayState.SIGNAL_VALID_RESEARCH))
        assertEquals(true, SensorSuiteExportPolicy.countsAsAcceptedQuality(
            SensorDisplayState.SIGNAL_VALID_RESEARCH, 50
        ))
        assertEquals(false, SensorSuiteExportPolicy.countsAsAcceptedQuality(
            SensorDisplayState.SIGNAL_VALID_RESEARCH, 49
        ))
        assertEquals(false, SensorSuiteExportPolicy.countsAsAcceptedQuality(
            SensorDisplayState.AVAILABLE, 100
        ))
        assertEquals(false, SensorSuiteExportPolicy.countsAsAcceptedQuality(
            SensorDisplayState.CALIBRATION_MISSING, 100
        ))
    }

    @Test
    fun freshRawDataIsCountedSeparatelyFromInterpretableQuality() {
        listOf(
            SensorDisplayState.CALIBRATION_MISSING,
            SensorDisplayState.RAW_ONLY,
            SensorDisplayState.QUALITY_REJECTED,
            SensorDisplayState.AVAILABLE
        ).forEach { state ->
            assertEquals(true, SensorSuiteExportPolicy.countsAsFreshRawData(true, state))
        }
        assertEquals(false, SensorSuiteExportPolicy.countsAsFreshRawData(true, SensorDisplayState.STALE))
        assertEquals(false, SensorSuiteExportPolicy.countsAsFreshRawData(true, SensorDisplayState.TIMESTAMP_UNKNOWN))
        assertEquals(false, SensorSuiteExportPolicy.countsAsFreshRawData(false, SensorDisplayState.AVAILABLE))
        assertEquals(false, SensorSuiteExportPolicy.countsAsAcceptedQuality(
            SensorDisplayState.CALIBRATION_MISSING, 100
        ))
    }

    @Test
    fun rawSamplesAreRetainedAsDataEvenWhenTimestampIsMissingOrExpired() {
        val now = 10_000_000L
        val window = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS
        listOf(
            0L to SensorDisplayState.TIMESTAMP_UNKNOWN,
            (now - window - 1L) to SensorDisplayState.STALE
        ).forEach { (measuredAt, expected) ->
            val state = SensorDisplayState.from(12, "has_data", measuredAt, now, window)
            assertEquals(true, SensorSuiteExportPolicy.hasRawData(12, "hardware_event", "has_data"))
            assertEquals(expected, state)
            assertEquals(false, SensorSuiteExportPolicy.countsAsFresh(state))
        }
        assertEquals(false, SensorSuiteExportPolicy.hasRawData(0, "hardware_event", "has_data"))
        assertEquals(false, SensorSuiteExportPolicy.hasRawData(12, "derived_estimate", "derived_estimate"))
        assertEquals(false, SensorSuiteExportPolicy.hasRawData(12, "not_measured", "no_events"))
        assertEquals(false, SensorSuiteExportPolicy.hasRawData(12, "not_measured", "skipped_charging"))
        assertEquals(false, SensorSuiteExportPolicy.hasRawData(12, "hardware_event", "skipped_charging"))
    }

    @Test
    fun freshnessReasonExplainsMissingFutureExpiredAndCurrentTimestamps() {
        val now = 10_000_000L
        val window = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS
        fun reason(measuredAt: Long): String {
            val source = "android_sensor_event_elapsed_realtime"
            val state = SensorDisplayState.from(
                samples = 1,
                status = "has_data",
                measuredAt = measuredAt,
                now = now,
                freshnessMs = window,
                kind = "ppg",
                timestampSource = source
            )
            return SensorSuiteExportPolicy.freshnessReason(state, measuredAt, "ppg", source)
        }
        assertEquals("missing_timestamp", reason(0L))
        assertEquals("future_timestamp", reason(now + 1L))
        assertEquals("expired", reason(now - window - 1L))
        assertEquals("within_window", reason(now - window))
    }
}
