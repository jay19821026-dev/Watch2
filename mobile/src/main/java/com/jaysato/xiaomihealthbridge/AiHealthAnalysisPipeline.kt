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
            warnings += "健康趨勢暫時無法讀取；已保留其他量測結果。"
            emptyInsights()
        }
        val measurementReport = try {
            readMeasurements()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            warnings += "部分量測暫時無法讀取，請稍後重試。"
            HealthConclusionEngine.unavailableReport("健康量測暫時無法讀取，請稍後重試。")
        }
        val engineAnalysis = try {
            analyzeEngine(insights, measurementReport.items)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            warnings += "綜合趨勢暫時無法產生；仍可查看各項量測。"
            fallbackEngineAnalysis(measurementReport.items)
        }
        val explanation = try {
            analyzeExplanation(insights, measurementReport.items)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            warnings += "趨勢說明暫時無法產生；仍可查看各項結果。"
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
        unavailableInputs = listOf("本次趨勢資料暫時讀不到")
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
        headline = "趨勢說明暫時讀不到；請查看各項健康結果",
        observedValueCount = report.items.count { it.available },
        observations = listOf("這次無法整理近期變化。"),
        actions = listOf("稍後重新分析，或查看各項健康結果。"),
        measurementHeadline = report.overallHeadline,
        measurementDetail = report.overallDetail,
        measuredNormalCount = report.items.count { it.available && it.userState == HealthConclusionEngine.UserState.NORMAL },
        measuredWatchCount = report.items.count { it.available && it.userState == HealthConclusionEngine.UserState.RESEARCH },
        measuredAttentionCount = report.items.count { it.available && it.userState == HealthConclusionEngine.UserState.ATTENTION },
        researchOnlyCount = report.items.count { it.available && it.userState == HealthConclusionEngine.UserState.RESEARCH }
    )
}
