package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgEventPreviewTest {
    @Test fun oemPacketPreviewRequiresMarkerAndStrictShape() {
        val marked = (listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1000f, 42f) + List(4) { 0f }).joinToString(",")
        val unmarked = marked.replace(",1000.0,", ",0.0,")
        assertEquals((1..10).map(Int::toFloat), EcgEventPreview.oemPacketCandidates(listOf(unmarked, marked)))
        assertEquals(1, EcgEventPreview.oemMarkerCount(listOf(unmarked, marked)))
        assertTrue(EcgEventPreview.oemPacketCandidates(listOf(marked, unmarked)).isEmpty())
        assertTrue(EcgEventPreview.oemPacketCandidates(listOf(marked, "1,2")).isEmpty())
        assertTrue(EcgEventPreview.oemPacketCandidates(listOf(marked.replace("3.0", "NaN"))).isEmpty())
    }
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
