package com.jaysato.xiaomihealthbridge

import org.junit.Assert.*
import org.junit.Test

class ResearchCompositeModelTest {
    @Test fun pairedBaselineAndSleepProduceFourBoundedScores() {
        val result = ResearchCompositeModel.calculate(40.0, 40.0, 8.0, true)
        assertEquals(50, result.stress)
        assertEquals(100, result.recovery)
        assertEquals(80, result.bodyEnergy)
        assertEquals(90, result.readiness)
    }
    @Test fun incompleteOrUnpairedEvidenceNeverProducesComposite() {
        assertNull(ResearchCompositeModel.calculate(40.0, 40.0, 8.0, false).readiness)
        assertNull(ResearchCompositeModel.calculate(40.0, Double.NaN, 8.0, true).stress)
        assertNull(ResearchCompositeModel.calculate(40.0, 40.0, null, true).recovery)
        assertNull(ResearchCompositeModel.calculate(40.0, 0.0, 8.0, true).readiness)
    }
    @Test fun sessionWindowWithoutRecordedSleepCannotProduceRecoveryScores() {
        val result = ResearchCompositeModel.calculate(40.0, 40.0, null, true)
        assertEquals(50, result.stress)
        assertNull(result.recovery)
        assertNull(result.bodyEnergy)
        assertNull(result.readiness)
    }
    @Test fun reducedHrvAndShorterSleepLowerRecovery() {
        val baseline = ResearchCompositeModel.calculate(40.0, 40.0, 8.0, true)
        val reduced = ResearchCompositeModel.calculate(20.0, 40.0, 4.0, true)
        assertTrue(reduced.stress!! > baseline.stress!!)
        assertTrue(reduced.recovery!! < baseline.recovery!!)
    }
}
