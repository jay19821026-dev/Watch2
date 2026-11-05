package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgHalEventResearchDecoderTest {
    @Test
    fun reportsCandidateFieldCountButNeverClaimsDecodedWaveformOrSamplingClock() {
        val rows = listOf(
            row(1000.0),
            row(1010.0),
            row(1020.0)
        ).joinToString(";")
        val result = EcgHalEventResearchDecoder.decode(rows, listOf(1_000_000_000L, 1_020_000_000L, 1_040_000_000L))

        assertEquals("candidate_values_unverified", result.status)
        assertEquals(0, result.waveform.size)
        assertEquals(3, result.eventCount)
        assertEquals(null, result.estimatedSampleRateHz)
        assertEquals(0, result.decodedSampleCount)
        assertEquals(30, result.candidateValueCount)
        assertEquals(false, result.layoutComparison?.mappingVerified)
        assertEquals(EcgHalEventResearchDecoder.DECODE_STATE_EVENTS_PRESENT_ZERO_POINTS, result.decodeState)
    }

    @Test
    fun treatsNumericSixteenFieldRowsAsUnverifiedCandidates() {
        val invalid = (1..16).joinToString(",") { it.toString() }
        val result = EcgHalEventResearchDecoder.decode(invalid, listOf(1L))

        assertTrue(result.waveform.isEmpty())
        assertEquals("candidate_values_unverified", result.status)
        assertEquals(0, result.waveform.size)
        assertEquals(10, result.candidateValueCount)
        assertEquals(EcgHalEventResearchDecoder.DECODE_STATE_EVENTS_PRESENT_ZERO_POINTS, result.decodeState)
    }

    @Test
    fun eventTimestampsNeverPromoteUnverifiedFieldsToWaveformClock() {
        val rows = listOf(row(1000.0), row(1010.0), row(1020.0)).joinToString(";")

        val extraTimestamp = EcgHalEventResearchDecoder.decode(
            rows,
            listOf(1_000_000_000L, 1_020_000_000L, 1_040_000_000L, 1_060_000_000L)
        )
        val missingTimestamp = EcgHalEventResearchDecoder.decode(
            rows,
            listOf(1_000_000_000L, 1_020_000_000L)
        )

        assertEquals(0, extraTimestamp.waveform.size)
        assertEquals(0, missingTimestamp.waveform.size)
        assertEquals("candidate_values_unverified", extraTimestamp.status)
        assertEquals("candidate_values_unverified", missingTimestamp.status)
        assertEquals(null, extraTimestamp.estimatedSampleRateHz)
        assertEquals(null, missingTimestamp.estimatedSampleRateHz)
        assertEquals(EcgHalEventResearchDecoder.DECODE_STATE_EVENTS_PRESENT_ZERO_POINTS, extraTimestamp.decodeState)
        assertEquals(EcgHalEventResearchDecoder.DECODE_STATE_EVENTS_PRESENT_ZERO_POINTS, missingTimestamp.decodeState)

        val noEvents = EcgHalEventResearchDecoder.decode("", emptyList())
        assertEquals(EcgHalEventResearchDecoder.DECODE_STATE_NO_EVENTS, noEvents.decodeState)
    }

    @Test
    fun neverConvertsSourceTimestampEvidenceBeforeFieldMappingIsVerified() {
        val rows = listOf(row(1000.0), row(1010.0), row(1020.0)).joinToString(";")
        val results = listOf(
            listOf(1_000_000_000L, 0L, 1_040_000_000L),
            listOf(1_040_000_000L, 1_020_000_000L, 1_000_000_000L),
            listOf(1_000_000_000L, 1_000_000_000L, 1_040_000_000L)
        ).map { EcgHalEventResearchDecoder.decode(rows, it) }

        assertTrue(results.all { it.waveform.isEmpty() })
        assertTrue(results.all { it.estimatedSampleRateHz == null })
    }

    @Test
    fun doesNotInferGapSamplesFromCounterDeltas() {
        val rows = listOf(row(1000.0), row(1020.0)).joinToString(";")
        val result = EcgHalEventResearchDecoder.decode(rows, listOf(1_000_000_000L, 1_020_000_000L))

        assertEquals("candidate_values_unverified", result.status)
        assertEquals(2, result.eventCount)
        assertEquals(0, result.missingSampleCount)
        assertEquals(0, result.decodedSampleCount)
        assertEquals(0, result.waveform.size)
        assertEquals(20, result.candidateValueCount)
        assertEquals(null, result.estimatedSampleRateHz)
    }

    private fun row(counter: Double): String =
        ((1..10).map(Int::toDouble) + listOf(1.0, counter, 0.0, 0.0, 0.0, 0.0)).joinToString(",")
}
