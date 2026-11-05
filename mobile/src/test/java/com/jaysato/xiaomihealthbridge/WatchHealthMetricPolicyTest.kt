package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchHealthMetricPolicyTest {
    @Test
    fun uncalibratedWatchTemperatureCannotBecomeAHealthConnectBodyTemperatureRecord() {
        assertFalse(WatchHealthMetricPolicy.isSupported("body_temperature"))
        assertTrue(WatchHealthMetricPolicy.isSupported("steps"))
    }
}
