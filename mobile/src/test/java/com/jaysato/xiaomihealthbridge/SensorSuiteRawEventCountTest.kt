package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorSuiteRawEventCountTest {
    @Test
    fun countsOnlyPositiveHardwareEventProvenance() {
        assertTrue(SensorSuiteExportPolicy.countsAsRawEvent(1, "hardware_event", "has_data"))
        assertFalse(SensorSuiteExportPolicy.countsAsRawEvent(0, "hardware_event", "has_data"))
        assertFalse(SensorSuiteExportPolicy.countsAsRawEvent(1, "health_services", "has_data"))
        assertFalse(SensorSuiteExportPolicy.countsAsRawEvent(1, "derived_estimate", "derived_estimate"))
        assertFalse(SensorSuiteExportPolicy.countsAsRawEvent(1, "hardware_event", "skipped_charging"))
        assertFalse(SensorSuiteExportPolicy.countsAsRawEvent(1, "", "has_data"))
    }
}
