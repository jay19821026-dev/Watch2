package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexDomainPpgFilterTest {
    @Test
    fun medianSmoothingMarksChangedPointsFilteredAndLeavesRawInputUntouched() {
        val raw = listOf(1.0, 100.0, 2.0, 3.0)

        val result = IndexDomainPpgFilter.apply(raw)

        assertEquals(listOf(1.0, 2.0, 3.0, 3.0), result.values)
        assertEquals(listOf(DataOrigin.MEASURED, DataOrigin.FILTERED, DataOrigin.FILTERED, DataOrigin.MEASURED), result.origins)
        assertEquals(2, result.filteredPointCount)
        assertEquals(100.0, raw[1]!!, 0.0)
        assertEquals("ppg-index-median-3-v1", result.algorithmVersion)
    }

    @Test
    fun missingGapsAreNotFilledOrFilteredAcross() {
        val sentinel = PpgCompositionQualityEngine.GAP_SENTINEL
        val raw = listOf(1.0, sentinel, 100.0, 2.0)

        val result = IndexDomainPpgFilter.apply(raw)

        assertEquals(sentinel, result.values[1]!!, 0.0)
        assertEquals(DataOrigin.UNKNOWN, result.origins[1])
        assertEquals(100.0, result.values[2]!!, 0.0)
        assertEquals(2.0, result.values[3]!!, 0.0)
        assertTrue(result.origins.drop(2).none { it == DataOrigin.IMPUTED })
    }

    @Test
    fun nullNanAndShortInputsRemainUnchanged() {
        val raw = listOf(1.0, null, Double.NaN, 4.0)

        val result = IndexDomainPpgFilter.apply(raw)

        assertEquals(1.0, result.values[0]!!, 0.0)
        assertNull(result.values[1])
        assertTrue(result.values[2]!!.isNaN())
        assertEquals(0, result.filteredPointCount)
        assertEquals(DataOrigin.UNKNOWN, result.origins[1])
        assertEquals(DataOrigin.UNKNOWN, result.origins[2])
        assertEquals(listOf(5.0, 1.0), IndexDomainPpgFilter.apply(listOf(5.0, 1.0)).values)
    }
}
