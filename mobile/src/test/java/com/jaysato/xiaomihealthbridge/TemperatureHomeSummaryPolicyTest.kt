package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureHomeSummaryPolicyTest {
    @Test
    fun displaysSavedRawValueFromAnUnrecognizedSourceWithoutInventingCelsius() {
        val item = HealthConclusionEngine.temperatureResearchItem(
            value = 33.29,
            measuredAt = 0L,
            source = "MAX86178 board channel",
            now = 10_000L,
            timestampSource = "UNKNOWN"
        )

        val display = TemperatureHomeSummaryPolicy.resolve(null, item)

        assertEquals("已收到溫度訊號", display.value)
        assertEquals("量測時間未確認；單位也未確認，不能當成攝氏體溫。", display.detail)
    }

    @Test
    fun verifiedCelsiusTakesPrecedenceOverRawResearchValue() {
        val item = HealthConclusionEngine.temperatureResearchItem(
            value = 329.0,
            measuredAt = 0L,
            source = "MAX86178 board channel",
            now = 10_000L,
            timestampSource = "UNKNOWN"
        )

        val display = TemperatureHomeSummaryPolicy.resolve(36.6, item)

        assertEquals("36.6 °C", display.value)
        assertEquals("已驗證攝氏單位 · 最近一次裝置溫度", display.detail)
    }

    @Test
    fun unavailableOrUnparseableEvidenceRemainsAwaitingMeasurement() {
        val item = HealthConclusionEngine.temperatureResearchItem(
            value = null,
            measuredAt = 0L,
            source = "unknown",
            now = 10_000L
        )

        val display = TemperatureHomeSummaryPolicy.resolve(null, item)

        assertEquals("待量測", display.value)
        assertEquals("量測完成後顯示溫度結果", display.detail)
    }

    @Test
    fun staleResearchTemperatureIsIdentifiedAsHistoricalOnHomeSummary() {
        val now = System.currentTimeMillis()
        val item = HealthConclusionEngine.temperatureResearchItem(
            value = 33.29,
            measuredAt = now - 2 * 24 * 60 * 60 * 1000L,
            source = "watch_thermometer",
            now = now,
            timestampSource = "android_sensor_event_elapsed_realtime"
        )

        val display = TemperatureHomeSummaryPolicy.resolve(null, item)

        assertEquals("已收到溫度訊號", display.value)
        assertTrue(display.detail.contains("較早的紀錄"))
    }
}
