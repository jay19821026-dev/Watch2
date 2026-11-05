package com.jaysato.xiaomihealthbridge

import kotlinx.coroutines.CancellationException

internal object AiHealthAnalysisPipeline {
    data class Result(
        val insights: AlgorithmicInsightsEngine.Insights,
        val measurementReport: HealthConclusionEngine.Report,
        val engineAnalysis: LocalHealthAnalysisEngine.Analysis,
        val explanation: LocalAiHealthAnalyzer.Result,
        val insightsAvailable: Boolean,
        val warnings: List<String>
    )

    suspend fun load(
        readInsights: suspend () -> AlgorithmicInsightsEngine.Insights,
        readMeasurements: () -> HealthConclusionEngine.Report,
        analyzeEngine: (AlgorithmicInsightsEngine.Insights, List<HealthConclusionEngine.Item>) -> LocalHealthAnalysisEngine.Analysis =
            LocalHealthAnalysisEngine::analyze,
        analyzeExplanation: (AlgorithmicInsightsEngine.Insights, List<HealthConclusionEngine.Item>) -> LocalAiHealthAnalyzer.Result =
            LocalAiHealthAnalyzer::analyze
    ): Result {
        val warnings = mutableListOf<String>()
        var insightsAvailable = true
        val insights = try {
            readInsights()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            insightsAvailable = false
            warnings += "健康趨勢資料讀取暫不可用（${error.javaClass.simpleName}）；其他單項判讀仍繼續。"
            emptyInsights()
        }
        val measurementReport = try {
            readMeasurements()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            warnings += "單項量測資料讀取暫不可用（${error.javaClass.simpleName}）。"
            HealthConclusionEngine.unavailableReport("健康量測資料讀取例外：${error.javaClass.simpleName}")
        }
        val engineAnalysis = try {
            analyzeEngine(insights, measurementReport.items)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            warnings += "統計整合暫不可用（${error.javaClass.simpleName}）；保留逐項量測結果。"
            fallbackEngineAnalysis(measurementReport.items)
        }
        val explanation = try {
            analyzeExplanation(insights, measurementReport.items)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            warnings += "趨勢說明暫不可用（${error.javaClass.simpleName}）；保留量測與統計結果。"
            fallbackExplanation(measurementReport)
        }
        return Result(insights, measurementReport, engineAnalysis, explanation, insightsAvailable, warnings)
    }

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
        readiness = null,
        unavailableInputs = listOf("本次趨勢資料階段發生例外；原始測量未修改")
    )

    private fun fallbackEngineAnalysis(items: List<HealthConclusionEngine.Item>): LocalHealthAnalysisEngine.Analysis {
        val findings = items.map { item ->
            val state = when (item.userState) {
                HealthConclusionEngine.UserState.NORMAL -> LocalHealthAnalysisEngine.FindingState.NORMAL
                HealthConclusionEngine.UserState.ATTENTION -> LocalHealthAnalysisEngine.FindingState.ATTENTION
                HealthConclusionEngine.UserState.RESEARCH -> LocalHealthAnalysisEngine.FindingState.RESEARCH
                HealthConclusionEngine.UserState.UNAVAILABLE -> LocalHealthAnalysisEngine.FindingState.UNAVAILABLE
            }
            val reason = when (state) {
                LocalHealthAnalysisEngine.FindingState.NORMAL -> LocalHealthAnalysisEngine.ReasonCode.NORMAL_REFERENCE_RESULT
                LocalHealthAnalysisEngine.FindingState.ATTENTION -> LocalHealthAnalysisEngine.ReasonCode.ATTENTION_REFERENCE_RESULT
                LocalHealthAnalysisEngine.FindingState.RESEARCH -> LocalHealthAnalysisEngine.ReasonCode.RESEARCH_ONLY_RESULT
                LocalHealthAnalysisEngine.FindingState.UNAVAILABLE -> LocalHealthAnalysisEngine.ReasonCode.MEASUREMENT_UNAVAILABLE
            }
            LocalHealthAnalysisEngine.MeasurementFinding(
                item.id, item.title, state, item.valueText, item.clinicalCompare, item.conclusion,
                item.score, listOf(reason)
            )
        }
        val normal = findings.count { it.state == LocalHealthAnalysisEngine.FindingState.NORMAL }
        val attention = findings.count { it.state == LocalHealthAnalysisEngine.FindingState.ATTENTION }
        val research = findings.count { it.state == LocalHealthAnalysisEngine.FindingState.RESEARCH }
        val unavailable = findings.size - normal - attention - research
        val measurementSummary = LocalHealthAnalysisEngine.MeasurementSummary(
            state = if (findings.isEmpty() || unavailable == findings.size) LocalHealthAnalysisEngine.SummaryState.UNAVAILABLE
            else if (unavailable > 0) LocalHealthAnalysisEngine.SummaryState.PARTIAL else LocalHealthAnalysisEngine.SummaryState.AVAILABLE,
            headline = "統計整合暫不可用；逐項結果仍保留",
            detail = "目前顯示已完成的單項量測結果，不合成整體分數。",
            normalCount = normal,
            attentionCount = attention,
            researchCount = research,
            unavailableCount = unavailable,
            findings = findings,
            reasonCodes = listOf(LocalHealthAnalysisEngine.ReasonCode.MEASUREMENT_UNAVAILABLE)
        )
        val recovery = LocalHealthAnalysisEngine.RecoveryEstimate(
            LocalHealthAnalysisEngine.RecoveryMethod.NONE,
            null,
            LocalHealthAnalysisEngine.SummaryState.UNAVAILABLE,
            "未評分",
            emptyList(),
            listOf(LocalHealthAnalysisEngine.ReasonCode.INSUFFICIENT_RECOVERY_COMPONENTS),
            "統計整合暫不可用；未計算恢復分數。"
        )
        return LocalHealthAnalysisEngine.Analysis(
            measurementSummary,
            recovery,
            emptyList(),
            listOf(LocalHealthAnalysisEngine.ReasonCode.NO_TREND_OBSERVATIONS),
            "統計整合暫不可用；逐項量測結果仍保留",
            emptyMap()
        )
    }

    private fun fallbackExplanation(report: HealthConclusionEngine.Report) = LocalAiHealthAnalyzer.Result(
        level = LocalAiHealthAnalyzer.Level.INSUFFICIENT,
        score = null,
        headline = "趨勢說明暫不可用；請查看下方單項判讀",
        observedValueCount = report.items.count { it.available },
        observations = listOf("趨勢說明階段發生例外；沒有生成替代生理數值。"),
        actions = listOf("可重新分析；原始測量與逐項判讀保留。"),
        measurementHeadline = report.overallHeadline,
        measurementDetail = report.overallDetail,
        measuredNormalCount = report.items.count { it.available && it.userState == HealthConclusionEngine.UserState.NORMAL },
        measuredWatchCount = report.items.count { it.available && it.userState == HealthConclusionEngine.UserState.RESEARCH },
        measuredAttentionCount = report.items.count { it.available && it.userState == HealthConclusionEngine.UserState.ATTENTION },
        researchOnlyCount = report.items.count { it.available && it.userState == HealthConclusionEngine.UserState.RESEARCH }
    )
}
