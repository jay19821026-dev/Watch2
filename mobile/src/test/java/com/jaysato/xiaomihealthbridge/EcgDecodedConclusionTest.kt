package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgDecodedConclusionTest {
    @Test
    fun homeCardDoesNotPromiseNormalOrAbnormalWhenInputsMayBeInsufficient() {
        assertTrue(EcgHomeCardPresentationPolicy.supportingText("2600 個原始值").contains("量測後查看結果"))
        assertTrue(EcgHomeCardPresentationPolicy.supportingText(null).contains("量測後查看結果"))
        assertEquals("最近一次研究心律結果", EcgHomeCardPresentationPolicy.supportingText("72 bpm · 研究估算 · RR 4"))
    }

    @Test
    fun decodedSubsetIsNotCalledUndecodedOrPromotedToDiagnosis() {
        val item = HealthConclusionEngine.ecgConclusion(
            samples = 0, eventCount = 573, qualityScore = 0, hr = null,
            rrCount = 0, titleRhythm = null, measuredAt = 0, now = 1000,
            decodedEventCount = 263, decodedValueCount = 2630
        )
        assertEquals("已取得 ECG 訊號，時間未確認", item.valueText)
        assertFalse(item.valueText.contains("測量中"))
        assertFalse(item.valueText.contains("未解碼"))
        assertTrue(item.conclusion.contains("尚無心律結果"))
        assertTrue(item.conclusion.contains("不是診斷準確率"))
        assertTrue(item.conclusion.contains("不能據此判定正常／異常"))
        assertTrue(item.conclusion.contains("胸痛／胸悶"))
    }

    @Test
    fun freshDecodedSubsetProducesSpecificResearchConclusionWithoutClinicalLabel() {
        val item = HealthConclusionEngine.ecgConclusion(
            samples = 0, eventCount = 573, qualityScore = 64, hr = null,
            rrCount = 0, titleRhythm = null, measuredAt = 1000, now = 2000,
            timestampSource = "sensor_event", decodedEventCount = 263, decodedValueCount = 2630
        )
        assertEquals("已取得 ECG 訊號，尚無心律結果", item.valueText)
        assertFalse(item.conclusion.contains("263 個事件"))
        assertTrue(item.conclusion.contains("不輸出 bpm"))
        assertFalse(item.conclusion.contains("正常心律"))
    }
}
