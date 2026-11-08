package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiHealthAnalyzerTest {
    @Test fun existingMeasurementConclusionsAreIncludedWithoutBecomingRecoveryScoreInputs() {
        val measurements = listOf(
            measurement("heart", HealthConclusionEngine.Level.GOOD),
            measurement("oxygen", HealthConclusionEngine.Level.WATCH),
            measurement("ecg", HealthConclusionEngine.Level.RESEARCH)
        )

        val result = LocalAiHealthAnalyzer.analyze(
            insights(stress = 20, energy = 80, sleep = 8.0), measurements
        )

        assertEquals(1, result.measuredNormalCount)
        assertEquals(1, result.measuredWatchCount)
        assertEquals(0, result.measuredAttentionCount)
        assertEquals(1, result.researchOnlyCount)
        assertEquals("1 項可比較量測結果建議觀察", result.measurementHeadline)
        assertEquals(LocalAiHealthAnalyzer.Level.STABLE, result.level)
    }

    @Test fun researchSignalsAloneNeverBecomeNormalOrAbnormalConclusion() {
        val result = LocalAiHealthAnalyzer.analyze(
            insights(stress = 20, energy = 80, sleep = 8.0),
            listOf(measurement("ecg", HealthConclusionEngine.Level.RESEARCH))
        )

        assertEquals(0, result.measuredNormalCount)
        assertEquals(0, result.measuredWatchCount)
        assertEquals(0, result.measuredAttentionCount)
        assertEquals(1, result.researchOnlyCount)
        assertTrue(result.measurementHeadline.contains("尚不能分類正常／異常"))
    }

    @Test fun stableSignalsProduceStableResult() {
        val result = LocalAiHealthAnalyzer.analyze(insights(stress = 20, energy = 80, sleep = 8.0))
        assertEquals(LocalAiHealthAnalyzer.Level.STABLE, result.level)
        assertTrue((result.score ?: 0) >= 75)
    }

    @Test fun poorRecoveryProducesAttentionResult() {
        val result = LocalAiHealthAnalyzer.analyze(insights(stress = 90, energy = 15, sleep = 3.5, load = 800))
        assertEquals(LocalAiHealthAnalyzer.Level.ATTENTION, result.level)
        assertTrue(result.actions.isNotEmpty())
    }

    @Test fun missingMeasurementsDoNotProduceAHealthyScore() {
        val missing = insights(stress = 20, energy = 80, sleep = 8.0)
            .copy(stress = null, sleepHours = null, hrvAvailable = false, confidence = "不足")
        val result = LocalAiHealthAnalyzer.analyze(missing)
        assertEquals(LocalAiHealthAnalyzer.Level.INSUFFICIENT, result.level)
        assertEquals(null, result.score)
    }

    @Test fun oneObservedDomainDoesNotProduceAHealthScore() {
        val sparse = insights(stress = 20, energy = 80, sleep = 8.0, load = 100)
            .copy(stress = null, bodyEnergy = null, recovery = null, sleepHours = null, trainingLoad = null,
                hrvAvailable = true, confidence = "低")
        val result = LocalAiHealthAnalyzer.analyze(sparse)
        assertEquals(LocalAiHealthAnalyzer.Level.INSUFFICIENT, result.level)
        assertEquals(null, result.score)
    }

    @Test fun insufficientGlobalConfidenceDoesNotHideIndividuallyAvailableDomains() {
        val sparse = insights(stress = 20, energy = 80, sleep = 8.0)
            .copy(trainingLoad = null, confidence = "不足")

        val result = LocalAiHealthAnalyzer.analyze(sparse)

        assertEquals(LocalAiHealthAnalyzer.Level.INSUFFICIENT, result.level)
        assertEquals(null, result.score)
        assertTrue(result.headline.contains("已有個別健康紀錄"))
        assertEquals(3, result.teamReviews.size)
        assertTrue(result.observations.any { it.contains("壓力估算目前平穩") })
        assertTrue(result.observations.any { it.contains("睡眠時間約") })
    }

    @Test fun incompleteSleepStagesStillShowRecordedSessionWindowWithoutCallingItSleepDuration() {
        val sparse = insights(stress = 20, energy = 80, sleep = 8.0)
            .copy(sleepHours = null, sleepSessionHours = 7.25, sleepStagesComplete = false)

        val result = LocalAiHealthAnalyzer.analyze(sparse)
        val sleepReview = result.teamReviews.first { it.role == "睡眠觀察" }.finding

        assertTrue(sleepReview.contains("7.3 小時"))
        assertTrue(sleepReview.contains("不等同實際睡眠時數"))
        assertTrue(sleepReview.contains("不計睡眠分數"))
    }

    @Test fun aSingleRecordedSleepSessionRemainsVisibleWithoutACompositeScore() {
        val sparse = insights(stress = 20, energy = 80, sleep = 8.0)
            .copy(stress = null, bodyEnergy = null, recovery = null, sleepHours = null, sleepSessionHours = 6.5,
                trainingLoad = null, confidence = "低")

        val result = LocalAiHealthAnalyzer.analyze(sparse)

        assertEquals(LocalAiHealthAnalyzer.Level.INSUFFICIENT, result.level)
        assertEquals(null, result.score)
        assertTrue(result.headline.contains("已有個別健康紀錄"))
        assertTrue(result.observedValueCount >= 1)
        assertTrue(result.teamReviews.first { it.role == "睡眠觀察" }.finding.contains("6.5 小時"))
    }

    @Test fun restingHeartRateAndHistoricalSleepRemainVisibleWithoutCompositeScore() {
        val partial = insights(stress = 20, energy = 80, sleep = 8.0).copy(
            stress = null,
            bodyEnergy = null,
            recovery = null,
            sleepHours = null,
            trainingLoad = null,
            latestObservedRestingHeartRateBpm = 64.0,
            historicalSleepSessionHours = 21.2,
            confidence = "不足"
        )

        val result = LocalAiHealthAnalyzer.analyze(partial)

        assertEquals(LocalAiHealthAnalyzer.Level.INSUFFICIENT, result.level)
        assertEquals(null, result.score)
        assertEquals(3, result.observedValueCount)
        assertTrue(result.headline.contains("已有個別健康紀錄"))
    }

    @Test fun hrvWithoutStressEnergyOrSleepDoesNotBecomeStableOrScore100() {
        val sparse = insights(stress = 20, energy = 80, sleep = 8.0, load = 100).copy(
            stress = null,
            bodyEnergy = null,
            recovery = null,
            sleepHours = null,
            trainingLoad = null,
            hrvAvailable = true,
            confidence = "低"
        )
        val result = LocalAiHealthAnalyzer.analyze(sparse)
        assertEquals(LocalAiHealthAnalyzer.Level.INSUFFICIENT, result.level)
        assertEquals(null, result.score)
        assertTrue(result.headline != "今日狀態大致穩定")
        assertTrue(result.agreement.contains("缺少"))
        assertTrue(result.agreement.contains("不代表沒有問題"))
    }

    @Test fun missingEnergyPreventsStableScoreAndAgreementClaimsLimitedToObservedDomains() {
        val result = LocalAiHealthAnalyzer.analyze(
            insights(stress = 20, energy = 80, sleep = 8.0).copy(bodyEnergy = null)
        )
        assertEquals(LocalAiHealthAnalyzer.Level.INSUFFICIENT, result.level)
        assertEquals(null, result.score)
        assertTrue(result.agreement.contains("缺少身體能量"))
    }

    @Test fun missingStressIsNotCountedAsNoConcern() {
        val result = LocalAiHealthAnalyzer.analyze(
            insights(stress = 20, energy = 80, sleep = 8.0).copy(stress = null)
        )
        assertTrue(result.agreement.contains("缺少壓力／恢復"))
        assertTrue(!result.agreement.contains("沒有一致的高關注訊號"))
    }

    @Test fun teamHasSeparateReviewsAndDoesNotClaimDiagnosticConsensus() {
        val result = LocalAiHealthAnalyzer.analyze(insights(stress = 20, energy = 80, sleep = 8.0))
        assertEquals(listOf("恢復與自律神經", "睡眠觀察", "活動負荷"), result.teamReviews.map { it.role })
        assertTrue(result.agreement.contains("不是多個 AI 的診斷共識") || result.agreement.contains("不代表排除疾病"))
        assertTrue(result.teamReviews.all { it.finding.isNotBlank() })
    }

    @Test fun inferredSleepHasLowConfidenceForSleepReview() {
        val result = LocalAiHealthAnalyzer.analyze(insights(stress = 20, energy = 80, sleep = 3.5).copy(sleepInferred = true))
        assertEquals("低", result.teamReviews.first { it.role == "睡眠觀察" }.confidence)
        assertTrue(result.teamReviews.first { it.role == "睡眠觀察" }.finding.contains("不據此判斷缺眠"))
        assertTrue(result.teamReviews.first { it.role == "睡眠觀察" }.finding.contains("低心率樣本跨度"))
    }

    private fun insights(stress: Int, energy: Int, sleep: Double, load: Int = 100) =
        AlgorithmicInsightsEngine.Insights(stress, energy, 70, load, 10, sleep, false, "中", true, true, 2200, null, 80)

    private fun measurement(id: String, level: HealthConclusionEngine.Level) = HealthConclusionEngine.Item(
        id = id,
        title = id,
        valueText = "72",
        clinicalCompare = "reference",
        conclusion = "result",
        level = level,
        score = 80.takeIf { level == HealthConclusionEngine.Level.GOOD },
        available = true
    )
}
