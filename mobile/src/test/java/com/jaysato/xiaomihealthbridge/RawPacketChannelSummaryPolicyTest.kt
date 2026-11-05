package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawPacketChannelSummaryPolicyTest {
    @Test
    fun computesFiniteMeanAndLastFiniteValueWithoutErasingGapCount() {
        val summary = RawPacketChannelSummaryPolicy.summarize(listOf(1.0, 3.0, Double.NaN))

        assertEquals(3, summary.totalCount)
        assertEquals(2, summary.finiteCount)
        assertEquals(1, summary.missingCount)
        assertEquals(3.0, summary.lastFiniteValue!!, 0.0)
        assertEquals(2.0, summary.finiteMean!!, 0.0)
    }

    @Test
    fun preservesRealZeroAndReportsNoFiniteValueAsAbsent() {
        val withZero = RawPacketChannelSummaryPolicy.summarize(listOf(0.0, Double.NaN))
        val withoutFiniteValues = RawPacketChannelSummaryPolicy.summarize(listOf(Double.NaN))

        assertEquals(0.0, withZero.lastFiniteValue!!, 0.0)
        assertEquals(0.0, withZero.finiteMean!!, 0.0)
        assertEquals(1, withZero.missingCount)
        assertNull(withoutFiniteValues.lastFiniteValue)
        assertNull(withoutFiniteValues.finiteMean)
        assertEquals(1, withoutFiniteValues.missingCount)
    }

    @Test
    fun countsInfiniteValuesAsMissingAndKeepsEmptyChannelUnscored() {
        val mixed = RawPacketChannelSummaryPolicy.summarize(
            listOf(Double.NEGATIVE_INFINITY, 2.0, Double.POSITIVE_INFINITY)
        )
        val empty = RawPacketChannelSummaryPolicy.summarize(emptyList())

        assertEquals(3, mixed.totalCount)
        assertEquals(1, mixed.finiteCount)
        assertEquals(2, mixed.missingCount)
        assertEquals(2.0, mixed.lastFiniteValue!!, 0.0)
        assertEquals(2.0, mixed.finiteMean!!, 0.0)
        assertEquals(0, empty.totalCount)
        assertEquals(0, empty.finiteCount)
        assertEquals(0, empty.missingCount)
        assertNull(empty.lastFiniteValue)
        assertNull(empty.finiteMean)
    }
}
