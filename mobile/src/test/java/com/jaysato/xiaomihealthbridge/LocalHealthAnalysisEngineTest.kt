package com.jaysato.xiaomihealthbridge

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHealthAnalysisEngineTest {
    @Test
    fun measurementFindingsRemainIndependentAndKeepResearchState() {
        val items = listOf(
            measurement("hr", HealthConclusionEngine.Level.GOOD, true),
            measurement("ecg", HealthConclusionEngine.Level.RESEARCH, true),
            measurement("spo2", HealthConclusionEngine.Level.UNAVAILABLE, false),
            measurement("bp", HealthConclusionEngine.Level.ATTENTION, true)
        )

        val summary = LocalHealthAnalysisEngine.analyze(insights(), items).measurements

        assertEquals(LocalHealthAnalysisEngine.SummaryState.PARTIAL, summary.state)
        assertEquals(listOf("hr", "ecg", "spo2", "bp"), summary.findings.map { it.id })
        assertEquals(1, summary.normalCount)
        assertEquals(1, summary.attentionCount)
        assertEquals(1, summary.researchCount)
        assertEquals(1, summary.unavailableCount)
        assertEquals(LocalHealthAnalysisEngine.FindingState.RESEARCH, summary.findings[1].state)
    }

    @Test
    fun emptyInputsDoNotProduceZeroOrHealthyRecoveryScore() {
        val analysis = LocalHealthAnalysisEngine.analyze(insights())

        assertEquals(LocalHealthAnalysisEngine.SummaryState.UNAVAILABLE, analysis.measurements.state)
        assertEquals(LocalHealthAnalysisEngine.ReasonCode.NO_MEASUREMENT_INPUTS, analysis.measurements.reasonCodes.single())
        assertNull(analysis.recovery.score)
        assertTrue(analysis.recovery.explanation.contains("不會以 0 代替"))
    }

    @Test
    fun recoveryEstimateUsesOnlyPresentComponentsAndReportsCoverage() {
        val result = LocalHealthAnalysisEngine.analyze(
            insights(stress = 75, energy = 30, sleep = null)
        ).recovery

        assertEquals(LocalHealthAnalysisEngine.RecoveryMethod.RULE_ESTIMATE, result.method)
        assertEquals(25, result.score)
        assertEquals("2/3 規則輸入", result.coverage)
        assertTrue(result.reasonCodes.contains(LocalHealthAnalysisEngine.ReasonCode.MISSING_RECORDED_SLEEP_INPUT))
        assertTrue(result.explanation.contains("不把缺項當 0"))
    }

    @Test
    fun inferredSleepIsExcludedRatherThanTreatedAsMissingMeasuredSleep() {
        val result = LocalHealthAnalysisEngine.analyze(
            insights(stress = 70, energy = 50, sleep = 8.0).copy(sleepInferred = true)
        ).recovery

        assertEquals(2, result.components.size)
        assertTrue(result.reasonCodes.contains(LocalHealthAnalysisEngine.ReasonCode.INFERRED_SLEEP_EXCLUDED))
        assertFalse(result.components.any { it.id == "recorded_sleep" })
    }

    @Test
    fun modelRecoveryOutputIsPreservedAsExploratoryAndNotRecomputed() {
        val result = LocalHealthAnalysisEngine.analyze(
            insights(stress = 95, energy = 5, sleep = 2.0).copy(recovery = 67)
        ).recovery

        assertEquals(LocalHealthAnalysisEngine.RecoveryMethod.RESEARCH_COMPOSITE_MODEL, result.method)
        assertEquals(67, result.score)
        assertTrue(result.reasonCodes.contains(LocalHealthAnalysisEngine.ReasonCode.NON_DIAGNOSTIC_OUTPUT))
    }

    @Test
    fun outOfRangeUpstreamRecoveryFallsBackWithAnExplicitReason() {
        val result = LocalHealthAnalysisEngine.analyze(
            insights(stress = 50, energy = 50, sleep = null).copy(recovery = 101)
        ).recovery

        assertEquals(LocalHealthAnalysisEngine.RecoveryMethod.RULE_ESTIMATE, result.method)
        assertTrue(result.reasonCodes.contains(LocalHealthAnalysisEngine.ReasonCode.INVALID_MODEL_RECOVERY_INPUT))
    }

    @Test
    fun invalidInputsAreExcludedAndDoNotBecomePenalties() {
        val result = LocalHealthAnalysisEngine.analyze(
            insights(stress = 120, energy = -1, sleep = Double.NaN)
        ).recovery

        assertNull(result.score)
        assertTrue(result.reasonCodes.contains(LocalHealthAnalysisEngine.ReasonCode.INVALID_STRESS_INPUT))
        assertTrue(result.reasonCodes.contains(LocalHealthAnalysisEngine.ReasonCode.INVALID_ENERGY_INPUT))
        assertTrue(result.reasonCodes.contains(LocalHealthAnalysisEngine.ReasonCode.INVALID_SLEEP_INPUT))
        assertTrue(result.components.isEmpty())
    }

    @Test
    fun trendsPreserveAvailableSourceTimesAndExcludeNonFiniteValues() {
        val result = LocalHealthAnalysisEngine.analyze(
            insights().copy(
                latestObservedRestingHeartRateBpm = 64.0,
                latestObservedRestingHeartRateAt = Instant.parse("2026-10-08T00:00:00Z"),
                latestObservedRestingHeartRateOrigin = "Health Connect",
                restingDeviation = Double.NaN,
                temperatureDeviation = 0.2
            )
        )

        assertTrue(result.trends.any { it.id == "resting_hr_latest" && it.value == 64.0 && it.source == "Health Connect" })
        assertTrue(result.trends.any { it.id == "temperature_deviation" && it.unit == "°C" })
        assertFalse(result.trends.any { it.id == "resting_hr_deviation" })
    }

    @Test
    fun absentTrendInputsHaveExplicitReasonAndInvalidOxygenRatioIsExcluded() {
        val empty = LocalHealthAnalysisEngine.analyze(insights())
        assertTrue(empty.trends.isEmpty())
        assertEquals(listOf(LocalHealthAnalysisEngine.ReasonCode.NO_TREND_OBSERVATIONS), empty.trendReasonCodes)

        val invalidOxygen = insights().copy(
            oxygenSampleSummary = SensorHealthAlgorithms.OxygenSampleSummary(
                validSampleCount = 4,
                minimumPercent = 95.0,
                maximumPercent = 98.0,
                below90Percent = 150.0
            )
        )
        val result = LocalHealthAnalysisEngine.analyze(invalidOxygen)

        assertFalse(result.trends.any { it.id == "spo2_below_90_ratio" })
        assertTrue(result.trendReasonCodes.contains(LocalHealthAnalysisEngine.ReasonCode.INVALID_PERCENT_RANGE))
    }

    @Test
    fun zeroIsPreservedAsARealObservedModelValue() {
        val result = LocalHealthAnalysisEngine.analyze(
            insights(stress = 0, energy = 0, sleep = 0.0)
        ).recovery

        assertEquals(0.0, result.components.first { it.id == "stress" }.value, 0.0)
        assertEquals(0.0, result.components.first { it.id == "body_energy" }.value, 0.0)
        assertEquals(0.0, result.components.first { it.id == "recorded_sleep" }.value, 0.0)
    }

    @Test
    fun everyReasonCodeHasHumanReadableExplanation() {
        assertEquals(LocalHealthAnalysisEngine.ReasonCode.entries.toSet(), LocalHealthAnalysisEngine.analyze(insights()).reasonExplanations.keys)
        assertTrue(LocalHealthAnalysisEngine.analyze(insights()).reasonExplanations.values.none(String::isBlank))
    }

    private fun measurement(id: String, level: HealthConclusionEngine.Level, available: Boolean) =
        HealthConclusionEngine.Item(id, id, "64 bpm", "參考", "既有解讀", level, null, available)

    private fun insights(stress: Int? = null, energy: Int? = null, sleep: Double? = null) =
        AlgorithmicInsightsEngine.Insights(
            stress = stress,
            bodyEnergy = energy,
            recovery = null,
            trainingLoad = null,
            recordedExerciseMinutes = null,
            sleepHours = sleep,
            sleepInferred = false,
            confidence = "不足",
            oxygenAvailable = false,
            hrvAvailable = false,
            estimatedDailyCalories = null,
            ansAdvice = null,
            readiness = null
        )
}
