package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertTrue
import org.junit.Test

class VitalsUnavailableReasonTest {
    @Test
    fun reportsRawSamplesInsteadOfCallingThemMissing() {
        val result = VitalsUnavailableReason.describe(
            status = "no_events",
            rawSampleCount = 1,
            reason = "no valid bpm",
            measuredAt = 10_000L,
            now = 10_000L
        )

        assertTrue(result.contains("請重新量測"))
        assertTrue(!result.contains("樣本"))
    }

    @Test
    fun explainsUncalibratedRawSamplesWithoutConvertingThem() {
        val result = VitalsUnavailableReason.describe(
            status = "has_data",
            rawSampleCount = 3_896,
            reason = "no OEM percentage scale",
            measuredAt = 10_000L,
            now = 10_000L
        )

        assertTrue(result.contains("沒有取得可顯示的讀值"))
        assertTrue(!result.contains("3896"))
        assertTrue(!result.contains("no OEM"))
    }

    @Test
    fun distinguishesConditionsQualityAndStaleData() {
        assertTrue(VitalsUnavailableReason.describe("needs_oem_mode", 0, "", 0L, 10_000L).contains("原廠量測頁"))
        assertTrue(VitalsUnavailableReason.describe("needs_motion", 0, "", 0L, 10_000L).contains("走動"))
        assertTrue(VitalsUnavailableReason.describe("invalid_optical", 0, "", 10_000L, 10_000L).contains("訊號不清楚"))
        assertTrue(VitalsUnavailableReason.describe("has_data", 1, "", 1L, 10_000L, 1_000L).contains("已過期"))
        assertTrue(VitalsUnavailableReason.describe("no_events", 0, "", 0L, 10_000L).contains("目前沒有量測結果"))
    }

    @Test
    fun acquisitionBlockerTakesPriorityOverOldTimestamp() {
        assertTrue(VitalsUnavailableReason.describe("needs_oem_mode", 0, "", 1L, 10_000L, 1_000L).contains("原廠量測頁"))
        assertTrue(VitalsUnavailableReason.describe("off_wrist", 0, "", 1L, 10_000L, 1_000L).contains("貼合"))
    }

    @Test
    fun hasDataWithoutSamplesIsNotPresentedAsMissing() {
        val result = VitalsUnavailableReason.describe("has_data", 0, "", 10_000L, 10_000L)
        assertTrue(result.contains("沒有取得可顯示的讀值"))
    }

    @Test
    fun reportsEventsSeparatelyWhenNoSamplesWereParsed() {
        val result = VitalsUnavailableReason.describe(
            status = "invalid_sensor_values",
            rawSampleCount = 0,
            reason = "empty vector",
            measuredAt = 10_000L,
            now = 10_000L,
            rawEventCount = 2
        )
        assertTrue(result.contains("訊號不清楚"))
        assertTrue(!result.contains("事件"))
    }
}
