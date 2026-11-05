package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchAlgorithmRegistryTest {
    @Test fun biozMetricIsExplicitlyNotImpedance() {
        val spec = ResearchAlgorithmRegistry.find("bioz_waveform_median_position")
        assertNotNull(spec)
        assertTrue(spec!!.formulaDescription.contains("not impedance"))
    }

    @Test fun relativeSignalIsNotPresentedAsClinicalUnit() {
        val result = ResearchAlgorithmRegistry.interpret("ecg_relative_waveform", 0.4, "index")
        assertEquals(NumericMeaning.RELATIVE_INDEX, result.meaning)
        assertEquals(Interpretation.NOT_APPLICABLE, result.interpretation)
        assertTrue(result.reason.contains("WITHOUT_COMPARABLE"))
    }

    @Test fun missingValueIsInsufficientEvidence() {
        val result = ResearchAlgorithmRegistry.interpret("ppg_quality", null)
        assertEquals(Interpretation.INSUFFICIENT_EVIDENCE, result.interpretation)
    }
}
