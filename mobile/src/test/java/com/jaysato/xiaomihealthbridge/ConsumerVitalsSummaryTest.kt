package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerVitalsSummaryTest {
    @Test fun oxygenStatesRangeBeforeAnyMeasurementAction() {
        assertTrue(ConsumerVitalsSummary.oxygen(94.0).headline.contains("偏低"))
        assertTrue(ConsumerVitalsSummary.oxygen(95.0).headline.contains("正常範圍"))
        assertTrue(ConsumerVitalsSummary.oxygen(94.0).detail.contains("95–100%"))
    }

    @Test fun heartRateDoesNotMislabelUnmarkedSampleAsResting() {
        val copy = ConsumerVitalsSummary.heartRate(101.0)
        assertTrue(copy.headline.contains("101 bpm"))
        assertTrue(copy.detail.contains("高於成人靜息參考"))
        assertTrue(ConsumerVitalsSummary.heartRateLabel(101.0) == "偏高")
        assertTrue(ConsumerVitalsSummary.heartRateLabel(80.0) == "正常範圍")
    }
}
