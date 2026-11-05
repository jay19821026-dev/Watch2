package com.jaysato.xiaomihealthbridge

import org.junit.Assert.*
import org.junit.Test

class PhysiologyAlgorithmsTest {
    @Test fun exerciseCaloriesAreActiveOnlyAndNonNegative() {
        val result = PhysiologyAlgorithms.exercise(35, PhysiologyAlgorithms.Sex.MALE, 175.0, 75.0, 45.0, 145.0, 8.0)
        assertTrue(result.activeCalories > 0)
        assertTrue(result.estimatedDailyCalories > result.activeCalories)
        assertEquals("心肺強化", result.intensityZone)
    }

    @Test fun regularRrSeriesDoesNotRaiseIrregularWarning() {
        val values = List(60) { 790.0 + (it % 4) * 4 }
        val result = PhysiologyAlgorithms.rhythm(values, false)
        assertFalse(result.irregular)
        assertNotNull(result.sdnn)
    }
}
