package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgEventPreviewTest {
    @Test fun keepsOneFieldPerEventWithoutFlatteningChannels() {
        assertEquals(listOf(1f, 3f), EcgEventPreview.firstField(listOf("1,2", "3,4")))
    }

    @Test fun rejectsInvalidFieldsWithoutBridgingMissingEvents() {
        assertTrue(EcgEventPreview.firstField(listOf("1,2", "NaN,3")).isEmpty())
        assertTrue(EcgEventPreview.firstField(listOf("9".repeat(100))).isEmpty())
    }

    @Test fun boundsPreviewWithoutChangingOriginalRows() {
        val rows = List(400) { "$it,1" }
        assertEquals(360, EcgEventPreview.firstField(rows).size)
        assertEquals(400, rows.size)
    }
}
