package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EcgOpaqueClockEvidenceTest {
    @Test
    fun observesCandidateCounterWithoutClaimingAdcClock() {
        val timestamps = listOf(1_000_000_000L, 1_020_000_000L, 1_040_000_000L)
        val rows = listOf(row(100), row(110), row(120))

        val result = EcgOpaqueClockEvidenceAnalyzer.analyze(timestamps, rows)

        assertEquals(EcgOpaqueClockEvidence.Status.CANDIDATE_COUNTER_OBSERVED, result.status)
        assertEquals(50.0, result.eventCadenceHz!!, 0.0001)
        assertEquals(500.0, result.candidateCounterUnitsPerSecond!!, 0.0001)
        assertEquals(2, result.candidateCounterStepCount)
        assertFalse(result.adcSampleClockVerified)
    }

    @Test
    fun rejectsDuplicateOrBackwardEventTimeline() {
        val rows = listOf(row(100), row(110), row(120))
        val duplicate = EcgOpaqueClockEvidenceAnalyzer.analyze(
            listOf(1_000L, 1_000L, 1_100L), rows
        )
        val backward = EcgOpaqueClockEvidenceAnalyzer.analyze(
            listOf(1_000L, 900L, 1_100L), rows
        )

        assertEquals(EcgOpaqueClockEvidence.Status.EVENT_TIMELINE_INVALID, duplicate.status)
        assertEquals(1, duplicate.duplicateTimestampCount)
        assertNull(duplicate.eventCadenceHz)
        assertEquals(EcgOpaqueClockEvidence.Status.EVENT_TIMELINE_INVALID, backward.status)
        assertEquals(1, backward.backwardTimestampCount)
    }

    @Test
    fun keepsOrderedTimelineWhenCounterIsDiscontinuous() {
        val result = EcgOpaqueClockEvidenceAnalyzer.analyze(
            listOf(1_000L, 2_000L, 3_000L),
            listOf(row(100), row(130), row(140))
        )

        assertEquals(EcgOpaqueClockEvidence.Status.ORDERED_EVENT_TIMELINE, result.status)
        assertEquals(1, result.candidateCounterStepCount)
        assertEquals(1, result.candidateCounterDiscontinuityCount)
        assertFalse(result.adcSampleClockVerified)
    }

    @Test
    fun malformedRowsDoNotPromoteCounterEvidence() {
        val result = EcgOpaqueClockEvidenceAnalyzer.analyze(
            listOf(1_000L, 2_000L),
            listOf(row(100), "malformed")
        )

        assertEquals(EcgOpaqueClockEvidence.Status.ORDERED_EVENT_TIMELINE, result.status)
        assertNull(result.candidateCounterUnitsPerSecond)
        assertFalse(result.adcSampleClockVerified)
    }

    private fun row(counter: Long): String = List(16) { index ->
        when (index) {
            in 0..9 -> (counter + index).toString()
            10 -> "1"
            11 -> counter.toString()
            else -> "0"
        }
    }.joinToString(",")
}
