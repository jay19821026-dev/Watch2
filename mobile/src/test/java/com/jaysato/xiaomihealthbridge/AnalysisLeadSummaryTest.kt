package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisLeadSummaryTest {
    @Test fun rawEcgAndBiozDoNotBecomeMeasuredHealthResults() {
        val items = listOf("ECG", "BioZ").map { title ->
            HealthConclusionEngine.Item(title.lowercase(), title, "221 個原始事件",
                "未校準", "已保存原始資料", HealthConclusionEngine.Level.RESEARCH, null, true)
        }
        val result = AnalysisLeadSummary.from(items)
        assertTrue(result.displayLines.isEmpty())
        assertTrue(result.researchLines.isEmpty())
        assertEquals("手錶訊號已收到，尚無可用健康讀值", result.headline)
        assertFalse(result.headline.contains("正常"))
    }

    @Test fun measuredResultLeadsAndRawEvidenceRemainsSeparate() {
        val measured = HealthConclusionEngine.Item(
            "heart_rate", "心率", "77 bpm", "", "已記錄", HealthConclusionEngine.Level.GOOD,
            null, true, HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL
        )
        val raw = HealthConclusionEngine.Item(
            "ecg", "ECG", "221 個原始事件", "", "未校準", HealthConclusionEngine.Level.RESEARCH,
            null, true
        )
        val result = AnalysisLeadSummary.from(listOf(raw, measured))
        assertEquals("心率：77 bpm · 正常", result.headline)
        assertTrue(result.displayLines.isEmpty())
        assertTrue(result.researchLines.isEmpty())
    }

    @Test fun readableReferenceRecordsAppearWithoutRawCounts() {
        val sleep = HealthConclusionEngine.Item(
            "sleep", "睡眠", "近 7 日 42.2 小時紀錄區間", "", "",
            HealthConclusionEngine.Level.RESEARCH, null, true
        )
        val ecg = HealthConclusionEngine.Item(
            "ecg", "ECG", "216 個原始事件", "", "",
            HealthConclusionEngine.Level.RESEARCH, null, true
        )
        val result = AnalysisLeadSummary.from(listOf(sleep, ecg))
        assertEquals("睡眠：42.2 小時 · 7 日紀錄 · 已記錄", result.headline)
        assertTrue(result.displayLines.isEmpty())
        assertFalse(result.headline.contains("216"))
    }
}
