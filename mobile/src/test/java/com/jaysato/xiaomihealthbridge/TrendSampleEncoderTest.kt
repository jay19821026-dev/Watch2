package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendSampleEncoderTest {
    @Test
    fun downsamplesLargeTrendAndPreservesEndpoints() {
        val encoded = TrendSampleEncoder.encode((0 until 50_000).asSequence().map(Int::toDouble), 50_000)
        val values = encoded.split(",").map(String::toDouble)

        assertTrue(values.size <= TrendSampleEncoder.MAX_POINTS)
        assertEquals(0.0, values.first(), 0.0)
        assertEquals(49_999.0, values.last(), 0.0)
    }

    @Test
    fun removesNonFiniteSamples() {
        assertEquals("1.0,2.0", TrendSampleEncoder.encode(listOf(1.0, Double.NaN, 2.0, Double.POSITIVE_INFINITY)))
    }

    @Test
    fun enforcesPointLimitWhenCountEstimateIsTooSmall() {
        val encoded = TrendSampleEncoder.encode((0 until 1_000).asSequence().map(Int::toDouble), 1)
        val values = encoded.split(",").map(String::toDouble)

        assertEquals(TrendSampleEncoder.MAX_POINTS, values.size)
        assertEquals(999.0, values.last(), 0.0)
    }

    @Test
    fun encodesEmptyInputAsEmptyTrend() {
        assertEquals("", TrendSampleEncoder.encode(emptyList()))
    }
}
