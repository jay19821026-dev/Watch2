package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BiomedicalRawDetailTextTest {
    @Test
    fun spo2PreviewDoesNotDescribeRetainedPointsAsTotalSamples() {
        val detail = BiomedicalRawDetailText.spo2Preview(512)

        assertEquals(
            "保留的原始波形預覽 512 點；不以 70–100 的數值範圍猜測 SpO₂ 百分比。",
            detail
        )
        assertFalse(detail.contains("原始樣本"))
    }

    @Test
    fun spo2PreviewClampsNegativePointCount() {
        assertEquals(
            "保留的原始波形預覽 0 點；不以 70–100 的數值範圍猜測 SpO₂ 百分比。",
            BiomedicalRawDetailText.spo2Preview(-4)
        )
    }
}
