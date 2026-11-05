package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class PpgEvidencePresentationPolicyTest {
    @Test
    fun absentAndUnsupportedEventCountAreNotDisplayedAsZero() {
        assertEquals("未知", PpgEvidencePresentationPolicy.eventCountLabel(null, null))
        assertEquals("未知", PpgEvidencePresentationPolicy.eventCountLabel(8, false))
    }

    @Test
    fun explicitZeroAndLegacyKnownCountRemainVisible() {
        assertEquals("0", PpgEvidencePresentationPolicy.eventCountLabel(0, true))
        assertEquals("8", PpgEvidencePresentationPolicy.eventCountLabel(8, null))
    }
}
