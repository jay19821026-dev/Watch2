package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthAlgorithmEngineTest {
    @Test
    fun rejectsInvalidResearchStressInputs() {
        assertNull(HealthAlgorithmEngine.researchStressScore(Double.NaN, 60.0))
        assertNull(HealthAlgorithmEngine.researchStressScore(-0.01, 60.0))
        assertNull(HealthAlgorithmEngine.researchStressScore(2.01, 60.0))
        assertNull(HealthAlgorithmEngine.researchStressScore(0.1, Double.NaN))
        assertNull(HealthAlgorithmEngine.researchStressScore(0.1, 241.0))
    }

    @Test
    fun hrvAndPressureFormulasAreDeterministic() {
        val result = HealthAlgorithmEngine.hrvFromRr(listOf(800.0, 810.0, 790.0, 805.0, 795.0))
        assertEquals(5, result.rrCount)
        assertTrue(result.rmssdMs!! > 0.0)
        assertEquals(0.0, HealthAlgorithmEngine.pressureAltitude(1013.25).altitudeM!!, 0.5)
        assertTrue(HealthAlgorithmEngine.pressureAltitude(1000.0).note.contains("非實際海拔／爬升"))
    }
}
