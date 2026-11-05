package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPowerEngineTest {
    @Test fun highDrainEntersSaver() {
        val result = AiPowerEngine.decide(70, false, false, 14.0, PowerWorkload.PASSIVE_SYNC)
        assertEquals(WearPowerMode.SAVER, result.mode)
        assertTrue(result.pauseBackground)
    }

    @Test fun measurementIsNeverBlockedByPowerPolicy() {
        val result = AiPowerEngine.decide(10, false, true, 20.0, PowerWorkload.MEASUREMENT)
        assertTrue(result.allowMeasurement)
        assertFalse(result.pauseBackground)
    }

    @Test fun severeSystemThermalStatusDefersBackgroundWork() {
        val result = AiPowerEngine.decide(80, false, true, 4.0, PowerWorkload.IDLE)
        assertEquals(WearPowerMode.SAVER, result.mode)
        assertTrue(result.pauseBackground)
    }

    @Test fun measurementRemainsAllowedDuringThermalPressure() {
        val result = AiPowerEngine.decide(80, false, true, 4.0, PowerWorkload.MEASUREMENT)
        assertTrue(result.allowMeasurement)
        assertFalse(result.pauseBackground)
    }

    @Test fun everyBatteryBandProducesAnExplicitDecision() {
        listOf(1, 15, 16, 30, 31, 60, 61, 80, 81, 100).forEach { percent ->
            val result = AiPowerEngine.decide(percent, false, false, 2.0, PowerWorkload.IDLE)
            assertTrue(result.batteryBand.isNotEmpty())
            assertTrue(result.syncIntervalMinutes == null || result.syncIntervalMinutes > 0)
        }
    }
}
