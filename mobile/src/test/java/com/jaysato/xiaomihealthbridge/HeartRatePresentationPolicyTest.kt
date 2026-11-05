package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRatePresentationPolicyTest {
    @Test
    fun freshHealthServicesValueRemainsDisplayableWhenWearSensorDisagrees() {
        val now = 100_000L
        assertEquals(
            82f,
            HeartRatePresentationPolicy.visibleHealthServicesBpm(
                82f, now - 1_000L, HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, now, 600_000L
            )
        )
        assertEquals(
            "離腕訊號不一致，請確認佩戴",
            HeartRatePresentationPolicy.wearContext(true, false, now - 1_000L, now, "android_sensor_event_elapsed_realtime")
        )
    }

    @Test
    fun invalidOrStaleHealthServicesValuesRemainHidden() {
        val now = 100_000L
        val source = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        assertNull(HeartRatePresentationPolicy.visibleHealthServicesBpm(221f, now - 1_000L, source, now, 600_000L))
        assertNull(HeartRatePresentationPolicy.visibleHealthServicesBpm(82f, now - 700_000L, source, now, 600_000L))
        assertNull(HeartRatePresentationPolicy.visibleHealthServicesBpm(82f, now - 1_000L, null, now, 600_000L))
        assertNull(HeartRatePresentationPolicy.visibleHealthServicesBpm(82f, now + 1L, source, now, 600_000L))
        assertEquals(
            "佩戴狀態未知",
            HeartRatePresentationPolicy.wearContext(false, false, now - 1_000L, now, "android_sensor_event_elapsed_realtime")
        )
    }

    @Test
    fun homeTimestampIncludesDateForPriorDayButKeepsSameDayCompact() {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDate.of(2026, 10, 10).atTime(0, 11).atZone(zone).toInstant().toEpochMilli()
        val priorDay = java.time.LocalDate.of(2026, 10, 9).atTime(18, 49).atZone(zone).toInstant().toEpochMilli()
        val sameDay = java.time.LocalDate.of(2026, 10, 10).atTime(0, 5).atZone(zone).toInstant().toEpochMilli()

        assertTrue(HeartRatePresentationPolicy.homeTimestampLabel(priorDay, now) !=
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(priorDay)))
        assertEquals(
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(sameDay)),
            HeartRatePresentationPolicy.homeTimestampLabel(sameDay, now)
        )
    }

    @Test
    fun repeatedFeatureSnapshotsCannotRefreshUntrustedOrOldWearState() {
        val now = 100_000L
        assertEquals(
            "佩戴狀態未知",
            HeartRatePresentationPolicy.wearContext(true, true, now - 1_000L, now, "watch_sample_timestamp_source_unreported")
        )
        assertEquals(
            "佩戴狀態未知",
            HeartRatePresentationPolicy.wearContext(
                true, true, now - HeartRatePresentationPolicy.WEAR_STATE_FRESHNESS_MILLIS - 1L,
                now, "android_sensor_event_elapsed_realtime"
            )
        )
    }

    @Test
    fun rawSensorRateRequiresTrustedFreshEventTimeNotRangeScore() {
        val now = 1_700_000_000_000L
        val source = "android_sensor_event_elapsed_realtime"
        fun bpm(time: Long = now, timestampSource: String? = source, status: String = "valid_signal") =
            HeartRatePresentationPolicy.visibleRawSensorBpm(80f, status, time, timestampSource, now)

        assertEquals(80f, bpm())
        assertEquals(80f, bpm(status = "has_data"))
        assertNull(bpm(timestampSource = null))
        assertNull(bpm(timestampSource = "watch_sample_timestamp_source_unreported"))
        assertNull(bpm(time = now + 1L))
        assertNull(bpm(time = now - HeartRatePresentationPolicy.HOME_HISTORY_WINDOW_MILLIS - 1L))
        assertNull(bpm(status = "research_signal_unscaled"))
        assertNull(HeartRatePresentationPolicy.visibleRawSensorBpm(Float.NaN, "has_data", now, source, now))
        assertNull(HeartRatePresentationPolicy.visibleRawSensorBpm(221f, "has_data", now, source, now))
    }

    @Test
    fun detailRateRequiresPlausibleValueAndFreshTrustedSourceTime() {
        val now = 1_700_000_000_000L
        assertEquals(72f, HeartRatePresentationPolicy.visibleDetailBpm(
            "heart_rate_raw", 72f, "has_data", now - 1_000L,
            "android_sensor_event_elapsed_realtime", now
        ))
        assertNull(HeartRatePresentationPolicy.visibleDetailBpm(
            "heart_rate_raw", 72f, "has_data", now - 1_000L, "unknown", now
        ))
        assertNull(HeartRatePresentationPolicy.visibleDetailBpm(
            "heart_rate_raw", 72f, "has_data", now + 1L,
            "android_sensor_event_elapsed_realtime", now
        ))
        assertEquals(72f, HeartRatePresentationPolicy.visibleDetailBpm(
            "whs_heart_rate", 72f, "has_data", now - 1_000L,
            HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, now
        ))
    }

    @Test
    fun detailRateLabelsOlderAcceptedValuesAsHistorical() {
        val now = 1_700_000_000_000L
        val sensorSource = "android_sensor_event_elapsed_realtime"

        assertTrue(HeartRatePresentationPolicy.detailTimeDescription(
            "heart_rate_raw", now - 6 * 60_000L, sensorSource, now
        ).contains("歷史讀值"))
        assertTrue(HeartRatePresentationPolicy.detailTimeDescription(
            "heart_rate_raw", now - 60_000L, sensorSource, now
        ).contains("近期讀值"))
        assertTrue(HeartRatePresentationPolicy.detailTimeDescription(
            "heart_rate_raw", now, "untrusted", now
        ).contains("量測時間未確認"))
    }

    @Test
    fun detailRateUsesExplicitUnavailableLabelInsteadOfDash() {
        assertEquals("讀值 72 bpm", HeartRatePresentationPolicy.detailValueLabel(72f))
        assertTrue(HeartRatePresentationPolicy.detailValueLabel(null).contains("讀值無法判定"))
        assertTrue(HeartRatePresentationPolicy.detailValueLabel(Float.NaN).contains("讀值無法判定"))
    }
}
