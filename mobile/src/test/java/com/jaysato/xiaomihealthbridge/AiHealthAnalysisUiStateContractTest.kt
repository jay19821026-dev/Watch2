package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHealthAnalysisUiStateContractTest {
    @Test
    fun longAnalysisPanelsAreHiddenBehindTheDetailsControl() {
        assertTrue(AiHealthAnalysisUiPolicy.isTechnicalDetailsPanel("本機規則檢查面向"))
        assertTrue(AiHealthAnalysisUiPolicy.isTechnicalDetailsPanel("資料觀察"))
        assertTrue(AiHealthAnalysisUiPolicy.isTechnicalDetailsPanel("Health Connect｜近 7 日活動觀測"))
        assertFalse(AiHealthAnalysisUiPolicy.isTechnicalDetailsPanel("健康量測判讀"))
    }

    @Test
    fun unavailableMeasurementNeverRendersAsNormal() {
        val display = display(
            item(
                id = "spo2",
                value = "—",
                level = HealthConclusionEngine.Level.UNAVAILABLE,
                available = false
            )
        )

        assertEquals("尚無結果", display.state)
        assertFalse(display.state == "正常")
    }

    @Test
    fun researchMeasurementIsNotPromotedToNormalOrAttention() {
        val display = display(
            item(
                id = "ecg",
                value = "3170 個原始事件 · 2600 點",
                level = HealthConclusionEngine.Level.RESEARCH
            )
        )

        assertEquals("研究資料", display.state)
        assertFalse(display.state == "正常" || display.state == "需留意")
        assertTrue(display.value.contains("3170"))
    }

    @Test
    fun partialSensorFailureDoesNotHideOtherAvailableMeasurements() {
        val rendered = listOf(
            item("spo2", "—", HealthConclusionEngine.Level.UNAVAILABLE, available = false),
            item("hr", "72 bpm", HealthConclusionEngine.Level.GOOD, score = 80)
        ).map(::display)

        assertEquals(2, rendered.size)
        assertEquals("尚無結果", rendered[0].state)
        assertEquals("72 bpm", rendered[1].value)
        assertEquals("正常", rendered[1].state)
    }

    @Test
    fun measuredZeroIsDifferentFromMissingValue() {
        val measuredZero = display(
            item("raw_count", "0 點", HealthConclusionEngine.Level.RESEARCH)
        )
        val missing = display(
            item("spo2", "—", HealthConclusionEngine.Level.UNAVAILABLE, available = false)
        )

        assertEquals("0 點", measuredZero.value)
        assertEquals("研究資料", measuredZero.state)
        assertEquals("尚無結果", missing.state)
        assertFalse(measuredZero.value == missing.value)
    }

    @Test
    fun unscoredResearchAndUnavailableItemsDoNotRenderScore() {
        val research = item("ecg", "研究波形", HealthConclusionEngine.Level.RESEARCH)
        val unavailable = item("temperature", "—", HealthConclusionEngine.Level.UNAVAILABLE, available = false)

        assertEquals(null, research.score)
        assertEquals(null, unavailable.score)
        assertFalse(HealthConclusionSummaryPolicy.shouldShowScore(research))
        assertFalse(HealthConclusionSummaryPolicy.shouldShowScore(unavailable))
    }

    private fun display(item: HealthConclusionEngine.Item) = ConsumerMetricPresentation.from(item)

    private fun item(
        id: String,
        value: String,
        level: HealthConclusionEngine.Level,
        score: Int? = null,
        available: Boolean = true
    ) = HealthConclusionEngine.Item(
        id = id,
        title = id,
        valueText = value,
        clinicalCompare = "",
        conclusion = "",
        level = level,
        score = score,
        available = available
    )
}
