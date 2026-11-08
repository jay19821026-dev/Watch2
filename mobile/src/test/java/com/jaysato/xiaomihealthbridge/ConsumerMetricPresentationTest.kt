package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConsumerMetricPresentationTest {
    @Test fun historicalRawCountsStayOutOfConsumerResult() {
        val item = HealthConclusionEngine.Item(
            "ecg", "ECG", "3170 個原始事件 · 260 個研究解碼事件／2600 點",
            "", "", HealthConclusionEngine.Level.RESEARCH, null, true
        )
        assertEquals("重新量測", ConsumerMetricPresentation.from(item).value)
        assertEquals("尚無健康結果", ConsumerMetricPresentation.from(item).state)
        assertEquals("這次沒有可解讀的心律結果；請在手錶重新量測。", ConsumerMetricPresentation.explanation(item))
        assertFalse(ConsumerMetricPresentation.explanation(item).contains("事件"))
    }

    @Test fun oxygenKeepsPhysiologicalValueWithoutDatabaseWording() {
        val item = HealthConclusionEngine.Item(
            "spo2", "血氧 SpO₂", "97% · 已記錄", "", "",
            HealthConclusionEngine.Level.GOOD, 80, true,
            HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL
        )
        assertEquals("97%", ConsumerMetricPresentation.from(item).value)
        assertEquals("正常", ConsumerMetricPresentation.from(item).state)
    }

    @Test fun unsupportedGlucoseIsPlainLanguage() {
        val item = HealthConclusionEngine.Item(
            "glucose", "血糖", "不可用", "", "",
            HealthConclusionEngine.Level.UNAVAILABLE, null, false
        )
        assertEquals("未支援", ConsumerMetricPresentation.from(item).value)
    }

    @Test fun recordedCuffPressureShowsSingleReadingCategory() {
        val item = HealthConclusionEngine.Item(
            "bp", "血壓（袖帶）", "135 / 85 mmHg", "", "",
            HealthConclusionEngine.Level.WATCH, null, true,
            HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL
        )
        assertEquals("單次袖帶讀值：高血壓第1期。可與近期紀錄比較。", ConsumerMetricPresentation.explanation(item))
    }

    @Test fun physiologicalLookingNumberRemainsResearchOnlyWhenEvidenceIsUncalibrated() {
        val item = HealthConclusionEngine.Item(
            "hr", "心率", "72 bpm · 研究估算", "", "",
            HealthConclusionEngine.Level.RESEARCH, null, true
        )

        val display = ConsumerMetricPresentation.from(item)

        assertEquals("重新量測", display.value)
        assertEquals("尚無健康結果", display.state)
    }

    @Test fun recordedSleepAndResearchHrvKeepReadableValuesWithoutClaimingNormalHealth() {
        val sleep = HealthConclusionEngine.Item(
            "sleep", "睡眠", "近 7 日 42.2 小時紀錄區間", "", "",
            HealthConclusionEngine.Level.RESEARCH, null, true
        )
        val hrv = HealthConclusionEngine.Item(
            "hrv", "HRV／壓力", "RMSSD 35 ms · SDNN 50 ms", "", "",
            HealthConclusionEngine.Level.GOOD, 80, true
        )
        assertEquals("42.2 小時 · 7 日紀錄", ConsumerMetricPresentation.from(sleep).value)
        assertEquals("已記錄", ConsumerMetricPresentation.from(sleep).state)
        assertEquals("35 ms · 研究估算", ConsumerMetricPresentation.from(hrv).value)
        assertEquals("研究估算", ConsumerMetricPresentation.from(hrv).state)
    }

    @Test fun unverifiedNumericTextIsNotAutomaticallyPresentedAsPhysiology() {
        val item = HealthConclusionEngine.Item(
            "temp", "腕部溫度", "36.6 °C · 原始資料", "", "",
            HealthConclusionEngine.Level.GOOD, 80, true
        )
        assertEquals("重新量測", ConsumerMetricPresentation.from(item).value)
        assertEquals("尚無健康結果", ConsumerMetricPresentation.from(item).state)
    }
}
