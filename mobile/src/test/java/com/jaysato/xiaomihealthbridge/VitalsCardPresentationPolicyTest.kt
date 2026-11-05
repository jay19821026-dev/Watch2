package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalsCardPresentationPolicyTest {
    @Test
    fun missingHeartRateDoesNotPromoteRawEventCountsToTheHeadline() {
        assertEquals("待有效心率", VitalsCardPresentationPolicy.heartRateHeadline(null, null))
        assertEquals(
            "待有效心率",
            VitalsCardPresentationPolicy.heartRateHeadline(null, "1 個原始值 · 1 個事件")
        )
    }

    @Test
    fun validHeartRateAndAvailableConclusionRemainVisible() {
        assertEquals("64 bpm", VitalsCardPresentationPolicy.heartRateHeadline(64.0, "原始值"))
        assertEquals("研究估算 62 bpm", VitalsCardPresentationPolicy.heartRateHeadline(null, "研究估算 62 bpm"))
    }

    @Test
    fun heartRateReferenceIsConditionalOnRestAndNeverCallsItADiagnosis() {
        assertTrue(VitalsCardPresentationPolicy.heartRateReference(101.0).contains("高於成人安靜心率常見上限"))
        assertTrue(VitalsCardPresentationPolicy.heartRateReference(59.0).contains("低於成人安靜心率常見下限"))
        assertTrue(VitalsCardPresentationPolicy.heartRateReference(60.0).contains("常見範圍"))
        assertTrue(VitalsCardPresentationPolicy.heartRateReference(100.0).contains("常見範圍"))
        assertTrue(VitalsCardPresentationPolicy.heartRateReference(39.0).contains("重新量測"))
        assertTrue(VitalsCardPresentationPolicy.heartRateReference(null).contains("沒有可用讀值"))
    }

    @Test
    fun recordedOxygenHeadlineAndDetailExplainSourceFreshness() {
        assertEquals("94%", VitalsCardPresentationPolicy.recordedSpO2Headline("94% · 已記錄"))

        val detail = VitalsCardPresentationPolicy.recordedSpO2Detail("health_connect", "10/08 21:00")
        assertTrue(detail.contains("health_connect"))
        assertTrue(detail.contains("10/08 21:00"))
        assertTrue(detail.contains("不代表目前值"))
        assertFalse(detail.contains("未提供"))
    }
}
