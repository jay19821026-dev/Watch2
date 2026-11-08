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
        assertTrue(result.recordedLines.isEmpty())
        assertEquals(2, result.researchLines.size)
        assertTrue(result.headline.contains("原始訊號"))
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
        assertEquals(listOf("心率：77 bpm"), result.recordedLines)
        assertEquals(listOf("ECG：221 個原始事件"), result.researchLines)
    }
}
