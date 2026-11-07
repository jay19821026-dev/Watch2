package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgEventPreviewTest {
    private fun preview(
        rows: List<String>, contacts: IntArray, timestamps: LongArray,
        totalEventCount: Int = EcgEventPreview.OEM_OBSERVED_WARMUP_EVENTS + rows.size
    ) = EcgEventPreview.oemPacketCandidates(rows, contacts, timestamps, totalEventCount)

    @Test fun retainsWarmupEventsButOmitsThemFromCandidatePreview() {
        val marked = (listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1000f, 42f) + List(4) { 0f }).joinToString(",")
        val rows = List(21) { marked }
        val contacts = IntArray(21) { 1 }
        val timestamps = LongArray(21) { (it + 1L) * 20_000_000L }
        assertEquals((1..10).map(Int::toFloat), preview(rows, contacts, timestamps, totalEventCount = 21))
        assertTrue(preview(rows.take(20), contacts.take(20).toIntArray(), timestamps.take(20).toLongArray(), totalEventCount = 20).isEmpty())
        assertTrue(preview(rows.takeLast(2), intArrayOf(1, 1), timestamps.takeLast(2).toLongArray(), totalEventCount = 20).isEmpty())
        assertEquals((1..10).map(Int::toFloat), preview(rows.takeLast(2), intArrayOf(1, 1), timestamps.takeLast(2).toLongArray(), totalEventCount = 21))
    }
    @Test fun oemPacketPreviewRequiresMarkerAndStrictShape() {
        val marked = (listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1000f, 42f) + List(4) { 0f }).joinToString(",")
        val unmarked = marked.replace(",1000.0,", ",0.0,")
        assertEquals((1..10).map(Int::toFloat), preview(listOf(unmarked, marked), intArrayOf(1, 1), longArrayOf(1L, 2L)))
        assertEquals(1, EcgEventPreview.oemMarkerCount(listOf(unmarked, marked)))
        assertTrue(preview(listOf(marked, unmarked), intArrayOf(1, 1), longArrayOf(1L, 2L)).isEmpty())
        assertTrue(preview(listOf(marked, "1,2"), intArrayOf(1, 1), longArrayOf(1L, 2L)).isEmpty())
        assertTrue(preview(listOf(marked.replace("3.0", "NaN")), intArrayOf(1), longArrayOf(1L)).isEmpty())
    }
    @Test fun oemPacketPreviewRequiresSynchronizedOnBodyState() {
        val marked = (listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 1000f, 42f) + List(4) { 0f }).joinToString(",")
        assertTrue(preview(listOf(marked), intArrayOf(), longArrayOf(1L)).isEmpty())
        assertTrue(preview(listOf(marked), intArrayOf(0), longArrayOf(1L)).isEmpty())
        assertTrue(preview(listOf(marked), intArrayOf(-1), longArrayOf(1L)).isEmpty())
        assertEquals((1..10).map(Int::toFloat), preview(listOf(marked), intArrayOf(1), longArrayOf(1L)))
        assertEquals((1..10).map(Int::toFloat), preview(listOf(marked, marked, marked), intArrayOf(1, 0, 1), longArrayOf(1L, 2L, 3L)))
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
        assertEquals(expected + expected, preview(rows, contacts, longArrayOf(1_000_000_000L, 2_000_000_000L)))
        assertEquals(expected, preview(rows, contacts, longArrayOf(1_000_000_000L, 2_000_000_001L)))
        assertEquals(expected, preview(rows, contacts, longArrayOf(2L, 1L)))
        assertTrue(preview(rows, contacts, longArrayOf(1L)).isEmpty())
        assertTrue(preview(rows, contacts, longArrayOf(1L, 0L)).isEmpty())
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
