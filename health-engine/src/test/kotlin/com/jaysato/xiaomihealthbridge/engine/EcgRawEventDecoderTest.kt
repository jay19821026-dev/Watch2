package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgRawEventDecoderTest {
    @Test
    fun keepsFirstTenFieldsAsUnverifiedCandidatesWithoutClaimingDecodedWaveform() {
        val result = EcgRawEventDecoder.decode(listOf(row(10), row(20)))

        assertEquals("candidate_values_unverified", result.status)
        assertEquals(2, result.eventCount)
        assertEquals((0..9).map(Int::toDouble) + (10..19).map(Int::toDouble), result.candidateValues)
        assertEquals(false, result.layoutComparison?.mappingVerified)
        assertEquals(1, result.layoutComparison?.counterPlusTenTransitions)
        assertEquals(1.0, result.layoutComparison?.rowMajorMedianAbsoluteDelta ?: Double.NaN, 0.0)
        assertEquals(10.0, result.layoutComparison?.columnMajorMedianAbsoluteDelta ?: Double.NaN, 0.0)
    }

    @Test
    fun summarizesAllObservedFieldsWithoutPromotingThemToSamples() {
        val result = EcgRawEventDecoder.decode(listOf(row(10), row(20)))
        val fields = result.layoutComparison!!.fieldSummaries

        assertEquals(20, result.candidateValueCount)
        assertEquals(32, result.inspectedFieldValueCount)
        assertEquals(16, fields.size)
        assertEquals(2, fields[0].finiteValueCount)
        assertEquals(1, fields[0].zeroValueCount)
        assertEquals(0.0, fields[0].minimum, 0.0)
        assertEquals(10.0, fields[0].maximum, 0.0)
        assertEquals(5.0, fields[0].mean, 0.0)
        assertEquals(1, fields[0].changedTransitionCount)
        assertEquals(1, fields[0].transitionCount)
        assertEquals(listOf(0.0, 1.0), fields[0].normalizedEventIndexPreview)
        assertEquals(0, fields[11].zeroValueCount)
        assertEquals(15.0, fields[11].mean, 0.0)
        assertEquals(1, fields[11].changedTransitionCount)
        assertEquals(2, fields[12].zeroValueCount)
        assertEquals(0.0, fields[12].mean, 0.0)
        assertEquals("candidate_values_unverified", result.status)
        assertEquals(false, result.layoutComparison?.mappingVerified)
    }

    @Test
    fun preservesAllValuesAndSeparatesCandidateCountFor216By16Rows() {
        val rows = List(216) { event ->
            val candidateFields = (0 until 10).map { field -> (event + field).toDouble() }
            (candidateFields + listOf(1.0, (event * 10).toDouble(), 0.0, 0.0, 0.0, 0.0))
                .joinToString(",")
        }
        val result = EcgRawEventDecoder.decode(rows)

        assertEquals(216, result.eventCount)
        assertEquals(2_160, result.candidateValueCount)
        assertEquals(3_456, result.inspectedFieldValueCount)
        assertEquals(16, result.layoutComparison?.fieldSummaries?.size)
        assertTrue(result.layoutComparison!!.fieldSummaries.all { it.finiteValueCount == 216 })
        assertTrue(result.layoutComparison.fieldSummaries.all {
            it.normalizedEventIndexPreview.size == 216 && it.normalizedEventIndexPreview.all { value ->
                value != null && value in 0.0..1.0
            }
        })
        assertEquals("candidate_values_unverified", result.status)
        assertEquals(false, result.layoutComparison.mappingVerified)
    }

    @Test
    fun concludesRepeatedPacketPatternWithoutClaimingEcgInterpretation() {
        val eventCount = 20
        val rows = List(eventCount) { event ->
            val changingValues = (0 until 10).map { field -> (event * 2 + field).toDouble() }
            (changingValues + listOf(1.0, (1000 + event * 10).toDouble(), 0.0, 0.0, 0.0, 0.0))
                .joinToString(",")
        }
        val decoded = EcgRawEventDecoder.decode(rows)

        val conclusion = EcgEventStructureInterpreter.interpret(eventCount, decoded.layoutComparison)

        assertEquals("candidate_packet_pattern_observed", conclusion.status)
        assertTrue(conclusion.headline.contains("封包欄位模式"))
        assertTrue(conclusion.details.any { it.contains("不是已驗證的 ECG 樣本映射") })
        assertTrue(conclusion.details.last().contains("不得據此計算 bpm"))
        assertEquals(false, decoded.layoutComparison?.mappingVerified)
    }

    @Test
    fun doesNotCallIrregularWidthsAStablePacketPattern() {
        val conclusion = EcgEventStructureInterpreter.interpret(eventCount = 1, layout = null)

        assertEquals("insufficient_structure_evidence", conclusion.status)
        assertTrue(conclusion.headline.contains("資料不足"))
    }

    @Test
    fun boundsNormalizedPreviewsAndKeepsFlatFieldsNeutral() {
        val rows = List(1_000) { event ->
            (listOf(event.toDouble()) + List(15) { 7.0 }).joinToString(",")
        }
        val fields = EcgRawEventDecoder.decode(rows).layoutComparison!!.fieldSummaries

        assertEquals(EcgRawEventDecoder.MAX_PREVIEW_POINTS, fields[0].normalizedEventIndexPreview.size)
        assertEquals(0.0, fields[0].normalizedEventIndexPreview.first()!!, 0.0)
        assertEquals(1.0, fields[0].normalizedEventIndexPreview.last()!!, 0.0)
        assertTrue(fields.drop(1).all { field -> field.normalizedEventIndexPreview.all { it == 0.5 } })
    }

    @Test
    fun doesNotInterpretCounterJumpAsVerifiedMissingSampleLayout() {
        val result = EcgRawEventDecoder.decode(listOf(row(10), row(31)))

        assertEquals(20, result.candidateValues.size)
        assertEquals("candidate_values_unverified", result.status)
    }

    @Test
    fun doesNotTreatCounterPlusTwentyAsProofOfTenMissingSignalSamples() {
        val result = EcgRawEventDecoder.decode(listOf(row(10), row(30)))

        assertEquals("candidate_values_unverified", result.status)
        assertEquals(2, result.eventCount)
        assertEquals(20, result.candidateValueCount)
        assertEquals(20, result.candidateValues.size)
    }

    @Test
    fun counterResetAndJumpRemainUnverifiedInsteadOfRejectingOriginalEvents() {
        assertEquals("candidate_values_unverified", EcgRawEventDecoder.decode(listOf(row(30), row(10))).status)
        assertEquals("candidate_values_unverified", EcgRawEventDecoder.decode(listOf(row(10), row(25))).status)
    }

    @Test
    fun zeroRowsRemainZeroAndNeverBecomeASample() {
        val result = EcgRawEventDecoder.decode(emptyList())

        assertEquals(0, result.eventCount)
        assertTrue(result.candidateValues.isEmpty())
        assertEquals("no_rows", result.status)
    }

    private fun row(counter: Int): String =
        ((0..9).map { counter - 10 + it } + listOf(1, counter, 0, 0, 0, 0))
            .joinToString(",")
}
