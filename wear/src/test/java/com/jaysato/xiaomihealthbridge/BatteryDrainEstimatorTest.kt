package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryDrainEstimatorTest {
    @Test
    fun calculatesDrainRateAndRemainingTime() {
        val result = BatteryDrainEstimator.estimate(
            listOf(BatterySample(100, 0), BatterySample(90, 3_600_000)), 90
        )
        assertEquals(10.0, result.percentPerHour!!, 0.001)
        assertEquals(9.0, result.estimatedHoursRemaining!!, 0.001)
        assertFalse(result.shouldConserve)
    }

    @Test
    fun flagsFastDrain() {
        val result = BatteryDrainEstimator.estimate(
            listOf(BatterySample(80, 0), BatterySample(70, 1_800_000)), 70
        )
        assertTrue(result.shouldConserve)
    }

    @Test
    fun doesNotGuessWithoutElapsedSamples() {
        val result = BatteryDrainEstimator.estimate(listOf(BatterySample(80, 0)), 80)
        assertNull(result.percentPerHour)
        assertNull(result.estimatedHoursRemaining)
        assertFalse(result.shouldConserve)
    }
}
