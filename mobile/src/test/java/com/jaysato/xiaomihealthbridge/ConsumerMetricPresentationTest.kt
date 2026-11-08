package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConsumerMetricPresentationTest {
    @Test fun calculationFailureIsNotShownAsMissingMeasurement() {
        val item = HealthConclusionEngine.buildSafely("hr", "心率") { error("storage failure") }
        val display = ConsumerMetricPresentation.from(item)
        assertEquals("讀取失敗", display.value)
        assertEquals("請重試", display.state)
        assertEquals("這次未能讀取資料，請稍後重試。", ConsumerMetricPresentation.explanation(item))
    }

    @Test fun historicalRawCountsStayOutOfConsumerResult() {
        val item = HealthConclusionEngine.Item(
            "ecg", "ECG", "3170 個原始事件 · 260 個研究解碼事件／2600 點",
            "", "", HealthConclusionEngine.Level.RESEARCH, null, true
        )
        assertEquals("已收到心電訊號", ConsumerMetricPresentation.from(item).value)
        assertEquals("尚無心律結果", ConsumerMetricPresentation.from(item).state)
        assertEquals("心電訊號已收到，但尚無可解讀的心律結果。", ConsumerMetricPresentation.explanation(item))
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

    @Test fun unavailableHrvAndBloodPressureGiveCorrectNextSteps() {
        val hrv = HealthConclusionEngine.Item("hrv", "HRV／壓力", "原始樣本", "", "",
            HealthConclusionEngine.Level.RESEARCH, null, true)
        val pressure = HealthConclusionEngine.Item("bp", "血壓", "—", "", "",
            HealthConclusionEngine.Level.UNAVAILABLE, null, false)
        assertEquals("尚無 HRV 讀值", ConsumerMetricPresentation.from(hrv).state)
        assertEquals("已取得心率變化資料，但尚無可信的 HRV 讀值。", ConsumerMetricPresentation.explanation(hrv))
        assertEquals("尚無紀錄", ConsumerMetricPresentation.from(pressure).value)
        assertEquals("尚無袖帶血壓紀錄；可輸入外部血壓計的讀值。", ConsumerMetricPresentation.explanation(pressure))
    }

    @Test fun physiologicalLookingNumberRemainsResearchOnlyWhenEvidenceIsUncalibrated() {
        val item = HealthConclusionEngine.Item(
            "hr", "心率", "72 bpm · 研究估算", "", "",
            HealthConclusionEngine.Level.RESEARCH, null, true
        )

        val display = ConsumerMetricPresentation.from(item)

        assertEquals("已收到心率訊號", display.value)
        assertEquals("尚無脈率讀值", display.state)
    }

    @Test fun explicitlyGatedResearchPulseKeepsReadableEstimate() {
        val ecg = HealthConclusionEngine.Item("ecg", "ECG", "估算心率 75 bpm（研究參考）", "", "",
            HealthConclusionEngine.Level.RESEARCH, null, true)
        val ppg = HealthConclusionEngine.Item("ppg", "PPG", "估算脈率 72 bpm（研究參考）", "", "",
            HealthConclusionEngine.Level.RESEARCH, null, true)
        assertEquals("75 bpm · 研究估算", ConsumerMetricPresentation.from(ecg).value)
        assertEquals("72 bpm · 研究估算", ConsumerMetricPresentation.from(ppg).value)
        assertEquals("心律未判定", ConsumerMetricPresentation.from(ecg).state)
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
        assertEquals("已收到溫度訊號", ConsumerMetricPresentation.from(item).value)
        assertEquals("溫度單位未確認", ConsumerMetricPresentation.from(item).state)
    }
}
