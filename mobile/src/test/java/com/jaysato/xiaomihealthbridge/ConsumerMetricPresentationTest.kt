package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsumerMetricPresentationTest {
    @Test fun historicalRawCountsStayOutOfConsumerResult() {
        val item = HealthConclusionEngine.Item(
            "ecg", "ECG", "3170 個原始事件 · 260 個研究解碼事件／2600 點",
            "", "", HealthConclusionEngine.Level.RESEARCH, null, true
        )
        assertEquals("重新量測", ConsumerMetricPresentation.from(item).value)
        assertEquals("尚無健康結果", ConsumerMetricPresentation.from(item).state)
    }

    @Test fun oxygenKeepsPhysiologicalValueWithoutDatabaseWording() {
        val item = HealthConclusionEngine.Item(
            "spo2", "血氧 SpO₂", "97% · 已記錄", "", "",
            HealthConclusionEngine.Level.GOOD, 80, true
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

    @Test fun physiologicalLookingNumberRemainsResearchOnlyWhenEvidenceIsUncalibrated() {
        val item = HealthConclusionEngine.Item(
            "hr", "心率", "72 bpm · 研究估算", "", "",
            HealthConclusionEngine.Level.RESEARCH, null, true
        )

        val display = ConsumerMetricPresentation.from(item)

        assertEquals("重新量測", display.value)
        assertEquals("尚無健康結果", display.state)
    }
}
