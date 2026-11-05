package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPowerSchedulerTest {
    @Test
    fun routineStandRemindersAreOptInByDefault() {
        assertFalse(WearPowerScheduler.DEFAULT_REMINDERS_ENABLED)
    }

    @Test fun normalModeAllowsReminderWork() {
        val state = WearPowerScheduler.decide(65, charging = false, powerSave = false)
        assertEquals(WearPowerMode.NORMAL, state.mode)
        assertTrue(state.allowReminderWork)
    }

    @Test fun saverModeDefersReminderWork() {
        val state = WearPowerScheduler.decide(25, charging = false, powerSave = false)
        assertEquals(WearPowerMode.SAVER, state.mode)
        assertFalse(state.allowReminderWork)
    }

    @Test fun chargingOverridesLowBattery() {
        val state = WearPowerScheduler.decide(10, charging = true, powerSave = true)
        assertEquals(WearPowerMode.NORMAL, state.mode)
        assertTrue(state.allowReminderWork)
    }

    @Test fun forceCriticalOverridesHighBattery() {
        val state = WearPowerScheduler.decide(
            90, charging = false, powerSave = false, policy = WearPowerPolicy.FORCE_CRITICAL
        )
        assertEquals(WearPowerMode.CRITICAL, state.mode)
        assertFalse(state.allowBackgroundSync)
    }

    @Test fun autoCriticalAtFifteenPercent() {
        val state = WearPowerScheduler.decide(15, charging = false, powerSave = false)
        assertEquals(WearPowerMode.CRITICAL, state.mode)
    }

    @Test fun criticalModeDisablesPassiveRegistrationButKeepsUserOptInSeparate() {
        val state = WearPowerScheduler.decide(15, charging = false, powerSave = false)
        assertEquals(WearPowerMode.CRITICAL, state.mode)
        assertFalse(PassiveRegistrationPolicy.shouldBeRegistered(userEnabled = true, state.mode))
    }

    @Test fun leavingCriticalModeRestoresRegistrationWhenUserOptedIn() {
        val normal = WearPowerScheduler.decide(65, charging = false, powerSave = false)
        val saver = WearPowerScheduler.decide(25, charging = false, powerSave = false)
        assertTrue(PassiveRegistrationPolicy.shouldBeRegistered(userEnabled = true, normal.mode))
        assertTrue(PassiveRegistrationPolicy.shouldBeRegistered(userEnabled = true, saver.mode))
        assertFalse(PassiveRegistrationPolicy.shouldBeRegistered(userEnabled = false, normal.mode))
    }
}
