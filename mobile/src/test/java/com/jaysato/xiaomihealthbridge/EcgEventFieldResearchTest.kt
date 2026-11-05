package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgEventFieldResearchTest {
    @Test
    fun persistsAllSixteenPerFieldIndexPreviewsWithoutClaimingDecodedEcgSamples() {
        val rows = (0 until 4).map { eventIndex ->
            (0 until EcgHalEventResearchDecoder.OBSERVED_ROW_WIDTH).map { fieldIndex ->
                if (fieldIndex == 15) 0.0 else fieldIndex * 10.0 + eventIndex
            }.joinToString(",")
        }.joinToString(";")
        val decoded = EcgHalEventResearchDecoder.decode(
            rows,
            listOf(1_000_000_000L, 1_020_000_000L, 1_040_000_000L, 1_060_000_000L)
        )

        val summary = requireNotNull(EcgEventFieldResearch.summarize(decoded))

        assertEquals(16, summary.fields.size)
        assertEquals(4, summary.eventCount)
        assertEquals(64, summary.inspectedFieldValueCount)
        assertEquals("candidate_values_unverified", summary.status)
        assertEquals((0 until 16).toList(), summary.fields.map { it.fieldIndex })
        assertEquals(4, summary.fields[15].finiteEventCount)
        assertEquals(4, summary.fields[15].zeroValueCount)
        assertEquals(listOf(0.5, 0.5, 0.5, 0.5), summary.fields[15].relativeAmplitude)
        assertTrue(summary.fields.take(15).all {
            it.relativeAmplitude.all { value -> value?.let { normalized -> normalized in 0.0..1.0 } == true }
        })
    }

    @Test
    fun doesNotPersistInvalidOrEmptyHalRowsAsFieldResearch() {
        val empty = EcgHalEventResearchDecoder.decode("", emptyList())
        val invalid = EcgHalEventResearchDecoder.decode("not,a,valid,row", emptyList())

        assertNull(EcgEventFieldResearch.summarize(empty))
        assertNull(EcgEventFieldResearch.summarize(invalid))
    }
}
