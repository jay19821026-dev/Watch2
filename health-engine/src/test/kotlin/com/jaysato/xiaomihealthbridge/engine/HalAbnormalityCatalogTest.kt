package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HalAbnormalityCatalogTest {
    @Test fun classifiesDecoderAndOemStatesWithoutChangingRawEvidence() {
        val report = HalAbnormalityCatalog.classify(
            listOf("row_shape_mismatch", "needs_oem_mode", "timestamp_missing", "vendor_new_state")
        )
        assertEquals(3, report.matched.size)
        assertTrue(report.unknownCodes.contains("vendor_new_state"))
        assertTrue(report.actions.any { it.contains("索引時間軸") })
    }
}
