package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyHealthReportTest {
    private val now = Instant.parse("2026-11-08T12:00:00Z")

    @Test fun onlyCurrentDayMeasurementsEnterReport() {
        val day = LocalDate.of(2026, 11, 8)
        val steps = HealthConnectActivityObservationPolicy.Summary(
            HealthConnectActivityObservationPolicy.State.AVAILABLE,
            now.minusSeconds(7 * 86400), now,
            listOf(HealthConnectActivityObservationPolicy.DayValue(day.minusDays(1), 12000.0, 1, setOf("watch.source")),
                HealthConnectActivityObservationPolicy.DayValue(day, 4500.0, 1, setOf("watch.source"))),
            16500.0, 2, 1
        )
        val report = DailyHealthReport.from(base().copy(
            stepsObservation = steps,
            latestObservedRestingHeartRateBpm = 62.0,
            latestObservedRestingHeartRateAt = now.minusSeconds(86400),
            latestObservedHrvMs = 44.0,
            latestObservedHrvAt = now.minusSeconds(3600),
            latestObservedHrvOrigin = "watch.source"
        ), now, ZoneOffset.UTC)

        assertTrue(report.observations.any { it.contains("4500 步") && it.contains("較昨天 -7500 步") })
        assertTrue(report.observations.any { it.contains("HRV）44.0 ms") })
        assertFalse(report.observations.any { it.contains("watch.source") || it.contains("aggregate") })
        assertFalse(report.observations.any { it.contains("12000") || it.contains("靜息心率 62") })
        assertTrue(report.missing.any { it.contains("靜息心率") })
        assertTrue(report.missing.any { it.contains("血氧") })
    }

    @Test fun incompleteDailyAggregateDoesNotBecomeZeroOrAdvice() {
        val report = DailyHealthReport.from(base(), now, ZoneOffset.UTC)
        assertTrue(report.observations.isEmpty())
        assertTrue(report.explanation.contains("尚無可用"))
        assertFalse(report.explanation.contains("正常"))
    }

    @Test fun datedSleepAndOxygenAppearOnlyWithTheirOwnEvidence() {
        val report = DailyHealthReport.from(base().copy(
            dailySleepHours = 7.2,
            dailySleepEndAt = Instant.parse("2026-11-08T06:00:00Z"),
            dailySleepOrigin = "sleep.source",
            dailyOxygenSummary = SensorHealthAlgorithms.OxygenSampleSummary(
                2, 94.0, 97.0, null,
                Instant.parse("2026-11-08T07:00:00Z"),
                Instant.parse("2026-11-08T09:00:00Z")
            ),
            dailyOxygenOrigin = "oxygen.source"
        ), now, ZoneOffset.UTC)
        assertTrue(report.observations.any { it.contains("睡眠 7.2 小時") && !it.contains("sleep.source") })
        assertTrue(report.observations.any { it.contains("血氧 94–97%") && it.contains("有偏低紀錄") && !it.contains("oxygen.source") })
        assertFalse(report.missing.any { it.startsWith("睡眠：") || it.startsWith("血氧：") })

        val stale = DailyHealthReport.from(base().copy(
            dailyOxygenSummary = SensorHealthAlgorithms.OxygenSampleSummary(
                1, 96.0, 96.0, null,
                Instant.parse("2026-11-07T09:00:00Z"),
                Instant.parse("2026-11-07T09:00:00Z")
            )
        ), now, ZoneOffset.UTC)
        assertTrue(stale.missing.any { it.startsWith("血氧：") })
    }

    private fun base() = AlgorithmicInsightsEngine.Insights(
        stress = null, bodyEnergy = null, recovery = null, trainingLoad = null,
        recordedExerciseMinutes = null, sleepHours = null, sleepInferred = false,
        confidence = "不足", oxygenAvailable = false, hrvAvailable = false,
        estimatedDailyCalories = null, ansAdvice = null, readiness = null
    )
}
