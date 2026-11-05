package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Spo2RelativeSignalDisplayTest {
    @Test
    fun reportsIndexRelativeStatisticsAndRetainsZeroAndMissingPositions() {
        val summary = Spo2RelativeSignalDisplay.describe(listOf(0f, 524287f, 5f, 10f))

        assertTrue(summary.contains("索引順序；單位未驗證"))
        assertTrue(summary.contains("有限原始值 75.0%"))
        assertTrue(summary.contains("原始均值 5.000"))
        assertTrue(summary.contains("相對幅值 0–1"))
        assertTrue(summary.contains("短缺口插補 0 點"))
        assertTrue(summary.contains("不換算 SpO₂ 百分比"))
        assertFalse(summary.contains("SpO₂ 75%"))
    }

    @Test
    fun boundsSummaryWorkAndLabelsTruncatedPreview() {
        val summary = Spo2RelativeSignalDisplay.describe(List(Spo2RelativeSignalDisplay.MAX_DISPLAY_POINTS + 1) { it.toFloat() })

        assertTrue(summary.contains("統計預覽截取 8192/8193 點"))
    }
}
