package com.jaysato.xiaomihealthbridge

import java.time.Instant
import kotlin.math.roundToInt

/** Pure local aggregation for observed measurements and exploratory recovery/trend outputs. */
object LocalHealthAnalysisEngine {
    enum class SummaryState { AVAILABLE, PARTIAL, UNAVAILABLE }

    enum class FindingState { NORMAL, ATTENTION, RESEARCH, UNAVAILABLE }

    enum class ReasonCode {
        NORMAL_REFERENCE_RESULT,
        ATTENTION_REFERENCE_RESULT,
        RESEARCH_ONLY_RESULT,
        MEASUREMENT_UNAVAILABLE,
        NO_MEASUREMENT_INPUTS,
        RECORDED_SLEEP_USED,
        INFERRED_SLEEP_EXCLUDED,
        MODEL_RECOVERY_INPUT,
        RULE_RECOVERY_ESTIMATE,
        INSUFFICIENT_RECOVERY_COMPONENTS,
        MISSING_STRESS_INPUT,
        MISSING_ENERGY_INPUT,
        MISSING_RECORDED_SLEEP_INPUT,
        INVALID_STRESS_INPUT,
        INVALID_ENERGY_INPUT,
        INVALID_SLEEP_INPUT,
        INVALID_MODEL_RECOVERY_INPUT,
        TREND_OBSERVATION_AVAILABLE,
        NO_TREND_OBSERVATIONS,
        NON_FINITE_TREND_VALUE,
        INVALID_PERCENT_RANGE,
        NON_DIAGNOSTIC_OUTPUT
    }

    enum class RecoveryMethod { RESEARCH_COMPOSITE_MODEL, RULE_ESTIMATE, NONE }

    data class MeasurementFinding(
        val id: String,
        val title: String,
        val state: FindingState,
        val valueText: String,
        val comparison: String,
        val interpretation: String,
        val score: Int?,
        val reasonCodes: List<ReasonCode>
    )

    data class MeasurementSummary(
        val state: SummaryState,
        val headline: String,
        val detail: String,
        val normalCount: Int,
        val attentionCount: Int,
        val researchCount: Int,
        val unavailableCount: Int,
        val findings: List<MeasurementFinding>,
        val reasonCodes: List<ReasonCode>
    )

    data class RecoveryComponent(
        val id: String,
        val value: Double,
        val unit: String,
        val penaltyFraction: Double,
        val interpretation: String,
        val reasonCodes: List<ReasonCode>
    )

    data class RecoveryEstimate(
        val method: RecoveryMethod,
        val score: Int?,
        val state: SummaryState,
        val coverage: String,
        val components: List<RecoveryComponent>,
        val reasonCodes: List<ReasonCode>,
        val explanation: String
    )

    data class TrendObservation(
        val id: String,
        val label: String,
        val value: Double,
        val unit: String,
        val basis: String,
        val measuredAt: Instant?,
        val source: String?,
        val reasonCodes: List<ReasonCode>
    )

    data class Analysis(
        val measurements: MeasurementSummary,
        val recovery: RecoveryEstimate,
        val trends: List<TrendObservation>,
        val trendReasonCodes: List<ReasonCode>,
        val headline: String,
        val reasonExplanations: Map<ReasonCode, String>
    )

    private data class TrendCollection(
        val observations: List<TrendObservation>,
        val reasonCodes: List<ReasonCode>
    )

    fun analyze(
        insights: AlgorithmicInsightsEngine.Insights,
        measurements: List<HealthConclusionEngine.Item> = emptyList()
    ): Analysis {
        val measurementSummary = summarizeMeasurements(measurements)
        val recovery = estimateRecovery(insights)
        val trendCollection = collectTrends(insights)
        val headline = when {
            measurementSummary.state == SummaryState.UNAVAILABLE && recovery.score == null ->
                "目前沒有可用的量測判讀或恢復估算"
            measurementSummary.state == SummaryState.UNAVAILABLE -> recovery.explanation
            measurementSummary.attentionCount > 0 -> "有 ${measurementSummary.attentionCount} 項量測結果需留意；研究訊號另列"
            measurementSummary.normalCount > 0 -> "${measurementSummary.normalCount} 項量測落在其參考範圍；不代表排除疾病"
            measurementSummary.researchCount > 0 -> "已取得 ${measurementSummary.researchCount} 項研究訊號，不能據此分類正常或異常"
            else -> "量測項目目前沒有可比較的正常／需留意結果"
        }
        return Analysis(
            measurements = measurementSummary,
            recovery = recovery,
            trends = trendCollection.observations,
            trendReasonCodes = trendCollection.reasonCodes,
            headline = headline,
            reasonExplanations = REASON_EXPLANATIONS
        )
    }

    private fun summarizeMeasurements(items: List<HealthConclusionEngine.Item>): MeasurementSummary {
        val findings = items.map { item ->
            val state = when (item.userState) {
                HealthConclusionEngine.UserState.NORMAL -> FindingState.NORMAL
                HealthConclusionEngine.UserState.ATTENTION -> FindingState.ATTENTION
                HealthConclusionEngine.UserState.RESEARCH -> FindingState.RESEARCH
                HealthConclusionEngine.UserState.UNAVAILABLE -> FindingState.UNAVAILABLE
            }
            val reason = when (state) {
                FindingState.NORMAL -> ReasonCode.NORMAL_REFERENCE_RESULT
                FindingState.ATTENTION -> ReasonCode.ATTENTION_REFERENCE_RESULT
                FindingState.RESEARCH -> ReasonCode.RESEARCH_ONLY_RESULT
                FindingState.UNAVAILABLE -> ReasonCode.MEASUREMENT_UNAVAILABLE
            }
            MeasurementFinding(
                id = item.id,
                title = item.title,
                state = state,
                valueText = item.valueText,
                comparison = item.clinicalCompare,
                interpretation = item.conclusion,
                score = item.score,
                reasonCodes = listOf(reason) + if (state == FindingState.RESEARCH) {
                    listOf(ReasonCode.NON_DIAGNOSTIC_OUTPUT)
                } else emptyList()
            )
        }
        val normal = findings.count { it.state == FindingState.NORMAL }
        val attention = findings.count { it.state == FindingState.ATTENTION }
        val research = findings.count { it.state == FindingState.RESEARCH }
        val unavailable = findings.count { it.state == FindingState.UNAVAILABLE }
        val comparableCount = normal + attention
        val state = when {
            findings.isEmpty() || findings.all { it.state == FindingState.UNAVAILABLE } -> SummaryState.UNAVAILABLE
            unavailable > 0 || research > 0 -> SummaryState.PARTIAL
            else -> SummaryState.AVAILABLE
        }
        val headline = when {
            findings.isEmpty() -> "沒有提供量測項目"
            attention > 0 -> "$attention 項量測結果需留意"
            comparableCount > 0 -> "$normal 項量測結果在參考範圍"
            research > 0 -> "$research 項研究訊號；無法分類正常／異常"
            else -> "目前沒有可用的量測結果"
        }
        val detail = "可比較 $comparableCount 項（正常 $normal、需留意 $attention）；研究訊號 $research；不可用 $unavailable。僅摘要已提供的項目，不代表排除疾病。"
        val reasons = if (findings.isEmpty()) listOf(ReasonCode.NO_MEASUREMENT_INPUTS)
        else findings.flatMap { it.reasonCodes }.distinct()
        return MeasurementSummary(state, headline, detail, normal, attention, research, unavailable, findings, reasons)
    }

    private fun estimateRecovery(insights: AlgorithmicInsightsEngine.Insights): RecoveryEstimate {
        val components = mutableListOf<RecoveryComponent>()
        val missingOrInvalid = mutableListOf<ReasonCode>()

        val stress = insights.stress
        if (stress == null) missingOrInvalid += ReasonCode.MISSING_STRESS_INPUT
        else if (stress !in 0..100) missingOrInvalid += ReasonCode.INVALID_STRESS_INPUT
        else {
            val penalty = when {
                stress >= 75 -> 35
                stress >= 50 -> 18
                else -> 0
            }
            components += RecoveryComponent(
                id = "stress", value = stress.toDouble(), unit = "/100", penaltyFraction = penalty / 35.0,
                interpretation = when {
                    stress >= 75 -> "規則估算偏高"
                    stress >= 50 -> "規則估算中等"
                    else -> "規則估算較低"
                }, reasonCodes = listOf(ReasonCode.RULE_RECOVERY_ESTIMATE)
            )
        }

        val energy = insights.bodyEnergy
        if (energy == null) missingOrInvalid += ReasonCode.MISSING_ENERGY_INPUT
        else if (energy !in 0..100) missingOrInvalid += ReasonCode.INVALID_ENERGY_INPUT
        else {
            val penalty = when {
                energy < 30 -> 30
                energy < 55 -> 15
                else -> 0
            }
            components += RecoveryComponent(
                id = "body_energy", value = energy.toDouble(), unit = "/100", penaltyFraction = penalty / 30.0,
                interpretation = when {
                    energy < 30 -> "規則估算偏低"
                    energy < 55 -> "規則估算中等"
                    else -> "規則估算較高"
                }, reasonCodes = listOf(ReasonCode.RULE_RECOVERY_ESTIMATE)
            )
        }

        val sleep = when {
            insights.sleepInferred -> {
                missingOrInvalid += ReasonCode.INFERRED_SLEEP_EXCLUDED
                null
            }
            insights.sleepHours == null -> {
                missingOrInvalid += ReasonCode.MISSING_RECORDED_SLEEP_INPUT
                null
            }
            !insights.sleepHours.isFinite() || insights.sleepHours !in 0.0..24.0 -> {
                missingOrInvalid += ReasonCode.INVALID_SLEEP_INPUT
                null
            }
            else -> insights.sleepHours
        }
        if (sleep != null) {
            val penalty = when {
                sleep < 5.0 -> 30
                sleep < 7.0 -> 12
                else -> 0
            }
            components += RecoveryComponent(
                id = "recorded_sleep", value = sleep, unit = "小時", penaltyFraction = penalty / 30.0,
                interpretation = when {
                    sleep < 5.0 -> "記錄睡眠時長偏短"
                    sleep < 7.0 -> "記錄睡眠時長略短"
                    else -> "記錄睡眠時長達 7 小時"
                }, reasonCodes = listOf(ReasonCode.RECORDED_SLEEP_USED)
            )
        }

        val upstream = insights.recovery?.takeIf { it in 0..100 }
        if (insights.recovery != null && upstream == null) {
            missingOrInvalid += ReasonCode.INVALID_MODEL_RECOVERY_INPUT
        }
        if (upstream != null) {
            return RecoveryEstimate(
                method = RecoveryMethod.RESEARCH_COMPOSITE_MODEL,
                score = upstream,
                state = if (missingOrInvalid.isEmpty()) SummaryState.AVAILABLE else SummaryState.PARTIAL,
                coverage = "${components.size}/3 規則輸入；另有研究複合模型輸出",
                components = components,
                reasonCodes = (missingOrInvalid + ReasonCode.MODEL_RECOVERY_INPUT + ReasonCode.NON_DIAGNOSTIC_OUTPUT).distinct(),
                explanation = "恢復估算 $upstream/100（研究複合模型；非疾病風險或診斷）。個別輸入與缺項分開列示。"
            )
        }

        if (components.size < MIN_RECOVERY_COMPONENTS) {
            val reasons = (missingOrInvalid + ReasonCode.INSUFFICIENT_RECOVERY_COMPONENTS).distinct()
            return RecoveryEstimate(
                method = RecoveryMethod.NONE,
                score = null,
                state = if (components.isEmpty()) SummaryState.UNAVAILABLE else SummaryState.PARTIAL,
                coverage = "${components.size}/3 規則輸入",
                components = components,
                reasonCodes = reasons,
                explanation = if (components.isEmpty()) "沒有足夠有效輸入產生恢復估算；缺項不會以 0 代替。"
                else "目前僅有 ${components.size}/3 個恢復面向；保留個別觀察，不合成恢復估算。"
            )
        }

        val score = (100.0 * (1.0 - components.map { it.penaltyFraction }.average()))
            .roundToInt().coerceIn(0, 100)
        val state = if (missingOrInvalid.isEmpty()) SummaryState.AVAILABLE else SummaryState.PARTIAL
        return RecoveryEstimate(
            method = RecoveryMethod.RULE_ESTIMATE,
            score = score,
            state = state,
            coverage = "${components.size}/3 規則輸入",
            components = components,
            reasonCodes = (missingOrInvalid + ReasonCode.RULE_RECOVERY_ESTIMATE + ReasonCode.NON_DIAGNOSTIC_OUTPUT).distinct(),
            explanation = "探索性恢復估算 $score/100（${components.size}/3 個有效面向；僅對有值面向取平均，不把缺項當 0；非診斷）。"
        )
    }

    private fun collectTrends(insights: AlgorithmicInsightsEngine.Insights): TrendCollection {
        val output = mutableListOf<TrendObservation>()
        val excludedReasons = mutableListOf<ReasonCode>()
        fun add(
            id: String,
            label: String,
            value: Double?,
            unit: String,
            basis: String,
            at: Instant? = null,
            source: String? = null
        ) {
            if (value == null) return
            if (!value.isFinite()) {
                excludedReasons += ReasonCode.NON_FINITE_TREND_VALUE
                return
            }
            output += TrendObservation(
                id, label, value, unit, basis, at, source,
                listOf(ReasonCode.TREND_OBSERVATION_AVAILABLE, ReasonCode.NON_DIAGNOSTIC_OUTPUT)
            )
        }

        add("resting_hr_latest", "最近靜息心率", insights.latestObservedRestingHeartRateBpm, "bpm", "單次觀測；非趨勢或診斷",
            insights.latestObservedRestingHeartRateAt, insights.latestObservedRestingHeartRateOrigin)
        add("hrv_latest", "最近 HRV", insights.latestObservedHrvMs, "ms", "單次觀測；非壓力診斷",
            insights.latestObservedHrvAt, insights.latestObservedHrvOrigin)
        insights.hrvPersonalBaselineDeviation?.let {
            add("hrv_baseline_deviation", "HRV 相對個人基線差異", it.difference, "ms",
                "最新值 ${it.latest} ms；基線 ${it.baseline} ms；${it.baselineDays} 個有紀錄日")
        }
        add("resting_hr_deviation", "靜息心率偏移", insights.restingDeviation, "bpm", "相對既有個人參考值")
        add("temperature_deviation", "手腕溫度相對偏移", insights.temperatureDeviation, "°C",
            "既有描述性個人基線差異；不是核心體溫", insights.wristTemperatureMeasuredAt)
        add("sleep_regularity", "睡眠紀錄規律度", insights.sleepRegularity?.toDouble(), "/100",
            "探索規則分；不是睡眠品質或疾病判定")
        add("activity_regularity", "活動紀錄規律度", insights.activityRegularity?.toDouble(), "/100",
            "有資料日 ${insights.activityRegularityObservedDays} 日；探索規則分")
        add("training_monotony", "訓練單調度", insights.trainingMonotony, "指標值", "既有訓練負荷摘要；不作過度訓練診斷")
        add("training_strain", "訓練負荷", insights.trainingStrain, "AU", "既有研究負荷摘要")
        add("trimp", "TRIMP", insights.trimp, "AU", "既有研究估算；個人校準狀態依原始來源")
        insights.oxygenSampleSummary?.let { oxygen ->
            if (oxygen.validSampleCount > 0 && oxygen.minimumPercent.isFinite() && oxygen.maximumPercent.isFinite() &&
                oxygen.minimumPercent in 70.0..100.0 && oxygen.maximumPercent in oxygen.minimumPercent..100.0
            ) {
                add("spo2_min", "既有有效 SpO₂ 樣本下界", oxygen.minimumPercent, "%",
                    "${oxygen.validSampleCount} 個歷史有效樣本；範圍摘要、非目前值", oxygen.firstValidSampleAt)
                add("spo2_max", "既有有效 SpO₂ 樣本上界", oxygen.maximumPercent, "%",
                    "${oxygen.validSampleCount} 個歷史有效樣本；範圍摘要、非目前值", oxygen.lastValidSampleAt)
                val below90Ratio = oxygen.below90Percent?.takeIf { it.isFinite() && it in 0.0..100.0 }
                if (oxygen.below90Percent != null && below90Ratio == null) {
                    excludedReasons += if (oxygen.below90Percent.isFinite()) {
                        ReasonCode.INVALID_PERCENT_RANGE
                    } else {
                        ReasonCode.NON_FINITE_TREND_VALUE
                    }
                }
                add("spo2_below_90_ratio", "低於 90% 的歷史樣本比例", below90Ratio, "%",
                    "描述性比例、非夜間專項；不作診斷")
            } else if (oxygen.validSampleCount > 0) {
                excludedReasons += ReasonCode.INVALID_PERCENT_RANGE
            }
        }
        val reasons = (excludedReasons + if (output.isEmpty()) listOf(ReasonCode.NO_TREND_OBSERVATIONS) else emptyList()).distinct()
        return TrendCollection(output, reasons)
    }

    private const val MIN_RECOVERY_COMPONENTS = 2

    private val REASON_EXPLANATIONS = mapOf(
        ReasonCode.NORMAL_REFERENCE_RESULT to "原量測結論標記為參考範圍內；只描述此項結果。",
        ReasonCode.ATTENTION_REFERENCE_RESULT to "原量測結論標記為需留意；不是疾病診斷。",
        ReasonCode.RESEARCH_ONLY_RESULT to "資料僅具研究／描述用途，不升格成正常或異常判定。",
        ReasonCode.MEASUREMENT_UNAVAILABLE to "來源項目未提供可用量測結果。",
        ReasonCode.NO_MEASUREMENT_INPUTS to "呼叫端未提供任何量測項目。",
        ReasonCode.RECORDED_SLEEP_USED to "使用已記錄且非推估的睡眠時長。",
        ReasonCode.INFERRED_SLEEP_EXCLUDED to "低心率跨度等推估睡眠不納入恢復規則。",
        ReasonCode.MODEL_RECOVERY_INPUT to "沿用既有研究複合模型的恢復輸出，不重新解讀為臨床分數。",
        ReasonCode.RULE_RECOVERY_ESTIMATE to "依現有固定規則計算探索性估算，不是經臨床驗證的模型。",
        ReasonCode.INSUFFICIENT_RECOVERY_COMPONENTS to "至少需要兩個有效恢復面向才合成規則估算。",
        ReasonCode.MISSING_STRESS_INPUT to "沒有有效壓力估算輸入；不以零代替。",
        ReasonCode.MISSING_ENERGY_INPUT to "沒有有效身體能量估算輸入；不以零代替。",
        ReasonCode.MISSING_RECORDED_SLEEP_INPUT to "沒有可用的實際睡眠紀錄時長；不推測缺失值。",
        ReasonCode.INVALID_STRESS_INPUT to "壓力估算不在 0–100 範圍內，已排除。",
        ReasonCode.INVALID_ENERGY_INPUT to "身體能量估算不在 0–100 範圍內，已排除。",
        ReasonCode.INVALID_SLEEP_INPUT to "睡眠時長不是有限的 0–24 小時值，已排除。",
        ReasonCode.INVALID_MODEL_RECOVERY_INPUT to "既有研究恢復分數不在 0–100 範圍內，已排除。",
        ReasonCode.TREND_OBSERVATION_AVAILABLE to "沿用上游已計算的描述性觀察；本引擎不外推趨勢。",
        ReasonCode.NO_TREND_OBSERVATIONS to "沒有可用的趨勢觀察值。",
        ReasonCode.NON_FINITE_TREND_VALUE to "非有限趨勢值不納入輸出。",
        ReasonCode.INVALID_PERCENT_RANGE to "百分比樣本範圍不合理，已排除。",
        ReasonCode.NON_DIAGNOSTIC_OUTPUT to "本結果不是疾病診斷，也不代表排除疾病。"
    )
}
