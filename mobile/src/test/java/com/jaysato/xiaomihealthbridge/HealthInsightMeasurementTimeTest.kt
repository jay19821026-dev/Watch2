package com.jaysato.xiaomihealthbridge

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthInsightMeasurementTimeTest {
    @Test
    fun healthConnectMeasurementTimesMustStayWithinRequestedWindow() {
        val start = Instant.parse("2026-09-21T00:00:00Z")
        val end = Instant.parse("2026-09-28T09:00:00Z")

        assertTrue(HealthInsightInputPolicy.timestampsWithinWindow(listOf(start, end), start, end))
        assertFalse(HealthInsightInputPolicy.timestampsWithinWindow(listOf(start.minusNanos(1)), start, end))
        assertFalse(HealthInsightInputPolicy.timestampsWithinWindow(listOf(end.plusNanos(1)), start, end))
    }

    @Test
    fun outOfWindowRecordDoesNotHideOtherInWindowMeasurements() {
        val start = Instant.parse("2026-09-21T00:00:00Z")
        val end = Instant.parse("2026-09-28T09:00:00Z")
        val before = start.minusSeconds(1)
        val inside = start.plusSeconds(1)
        val after = end.plusSeconds(1)

        val retained = HealthInsightInputPolicy.retainTimestampsWithinWindow(
            listOf(before, inside, after), start, end
        ) { it }

        assertEquals(listOf(inside), retained)
    }

    @Test
    fun displaySeparatesSourceMeasurementTimesFromCurrentInsightGeneration() {
        val insights = AlgorithmicInsightsEngine.Insights(
            stress = 40,
            bodyEnergy = 60,
            recovery = 70,
            trainingLoad = null,
            recordedExerciseMinutes = null,
            sleepHours = 7.0,
            sleepInferred = false,
            confidence = "低",
            oxygenAvailable = false,
            hrvAvailable = true,
            estimatedDailyCalories = null,
            ansAdvice = null,
            readiness = 65,
            activityRegularity = 64,
            sleepRegularity = 72,
            oxygenSampleSummary = SensorHealthAlgorithms.OxygenSampleSummary(4, 91.0, 98.0, 25.0),
            latestObservedRestingHeartRateBpm = 61.0,
            latestObservedRestingHeartRateAt = Instant.parse("2026-09-28T01:00:00Z"),
            latestObservedRestingHeartRateOrigin = "watch.health",
            latestObservedHrvMs = 44.0,
            latestObservedHrvAt = Instant.parse("2026-09-27T23:00:00Z"),
            latestObservedHrvOrigin = "watch.health",
            sleepSessionEndAt = Instant.parse("2026-09-28T00:00:00Z"),
            wristTemperatureMeasuredAt = Instant.parse("2026-09-27T22:00:00Z"),
            trainingWindowStartAt = Instant.parse("2026-09-27T20:00:00Z"),
            trainingWindowEndAt = Instant.parse("2026-09-27T20:30:00Z"),
            trimpBaselineWindowStartAt = Instant.parse("2026-09-27T18:00:00Z"),
            trimpBaselineWindowEndAt = Instant.parse("2026-09-27T18:00:00Z"),
            oxygenWindowStartAt = Instant.parse("2026-09-21T10:00:00Z"),
            oxygenWindowEndAt = Instant.parse("2026-09-28T08:00:00Z"),
            dailyEnergyWindowStartAt = Instant.parse("2026-09-28T00:00:00Z"),
            dailyEnergyWindowEndAt = Instant.parse("2026-09-28T09:00:00Z"),
            sourceDataCutoffAt = Instant.parse("2026-09-28T09:00:00Z")
        )

        val display = insights.display()
        assertTrue(display.contains("獨立觀測（非複合分數）：靜息心率 61 bpm"))
        assertTrue(display.contains("HRV 44.0 ms"))
        assertTrue(display.contains("研究規則 ${ResearchCompositeModel.VERSION}"))
        assertTrue(display.contains("睡眠"))
        assertTrue(display.contains("腕溫來源時間"))
        assertTrue(display.contains("訓練負荷區間"))
        assertTrue(display.contains("TRIMP 所用靜息心率時間"))
        assertTrue(display.contains("同日且運動前最近靜息心率"))
        assertTrue(display.contains("每場至少 2 筆運動心率樣本的算術平均"))
        assertTrue(display.contains("血氧資料觀測區間"))
        assertTrue(display.contains("每日能量計算區間"))
        assertFalse(display.contains("七日活動負荷計算區間"))
        assertTrue(display.contains("Health Connect 查詢資料截止（非量測時間）"))
        assertTrue(display.contains("相鄰有睡眠紀錄日時段相似度（探索規則分；缺紀錄日不視為無睡眠）"))
        assertTrue(display.contains("近 7 日步數一致性（Health Connect aggregate 有值日 n=0；探索規則分，非完整週）"))
        assertTrue(display.contains("近 7 日有效血氧樣本範圍（n=4；非夜間專項）"))
        assertTrue(display.contains("近 7 日有效樣本中低於 90% 的比例（描述性；非夜間專項）"))
        assertTrue(!display.contains("等待夜間血氧紀錄"))

        assertTrue(insights.copy(activityRegularity = null).display().contains("至少需要 3 個有 aggregate 紀錄日"))

        val inferredDisplay = insights.copy(
            sleepInferred = true,
            sleepSessionEndAt = null,
            sleepInferenceStartAt = Instant.parse("2026-09-27T08:00:00Z"),
            sleepInferenceEndAt = Instant.parse("2026-09-27T11:00:00Z")
        ).display()
        assertTrue(inferredDisplay.contains("心率推估樣本區間"))
    }

    @Test
    fun missingCompositeInputsDoNotHideIndependentlyObservedRestingHeartRateAndHrv() {
        val insights = AlgorithmicInsightsEngine.Insights(
            stress = null,
            bodyEnergy = null,
            recovery = null,
            trainingLoad = null,
            recordedExerciseMinutes = null,
            sleepHours = null,
            sleepInferred = false,
            confidence = "不足",
            oxygenAvailable = false,
            hrvAvailable = true,
            estimatedDailyCalories = null,
            ansAdvice = null,
            readiness = null,
            latestObservedRestingHeartRateBpm = 62.0,
            latestObservedRestingHeartRateAt = Instant.parse("2026-09-28T01:00:00Z"),
            latestObservedRestingHeartRateOrigin = "watch.health",
            latestObservedHrvMs = 38.5,
            latestObservedHrvAt = Instant.parse("2026-09-28T01:02:00Z"),
            latestObservedHrvOrigin = "watch.health",
        )

        val display = insights.display()
        assertTrue(display.contains("獨立觀測（非複合分數）：靜息心率 62 bpm"))
        assertTrue(display.contains("HRV 38.5 ms"))
        assertTrue(display.contains("研究規則 ${ResearchCompositeModel.VERSION}"))
        assertTrue(display.contains("非複合分數"))

        val trend = SensorHealthAlgorithms.PersonalBaselineDeviation(
            baseline = 42.0,
            latest = 35.0,
            difference = -7.0,
            baselineDays = 3
        )
        val trendDisplay = insights.copy(hrvPersonalBaselineDeviation = trend).display()
        assertTrue(trendDisplay.contains("HRV 個人趨勢"))
        assertTrue(trendDisplay.contains("-7.0 ms（最新 35.0 / 個人基準 42.0 ms）"))
        assertTrue(insights.display().contains("需至少 3 個同來源有效紀錄日"))
    }
}
