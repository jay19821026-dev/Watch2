package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureResearchSummaryTest {
    @Test
    fun reportsUnitlessWithinSequenceStatisticsAndPreservesZero() {
        val result = TemperatureResearchSummary.calculate(listOf(0f, 10f, 20f))!!

        assertEquals(3, result.finiteSampleCount)
        assertEquals(10.0, result.medianRaw, 0.0)
        assertEquals(0.0, result.minimumRaw, 0.0)
        assertEquals(20.0, result.maximumRaw, 0.0)
        assertEquals(1.0, result.normalizedPosition!!, 0.0)
        assertTrue(result.displayText().contains("索引順序，非時間尺度"))
        assertTrue(result.displayText().contains("原始通道單位未驗證"))
        assertTrue(result.displayText().contains("非品質分數"))
        assertTrue(result.displayText().contains("資料完整度"))
        assertTrue(result.displayText().contains("非準確率或統計信心"))
        assertTrue(result.displayText().contains("分析信心未評分"))
        assertEquals("EVIDENCE_COMPLETENESS_NOT_ACCURACY", result.evidenceIndex.reason)
        assertFalse(result.displayText().contains("°C"))
    }

    @Test
    fun filtersNonFiniteValuesAndDoesNotInventPositionForFlatSequence() {
        val result = TemperatureResearchSummary.calculateRaw(
            listOf(34.0, Double.NaN, 34.0, Double.POSITIVE_INFINITY)
        )!!

        assertEquals(2, result.finiteSampleCount)
        assertNull(result.normalizedPosition)
        assertTrue(result.displayText().contains("資料完整度"))
        assertTrue(result.displayText().contains("未評分"))
        assertEquals(0.0, result.lastMinusMedianRaw!!, 0.0)
    }

    @Test
    fun trailingMissingPointsAreNotMisidentifiedAsSequenceEndpoint() {
        val result = TemperatureResearchSummary.calculate(listOf(34f, 36f, Float.NaN))!!

        assertEquals(2, result.finiteSampleCount)
        assertEquals(36.0, result.lastSequenceValueRaw, 0.0)
        assertTrue(result.displayText().contains("最後有限樣本−中位數"))
        assertTrue(!result.displayText().contains("序列末點"))
    }

    @Test
    fun oneFinitePointIsShownWithoutPresentingItAsTrendOrBaseline() {
        val singlePoint = TemperatureResearchSummary.calculate(listOf(36.6f, Float.NaN))!!
        assertEquals(1, singlePoint.finiteSampleCount)
        assertTrue(singlePoint.displayText().contains("單點原始值 36.60（不建立基線）"))
        assertTrue(singlePoint.displayText().contains("相對變化無法計算（至少需 2 點）"))
        assertFalse(singlePoint.displayText().contains("中位數"))
        assertFalse(singlePoint.displayText().contains("範圍"))
        assertNull(TemperatureResearchSummary.calculate(emptyList<Float>()))
    }

    @Test
    fun overflowIsReportedAsUnavailableInsteadOfPrintingInfinity() {
        val result = TemperatureResearchSummary.calculateRaw(
            listOf(-Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE)
        )!!

        assertNull(result.lastMinusMedianRaw)
        assertNull(result.normalizedPosition)
        assertTrue(result.displayText().contains("數值範圍溢位"))
        assertFalse(result.displayText().contains("Infinity"))
        assertFalse(result.displayText().contains("NaN"))
    }
}
