package com.jaysato.xiaomihealthbridge

import org.junit.Assert.*
import org.junit.Test

class OpticalBloodPressureApiTest {
    @Test
    fun categorizesAhaBands() {
        assertEquals(
            OpticalBloodPressureApi.Category.STAGE2,
            OpticalBloodPressureApi.categorize(145.0, 92.0)
        )
        assertEquals(
            OpticalBloodPressureApi.Category.HYPERTENSIVE_CRISIS,
            OpticalBloodPressureApi.categorize(185.0, 100.0)
        )
    }

    @Test
    fun manualCuffCategoryHelperDoesNotExposeOpticalEstimateApi() {
        assertFalse(OpticalBloodPressureApi::class.java.methods.any { it.name.startsWith("estimate") })
    }
}
