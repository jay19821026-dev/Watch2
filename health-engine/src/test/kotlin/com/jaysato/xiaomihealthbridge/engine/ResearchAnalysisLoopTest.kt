package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchAnalysisLoopTest {
    @Test fun secondPassUsesDerivedEvidenceAndThenStops() {
        val result = ResearchAnalysisLoop.analyze(
            listOf(2.0, 0.0, 4.0), ZeroEvidencePolicy(ZeroMeaning.MISSING_SENTINEL)
        )
        assertEquals(listOf(2.0, 2.0, 4.0), result.values)
        assertEquals(2, result.passes.size)
        assertTrue(result.informative)
        assertEquals("EVIDENCE_STABLE", result.terminalReason)
    }

    @Test fun noEvidenceDoesNotBecomeAValue() {
        val result = ResearchAnalysisLoop.analyze(
            listOf(null, Double.NaN), ZeroEvidencePolicy(ZeroMeaning.MISSING_SENTINEL)
        )
        assertTrue(!result.informative)
        assertEquals("NO_FINITE_EVIDENCE", result.terminalReason)
    }
}
