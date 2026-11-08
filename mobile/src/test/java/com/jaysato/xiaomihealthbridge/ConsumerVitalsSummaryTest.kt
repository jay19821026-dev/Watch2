package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    @Test fun attentionReadingLeadsBeforeNormalReading() {
        fun item(id: String, value: String) = HealthConclusionEngine.Item(
            id, id, value, "", "", HealthConclusionEngine.Level.GOOD, null, true,
            HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL
        )
        val highHeart = HealthConclusionEngine.Report(
            listOf(item("spo2", "98%"), item("hr", "101 bpm")), "", ""
        )
        assertEquals("心率 101 bpm：偏高", ConsumerVitalsSummary.from(highHeart).headline)
        val lowOxygen = HealthConclusionEngine.Report(
            listOf(item("hr", "77 bpm"), item("spo2", "94%")), "", ""
        )
        assertEquals("血氧 94%：偏低", ConsumerVitalsSummary.from(lowOxygen).headline)
    }
}
