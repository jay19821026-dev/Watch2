package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgReferenceTraceAlignerTest {
    @Test
    fun pairsNearestTimestampWithoutReusingReferenceSamples() {
        val raw = listOf(
            EcgReferenceTraceAligner.Sample(100L, 10.0),
            EcgReferenceTraceAligner.Sample(200L, 20.0),
            EcgReferenceTraceAligner.Sample(300L, 30.0)
        )
        val reference = listOf(
            EcgReferenceTraceAligner.Sample(110L, 1.0),
            EcgReferenceTraceAligner.Sample(205L, 2.0),
            EcgReferenceTraceAligner.Sample(310L, 3.0)
        )

        val result = EcgReferenceTraceAligner.align(raw, reference, 15L, 10)

        assertEquals(3, result.pairs.size)
        assertEquals(listOf(1.0, 2.0, 3.0), result.pairs.map { it.millivolts })
        assertEquals(0, result.unmatchedRawCount)
        assertEquals(0, result.unmatchedReferenceCount)
    }

    @Test
    fun rejectsPairsOutsideSkewAndPreservesUnmatchedCounts() {
        val result = EcgReferenceTraceAligner.align(
            raw = listOf(EcgReferenceTraceAligner.Sample(100L, 1.0)),
            reference = listOf(EcgReferenceTraceAligner.Sample(200L, 2.0)),
            maximumSkewNanos = 10L,
            maximumPairs = 10
        )

        assertTrue(result.pairs.isEmpty())
        assertEquals(1, result.unmatchedRawCount)
        assertEquals(1, result.unmatchedReferenceCount)
    }

    @Test
    fun capsActualPairSkewAtHalfTheFasterSamplePeriod() {
        val raw = (0L..5L).map { EcgReferenceTraceAligner.Sample(1_000_000L + it * 10_000_000L, it.toDouble()) }
        val reference = (0L..5L).map { EcgReferenceTraceAligner.Sample(7_000_000L + it * 10_000_000L, it.toDouble()) }

        val result = EcgReferenceTraceAligner.align(raw, reference, 1_000_000_000L, 10)

        assertTrue(result.pairs.isNotEmpty())
        assertTrue(result.pairs.all { kotlin.math.abs(it.rawTimestampNanos - it.referenceTimestampNanos) <= 5_000_000L })
    }

    @Test
    fun rejectsInvalidOrNonMonotonicTraceInsteadOfSortingIt() {
        val result = EcgReferenceTraceAligner.align(
            raw = listOf(
                EcgReferenceTraceAligner.Sample(200L, 1.0),
                EcgReferenceTraceAligner.Sample(100L, 2.0)
            ),
            reference = listOf(EcgReferenceTraceAligner.Sample(100L, 3.0)),
            maximumSkewNanos = 10L,
            maximumPairs = 10
        )

        assertNotNull(result.error)
        assertTrue(result.pairs.isEmpty())
    }

    @Test
    fun boundsLargeAlignedTraceByEvenlySamplingPairs() {
        val raw = (1L..100L).map { EcgReferenceTraceAligner.Sample(it * 100L, it.toDouble()) }
        val reference = (1L..100L).map { EcgReferenceTraceAligner.Sample(it * 100L, it.toDouble() / 10.0) }

        val result = EcgReferenceTraceAligner.align(raw, reference, 1L, 10)

        assertEquals(10, result.pairs.size)
        assertEquals(1.0, result.pairs.first().raw, 0.0)
        assertEquals(100.0, result.pairs.last().raw, 0.0)
    }
}
