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

        assertTrue(result.contains("已收到 1 筆原始樣本"))
        assertTrue(result.contains("未解析成有效的此項數值"))
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

        assertTrue(result.contains("已收到 3896 筆原始樣本"))
        assertTrue(result.contains("沒有可用的此項換算值"))
        assertTrue(result.contains("no OEM percentage scale"))
    }

    @Test
    fun distinguishesConditionsQualityAndStaleData() {
        assertTrue(VitalsUnavailableReason.describe("needs_oem_mode", 0, "", 0L, 10_000L).contains("原廠量測模式"))
        assertTrue(VitalsUnavailableReason.describe("needs_motion", 0, "", 0L, 10_000L).contains("走動"))
        assertTrue(VitalsUnavailableReason.describe("invalid_optical", 0, "", 10_000L, 10_000L).contains("品質"))
        assertTrue(VitalsUnavailableReason.describe("has_data", 1, "", 1L, 10_000L, 1_000L).contains("已過期"))
        assertTrue(VitalsUnavailableReason.describe("no_events", 0, "", 0L, 10_000L).contains("沒有收到感測事件"))
    }

    @Test
    fun acquisitionBlockerTakesPriorityOverOldTimestamp() {
        assertTrue(VitalsUnavailableReason.describe("needs_oem_mode", 0, "", 1L, 10_000L, 1_000L).contains("原廠量測模式"))
        assertTrue(VitalsUnavailableReason.describe("off_wrist", 0, "", 1L, 10_000L, 1_000L).contains("貼合"))
    }

    @Test
    fun hasDataWithoutSamplesIsNotPresentedAsMissing() {
        val result = VitalsUnavailableReason.describe("has_data", 0, "", 10_000L, 10_000L)
        assertTrue(result.contains("資料已收到"))
        assertTrue(result.contains("沒有可用的此項換算值"))
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
        assertTrue(result.contains("2 個原始事件"))
        assertTrue(result.contains("品質或數值格式未通過"))
    }
}
