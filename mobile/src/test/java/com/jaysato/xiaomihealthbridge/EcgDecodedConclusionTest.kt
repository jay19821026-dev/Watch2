package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgDecodedConclusionTest {
    @Test
    fun homeCardDoesNotPromiseNormalOrAbnormalWhenInputsMayBeInsufficient() {
        assertTrue(EcgHomeCardPresentationPolicy.supportingText("2600 個原始值").contains("可能無法判定"))
        assertTrue(EcgHomeCardPresentationPolicy.supportingText(null).contains("可能無法判定"))
        assertEquals("最近一次研究心律結果", EcgHomeCardPresentationPolicy.supportingText("72 bpm · 研究估算 · RR 4"))
    }

    @Test
    fun decodedSubsetIsNotCalledUndecodedOrPromotedToDiagnosis() {
        val item = HealthConclusionEngine.ecgConclusion(
            samples = 0, eventCount = 573, qualityScore = 0, hr = null,
            rrCount = 0, titleRhythm = null, measuredAt = 0, now = 1000,
            decodedEventCount = 263, decodedValueCount = 2630
        )
        assertTrue(item.valueText.contains("573 個原始事件 · 263 個研究解碼事件／2630 點"))
        assertFalse(item.valueText.contains("測量中"))
        assertFalse(item.valueText.contains("未解碼"))
        assertTrue(item.conclusion.contains("不輸出 bpm"))
        assertTrue(item.conclusion.contains("不是診斷準確率"))
        assertTrue(item.conclusion.contains("不能據此判定正常／異常"))
        assertTrue(item.conclusion.contains("胸痛／胸悶"))
    }
}
