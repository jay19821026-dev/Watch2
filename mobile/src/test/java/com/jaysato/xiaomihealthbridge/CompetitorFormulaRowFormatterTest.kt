package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitorFormulaRowFormatterTest {
    @Test
    fun cachedValuesAreHiddenWhenQualificationFails() {
        val row = CompetitorFormulaRowFormatter.format(
            kind = "ppg",
            piPercent = 2.4f,
            rmssdMs = 38f,
            altitudeM = 120f,
            note = "cached output",
            eligibility = RawSensorFormulaDisplayPolicy.Eligibility(
                showOpticalMetrics = false,
                showHrvMetrics = false,
                showEcgPulse = false,
                showEnvironmentMetrics = false
            ),
            hasCachedFormula = true
        )

        assertFalse(row.contains("2.40%"))
        assertFalse(row.contains("38 ms"))
        assertFalse(row.contains("120.0m"))
        assertFalse(row.contains("cached output"))
        assertTrue(row.contains("資格／新鮮度未通過"))
    }

    @Test
    fun onlyEligibleMetricClassesAreShown() {
        val row = CompetitorFormulaRowFormatter.format(
            kind = "ppg",
            piPercent = 2.4f,
            rmssdMs = 38f,
            altitudeM = 120f,
            note = null,
            eligibility = RawSensorFormulaDisplayPolicy.Eligibility(
                showOpticalMetrics = true,
                showHrvMetrics = false,
                showEcgPulse = false,
                showEnvironmentMetrics = false
            ),
            hasCachedFormula = true
        )

        assertTrue(row.contains("2.40%"))
        assertFalse(row.contains("38 ms"))
        assertFalse(row.contains("120.0m"))
    }
}
