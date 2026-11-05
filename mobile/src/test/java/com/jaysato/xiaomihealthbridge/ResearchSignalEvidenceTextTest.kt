package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchSignalEvidenceTextTest {
    @Test
    fun decodedEcgSummaryPreservesResearchLimitationsWithoutClaimingAi() {
        val text = ResearchSignalEvidenceText.summarize("ecg", 0, "research_signal_unscaled", 2630, true, true)
        assertFalse(text.contains("未解碼"))
        assertTrue(text.contains("非醫療心電圖"))
        assertFalse(text.contains("AI"))
    }

    @Test
    fun scalarPreviewIsNotCalledUndecoded() {
        val text = ResearchSignalEvidenceText.summarize("pressure", 4, "has_data", 4, false)
        assertFalse(text.contains("未解碼"))
    }
}
