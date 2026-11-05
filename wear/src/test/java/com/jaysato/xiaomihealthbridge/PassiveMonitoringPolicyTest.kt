package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveMonitoringPolicyTest {
    @Test
    fun supportedIntervalsAndDefaultAreExplicit() {
        assertEquals(listOf(1, 5, 10, 30, 60, 240, 480, 720, 0), PassiveMonitoringSettings.supportedIntervalsMinutes)
        assertEquals(5, PassiveMonitoringSettings.DEFAULT_INTERVAL_MINUTES)
        assertFalse(PassiveMonitoringSettings.KEY_ENABLED.isBlank())
        assertEquals("1 小時", PassiveMonitoringSettings.intervalLabel(60))
        assertEquals("4 小時", PassiveMonitoringSettings.intervalLabel(240))
        assertEquals("連續（耗電較高）", PassiveMonitoringSettings.intervalLabel(0))
    }

    @Test
    fun normalModeUsesSelectedInterval() {
        assertEquals(60_000L, PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(1, WearPowerMode.NORMAL))
        assertEquals(600_000L, PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(10, WearPowerMode.NORMAL))
        assertEquals(3_600_000L, PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(60, WearPowerMode.NORMAL))
        assertEquals(14_400_000L, PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(240, WearPowerMode.NORMAL))
        assertEquals(28_800_000L, PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(480, WearPowerMode.NORMAL))
        assertEquals(43_200_000L, PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(720, WearPowerMode.NORMAL))
        assertEquals(0L, PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(0, WearPowerMode.NORMAL))
    }

    @Test
    fun saverModeEnforcesFiveMinuteMinimumAndCriticalStopsHeartRateSync() {
        assertEquals(300_000L, PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(1, WearPowerMode.SAVER))
        assertEquals(1_800_000L, PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(30, WearPowerMode.SAVER))
        assertEquals(300_000L, PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(0, WearPowerMode.SAVER))
        assertNull(PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(5, WearPowerMode.CRITICAL))
    }

    @Test
    fun invalidIntervalsFallBackToSafeDefault() {
        assertEquals(5, PassiveMonitoringPolicy.sanitizeInterval(2))
        assertEquals(0, PassiveMonitoringPolicy.sanitizeInterval(0))
        assertTrue(PassiveMonitoringSettings.supportedIntervalsMinutes.contains(PassiveMonitoringPolicy.sanitizeInterval(60)))
    }

    @Test
    fun backgroundSyncMigrationPreservesOnlyPreviouslyRegisteredChoice() {
        assertTrue(PassiveMonitoringSettings.migratedEnabledState(false, false, true))
        assertFalse(PassiveMonitoringSettings.migratedEnabledState(false, false, false))
        assertFalse(PassiveMonitoringSettings.migratedEnabledState(true, false, true))
        assertTrue(PassiveMonitoringSettings.migratedEnabledState(true, true, false))
    }
}
