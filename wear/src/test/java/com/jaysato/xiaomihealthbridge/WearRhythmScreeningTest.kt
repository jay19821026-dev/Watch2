package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearRhythmScreeningTest {
    @Test
    fun fewerThanFiveValidIntervalsAreExplicitlyInsufficient() {
        listOf(3, 4).forEach { count ->
            val result = WearRhythmScreening.assess(List(count) { 800.0 })

            assertEquals(WearRhythmScreening.Label.INSUFFICIENT, result.label)
            assertEquals(count, result.rrCount)
            assertTrue(result.detail.contains("至少需要 5 個有效間隔"))
            assertEquals(null, result.cv)
            assertEquals(null, result.rmssdMs)
        }
    }

    @Test
    fun outOfRangeIntervalsDoNotSatisfyTheMinimum() {
        val result = WearRhythmScreening.assess(listOf(800.0, 800.0, 800.0, 800.0, 2_500.0))

        assertEquals(WearRhythmScreening.Label.INSUFFICIENT, result.label)
        assertEquals(4, result.rrCount)
        assertTrue(result.detail.contains("目前 4 個"))
    }

    @Test
    fun regularRrPatternDoesNotClaimSinusRhythmOrExcludeAbnormality() {
        val result = WearRhythmScreening.assess(List(20) { 800.0 })

        assertEquals(WearRhythmScreening.Label.REGULAR_PATTERN, result.label)
        assertTrue(result.title.contains("不排除心律異常"))
        assertFalse(result.title.contains("竇性"))
    }

    @Test
    fun irregularRrPatternRemainsResearchOnlyAndNeverClaimsAf() {
        val result = WearRhythmScreening.assess(
            listOf(500.0, 1200.0, 700.0, 1100.0, 600.0, 1300.0, 800.0,
                500.0, 1200.0, 700.0, 1100.0, 600.0, 1300.0, 800.0)
        )

        assertEquals(WearRhythmScreening.Label.IRREGULAR_POSSIBLE, result.label)
        assertTrue(result.researchOnly)
        assertTrue(result.title.contains("非 AF 判讀"))
        assertFalse(result.title.contains("房顫"))
    }
}
