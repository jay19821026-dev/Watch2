package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConclusionSummaryPolicyTest {
    private fun item(level: HealthConclusionEngine.Level, available: Boolean = true, score: Int? = null) =
        HealthConclusionEngine.Item("test", "test", "—", "—", "—", level, score, available)

    @Test
    fun rawResearchSignalsDoNotCountAsComparablePhysiologyOrClaimGoodResults() {
        val summary = HealthConclusionSummaryPolicy.summarize(
            listOf(item(HealthConclusionEngine.Level.RESEARCH), item(HealthConclusionEngine.Level.UNAVAILABLE, false)),
            slotCount = 2
        )

        assertEquals("目前只有研究級原始訊號，沒有足夠可對照的生理數值", summary.first)
        assertEquals("可對照生理數值 0/2 項；歷史／未分級生理紀錄 0 項；研究級原始訊號 1 項。良好 0 · 觀察 0 · 需留意 0。非醫療診斷。", summary.second)
    }

    @Test
    fun clinicalReferenceCountsAreSeparateFromResearchSignalCount() {
        val summary = HealthConclusionSummaryPolicy.summarize(
            listOf(item(HealthConclusionEngine.Level.GOOD), item(HealthConclusionEngine.Level.RESEARCH)),
            slotCount = 2
        )

        assertEquals("已量測指標大致落在公開臨床參考帶內（研究參考）", summary.first)
        assertEquals("可對照生理數值 1/2 項；歷史／未分級生理紀錄 0 項；研究級原始訊號 1 項。良好 1 · 觀察 0 · 需留意 0。非醫療診斷。", summary.second)
    }

    @Test
    fun recordedPhysiologyIsNotMislabelledAsRawResearch() {
        val recorded = HealthConclusionEngine.Item(
            "hr", "心率", "75 bpm · 歷史紀錄", "來源：Health Connect", "非即時", 
            HealthConclusionEngine.Level.RESEARCH, null, true,
            HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL
        )
        val summary = HealthConclusionSummaryPolicy.summarize(listOf(recorded), 1)
        assertEquals("已有 1 項生理數值紀錄；缺少近期可對照量測", summary.first)
        assertEquals("可對照生理數值 0/1 項；歷史／未分級生理紀錄 1 項；研究級原始訊號 0 項。良好 0 · 觀察 0 · 需留意 0。非醫療診斷。", summary.second)
    }

    @Test
    fun researchAndUnavailableItemsNeverShowReferenceScore() {
        assertEquals(false, HealthConclusionSummaryPolicy.shouldShowScore(item(HealthConclusionEngine.Level.RESEARCH, score = 40)))
        assertEquals(false, HealthConclusionSummaryPolicy.shouldShowScore(item(HealthConclusionEngine.Level.UNAVAILABLE, score = 40)))
        assertEquals(true, HealthConclusionSummaryPolicy.shouldShowScore(item(HealthConclusionEngine.Level.GOOD, score = 80)))
    }
}
