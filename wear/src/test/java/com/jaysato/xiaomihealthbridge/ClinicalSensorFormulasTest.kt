package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.HealthAlgorithmEngine as ClinicalSensorFormulas

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClinicalSensorFormulasTest {
    @Test
    fun researchStressScoreRejectsInvalidInputsInsteadOfClampingThem() {
        assertNull(ClinicalSensorFormulas.researchStressScore(Double.NaN, 60.0))
        assertNull(ClinicalSensorFormulas.researchStressScore(-0.01, 60.0))
        assertNull(ClinicalSensorFormulas.researchStressScore(2.01, 60.0))
        assertNull(ClinicalSensorFormulas.researchStressScore(0.1, Double.NaN))
        assertNull(ClinicalSensorFormulas.researchStressScore(0.1, 0.0))
        assertNull(ClinicalSensorFormulas.researchStressScore(0.1, 241.0))
        assertNotNull(ClinicalSensorFormulas.researchStressScore(0.1, null))
    }
}
