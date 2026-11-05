package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroEvidenceInterceptorTest {
    @Test fun measuredZeroIsNeverRemoved() {
        val result = ZeroEvidenceInterceptor.apply(
            listOf(2.0, 0.0, 4.0), ZeroEvidencePolicy(ZeroMeaning.MEASURED_ZERO)
        )
        assertEquals(listOf(2.0, 0.0, 4.0), result.rawValues)
        assertEquals(result.rawValues, result.analysisValues)
        assertTrue(result.replacedIndices.isEmpty())
    }

    @Test fun sentinelZeroUsesPreviousEvidenceOnlyInDerivedSeries() {
        val result = ZeroEvidenceInterceptor.apply(
            listOf(2.0, 0.0, 4.0), ZeroEvidencePolicy(ZeroMeaning.MISSING_SENTINEL)
        )
        assertEquals(listOf(2.0, 0.0, 4.0), result.rawValues)
        assertEquals(listOf(2.0, 2.0, 4.0), result.analysisValues)
        assertEquals(listOf(1), result.replacedIndices)
    }

    @Test fun sentinelWithoutContextDoesNotInventValue() {
        val result = ZeroEvidenceInterceptor.apply(
            listOf(0.0, null), ZeroEvidencePolicy(ZeroMeaning.MISSING_SENTINEL)
        )
        assertNull(result.analysisValues[0])
        assertTrue(result.replacedIndices.isEmpty())
    }
    @Test fun autoPolicyUsesProducerEvidence() {
        val measured = ZeroEvidenceInterceptor.apply(
            listOf(2.0, 0.0, 4.0), ZeroEvidencePolicy(ZeroMeaning.AUTO), producerMarksZeroAsMissing = false
        )
        val sentinel = ZeroEvidenceInterceptor.apply(
            listOf(2.0, 0.0, 4.0), ZeroEvidencePolicy(ZeroMeaning.AUTO), producerMarksZeroAsMissing = true
        )
        assertEquals(listOf(2.0, 0.0, 4.0), measured.analysisValues)
        assertEquals(listOf(2.0, 2.0, 4.0), sentinel.analysisValues)
    }
}
