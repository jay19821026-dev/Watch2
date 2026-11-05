package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgDecoderReadinessTest {
    @Test
    fun candidateValuesNeverPromoteToVerifiedEcgSamples() {
        val rows = List(3) { index ->
            ((1..10).map { it + index.toDouble() } + listOf(1.0, index * 10.0, 0.0, 0.0, 0.0, 0.0))
                .joinToString(",")
        }
        val inspection = EcgRawEventDecoder.decode(rows)
        val readiness = EcgDecoderReadiness.assess(
            decodedSampleCount = 0,
            mappingVerified = inspection.layoutComparison?.mappingVerified == true,
            sourceClockVerified = false,
            sampleRateVerified = false
        )

        assertEquals(30, inspection.candidateValueCount)
        assertEquals(EcgDecoderReadiness.Status.RESEARCH_BLOCKED, readiness.status)
        assertTrue(readiness.blockers.contains("field_mapping_unverified"))
        assertTrue(readiness.blockers.contains("source_clock_unverified"))
    }

    @Test
    fun readinessRequiresAllIndependentEvidence() {
        val readiness = EcgDecoderReadiness.assess(32, true, true, false)
        assertEquals(EcgDecoderReadiness.Status.RESEARCH_BLOCKED, readiness.status)
        assertEquals(listOf("sample_rate_unverified"), readiness.blockers)
        assertEquals(
            EcgDecoderReadiness.Status.READY,
            EcgDecoderReadiness.assess(32, true, true, true).status
        )
    }
}
