package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorCountLabelsTest {
    @Test
    fun missingVersionedCountsRemainUnknown() {
        assertEquals("未知", SensorCountLabels.rawValueCount(null))
        assertEquals("未知", SensorCountLabels.eventCount(null))
        assertEquals("未知", SensorCountLabels.count(null))
    }

    @Test
    fun explicitZeroIsDifferentFromUnknown() {
        assertEquals("0", SensorCountLabels.rawValueCount(0))
        assertEquals("0", SensorCountLabels.eventCount(0))
        assertEquals("0", SensorCountLabels.count(0))
    }
}
