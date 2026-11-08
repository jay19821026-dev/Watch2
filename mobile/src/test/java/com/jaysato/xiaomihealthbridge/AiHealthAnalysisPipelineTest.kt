package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class AiHealthAnalysisPipelineTest {
    @Test fun insightFailureKeepsIndependentMeasurementReportAndAvoidsPublishingFallback() = runBlocking {
        val report = reportWithHeartRate()
        val result = AiHealthAnalysisPipeline.load(
            readInsights = { error("insights failed") },
            readMeasurements = { report }
        )

        assertFalse(result.insightsAvailable)
        assertEquals(report, result.measurementReport)
        assertTrue(result.engineAnalysis.measurements.findings.any { it.id == "heart" })
        assertTrue(result.explanation.measurementDetail.isNotBlank())
        assertTrue(result.warnings.any { it.contains("健康趨勢暫時無法讀取") && !it.contains("Exception") })
    }

    @Test fun statisticsFailurePreservesPerMetricResultsAndLeavesCompositeUnscored() = runBlocking {
        val report = reportWithHeartRate()
        val result = AiHealthAnalysisPipeline.load(
            readInsights = ::emptyInsights,
            readMeasurements = { report },
            analyzeEngine = { _, _ -> error("statistics failed") }
        )

        assertEquals("heart", result.engineAnalysis.measurements.findings.single().id)
        assertNull(result.engineAnalysis.recovery.score)
        assertTrue(result.warnings.any { it.contains("綜合趨勢暫時無法產生") && !it.contains("Exception") })
    }

    @Test fun measurementReadFailureStillReturnsUnscoredReportAndTrendSummary() = runBlocking {
        val result = AiHealthAnalysisPipeline.load(
            readInsights = ::emptyInsights,
            readMeasurements = { error("measurement read failed") }
        )

        assertTrue(result.measurementReport.items.isNotEmpty())
        assertTrue(result.measurementReport.items.all { it.score == null && !it.available })
        assertNull(result.engineAnalysis.recovery.score)
        assertTrue(result.warnings.any { it.contains("部分量測暫時無法讀取") && !it.contains("Exception") })
    }

    @Test fun explanationFailureDoesNotDiscardMeasurementOrStatisticsResults() = runBlocking {
        val report = reportWithHeartRate()
        val result = AiHealthAnalysisPipeline.load(
            readInsights = ::emptyInsights,
            readMeasurements = { report },
            analyzeExplanation = { _, _ -> error("explanation failed") }
        )

        assertEquals(report, result.measurementReport)
        assertEquals("heart", result.engineAnalysis.measurements.findings.single().id)
        assertEquals(LocalAiHealthAnalyzer.Level.INSUFFICIENT, result.explanation.level)
        assertNull(result.explanation.score)
        assertEquals(1, result.explanation.observedValueCount)
        assertEquals(1, result.explanation.researchOnlyCount)
    }

    private fun reportWithHeartRate() = HealthConclusionEngine.Report(
        items = listOf(
            HealthConclusionEngine.Item(
                "heart", "心率", "72 bpm", "參考範圍", "研究觀察", HealthConclusionEngine.Level.RESEARCH,
                null, true
            )
        ),
        overallHeadline = "有可用的心率觀察",
        overallDetail = "逐項結果"
    )

    private fun emptyInsights() = AlgorithmicInsightsEngine.Insights(
        stress = null,
        bodyEnergy = null,
        recovery = null,
        trainingLoad = null,
        recordedExerciseMinutes = null,
        sleepHours = null,
        sleepInferred = false,
        confidence = "不足",
        oxygenAvailable = false,
        hrvAvailable = false,
        estimatedDailyCalories = null,
        ansAdvice = null,
        readiness = null
    )
}
