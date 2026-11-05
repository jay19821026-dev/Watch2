package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgRhythmPatternEngineTest {
    @Test fun stableIntervalsAreDescriptiveOnly() {
        val result = EcgRhythmPatternEngine.assess(List(12) { 800.0 })

        assertEquals(EcgRhythmPatternEngine.Label.RELATIVELY_REGULAR, result.label)
        assertTrue(result.title.contains("不排除心律異常"))
        assertTrue(result.title.contains("研究描述"))
    }

    @Test fun variableIntervalsNeverClaimAfDiagnosis() {
        val result = EcgRhythmPatternEngine.assess(listOf(600.0, 1000.0, 650.0, 1050.0, 700.0, 1100.0, 620.0, 1020.0))

        assertEquals(EcgRhythmPatternEngine.Label.VARIABLE, result.label)
        assertTrue(result.title.contains("不是 AF 診斷"))
    }

    @Test fun insufficientOrOutOfWindowEvidenceDoesNotClassify() {
        assertEquals(EcgRhythmPatternEngine.Label.INSUFFICIENT, EcgRhythmPatternEngine.assess(listOf(800.0, 810.0, 790.0)).label)
        assertEquals(EcgRhythmPatternEngine.Label.INSUFFICIENT, EcgRhythmPatternEngine.assess(List(8) { 2200.0 }).label)
        assertEquals(EcgRhythmPatternEngine.Label.INCONCLUSIVE, EcgRhythmPatternEngine.assess(List(8) { 1500.0 }).label)
    }
}
