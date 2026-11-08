package com.jaysato.xiaomihealthbridge

import org.junit.Assert.*
import org.junit.Test

class ClinicalMetricCalibrationTest {
    @Test
    fun oxygenUsesReferenceBandWithoutInventedScore() {
        val good = ClinicalMetricCalibration.forOxygen(97.0)
        assertEquals("未評分", good.grade)
        assertNull(good.score)
        assertFalse(good.trusted)
        assertTrue(good.comparisons.any { it.contains("95") })

        val low = ClinicalMetricCalibration.forOxygen(88.0)
        assertNull(low.score)
        assertTrue(low.comparisons.any { it.contains("90") })
    }

    @Test
    fun oxygenRejectsRawPacketsAndImpossiblePercentages() {
        listOf(1015806.0, 360447.0, 101.0, 65.0, Double.NaN).forEach { raw ->
            val result = ClinicalMetricCalibration.forOxygen(raw)
            assertNull(result.value)
            assertFalse(result.trusted)
            assertNull(result.score)
        }
    }

    @Test
    fun restingHeartRateUsesQuietBand() {
        val ok = ClinicalMetricCalibration.forHeartRate(72.0, resting = true)
        assertTrue(ok.score!! >= 75)
        val high = ClinicalMetricCalibration.forHeartRate(110.0, resting = true)
        assertTrue(high.score!! < ok.score!!)
    }

    @Test
    fun scanHeartRateIsClassifiedAsInstantaneousNotResting() {
        val result = ClinicalMetricCalibration.forHeartRate(104.0, resting = false)
        assertEquals("heart_rate", result.metric)
        assertTrue(result.headline.contains("瞬時心率"))
        assertFalse(result.headline.contains("靜止心率"))
    }

    @Test
    fun stepsAndSleepCompare() {
        assertTrue(ClinicalMetricCalibration.forSteps(8500.0).score!! >= 75)
        assertTrue(ClinicalMetricCalibration.forSleepHours(7.5).score!! >= 75)
        assertTrue(ClinicalMetricCalibration.forSleepHours(4.0).score!! < 50)
    }

    @Test
    fun missingInputsRemainUnscoredInsteadOfZero() {
        assertNull(ClinicalMetricCalibration.forOxygen(null).score)
        assertNull(ClinicalMetricCalibration.forHeartRate(null, resting = false).score)
        assertNull(ClinicalMetricCalibration.forHrv(null, null).score)
        assertNull(ClinicalMetricCalibration.forSleepHours(null).score)
        assertNull(ClinicalMetricCalibration.forSteps(null).score)
    }

    @Test
    fun compareMetricCoversDashboardKeys() {
        listOf("heart_rate", "oxygen", "steps", "sleep", "calories", "distance", "weight").forEach { key ->
            val lines = ClinicalReferenceTables.compareMetric(key, 80.0)
            assertTrue(lines.isNotEmpty())
        }
    }
}
