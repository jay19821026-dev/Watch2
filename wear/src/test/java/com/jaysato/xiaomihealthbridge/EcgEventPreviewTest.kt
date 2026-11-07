package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgEventPreviewTest {
    @Test fun oemPacketPreviewRequiresMarkerAndStrictShape() {
        val marked = (listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1000f, 42f) + List(4) { 0f }).joinToString(",")
        val unmarked = marked.replace(",1000.0,", ",0.0,")
        assertEquals((1..10).map(Int::toFloat), EcgEventPreview.oemPacketCandidates(listOf(unmarked, marked), intArrayOf(1, 1), longArrayOf(1L, 2L)))
        assertEquals(1, EcgEventPreview.oemMarkerCount(listOf(unmarked, marked)))
        assertTrue(EcgEventPreview.oemPacketCandidates(listOf(marked, unmarked), intArrayOf(1, 1), longArrayOf(1L, 2L)).isEmpty())
        assertTrue(EcgEventPreview.oemPacketCandidates(listOf(marked, "1,2"), intArrayOf(1, 1), longArrayOf(1L, 2L)).isEmpty())
        assertTrue(EcgEventPreview.oemPacketCandidates(listOf(marked.replace("3.0", "NaN")), intArrayOf(1), longArrayOf(1L)).isEmpty())
    }
    @Test fun oemPacketPreviewRequiresSynchronizedOnBodyState() {
        val marked = (listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1000f, 42f) + List(4) { 0f }).joinToString(",")
        assertTrue(EcgEventPreview.oemPacketCandidates(listOf(marked), intArrayOf(), longArrayOf(1L)).isEmpty())
        assertTrue(EcgEventPreview.oemPacketCandidates(listOf(marked), intArrayOf(0), longArrayOf(1L)).isEmpty())
        assertTrue(EcgEventPreview.oemPacketCandidates(listOf(marked), intArrayOf(-1), longArrayOf(1L)).isEmpty())
        assertEquals((1..10).map(Int::toFloat), EcgEventPreview.oemPacketCandidates(listOf(marked), intArrayOf(1), longArrayOf(1L)))
        assertEquals((1..10).map(Int::toFloat), EcgEventPreview.oemPacketCandidates(listOf(marked, marked, marked), intArrayOf(1, 0, 1), longArrayOf(1L, 2L, 3L)))
        assertEquals(1, EcgEventPreview.contactAtEvent(10_000_000_000L, 9_000_000_000L, true))
        assertEquals(-1, EcgEventPreview.contactAtEvent(10_000_000_000L, 9_000_000_000L, false))
        assertEquals(0, EcgEventPreview.contactAtEvent(10_000_000_000L, 11_000_000_000L, true))
        assertEquals(0, EcgEventPreview.contactAtEvent(70_000_000_001L, 1L, true))
    }
    @Test fun breaksPreviewAcrossMissingOrNonMonotonicEventTime() {
        val marked = (listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1000f, 42f) + List(4) { 0f }).joinToString(",")
        val expected = (1..10).map(Int::toFloat)
        val rows = listOf(marked, marked)
        val contacts = intArrayOf(1, 1)
        assertEquals(expected + expected, EcgEventPreview.oemPacketCandidates(rows, contacts, longArrayOf(1_000_000_000L, 2_000_000_000L)))
        assertEquals(expected, EcgEventPreview.oemPacketCandidates(rows, contacts, longArrayOf(1_000_000_000L, 2_000_000_001L)))
        assertEquals(expected, EcgEventPreview.oemPacketCandidates(rows, contacts, longArrayOf(2L, 1L)))
        assertTrue(EcgEventPreview.oemPacketCandidates(rows, contacts, longArrayOf(1L)).isEmpty())
        assertTrue(EcgEventPreview.oemPacketCandidates(rows, contacts, longArrayOf(1L, 0L)).isEmpty())
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
