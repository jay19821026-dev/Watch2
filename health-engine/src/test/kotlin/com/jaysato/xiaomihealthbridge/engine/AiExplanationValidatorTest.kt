package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.*
import org.junit.Test

class AiExplanationValidatorTest {
    private val provenance = SensorProvenance("session", "policy", "deterministic facts", "v1", null, 0.0, "UNKNOWN", TimestampSource.UNKNOWN, null, 1L)
    private val facts = listOf(
        ExplanationFact("clock", "取樣時鐘未知；callback 不是感測器時間。", provenance),
        ExplanationFact("limits", "不可換算 SpO2 百分比、ECG mV 或 BioZ Ω。", provenance)
    )
    private val validClaims = facts.map { ModelExplanationClaim(it.id, it.verifiedText) }

    @Test fun acceptsOnlyExactKnownFacts() {
        assertTrue(AiExplanationValidator.validate(facts, validClaims).accepted)
    }

    @Test fun rejectsUnsupportedNumbersUnitsTimesAndCauses() {
        listOf("SpO2 = 97%", "ECG = 0.8 mV", "BioZ = 450 Ω", "PPG = 100 Hz", "sensor acquisition time = callback time", "vendor firmware bug").forEach { text ->
            val result = AiExplanationValidator.validate(facts, listOf(ModelExplanationClaim("clock", text), validClaims[1]))
            assertFalse(result.accepted)
            assertEquals(facts.joinToString("\n") { it.verifiedText }, result.text)
        }
    }

    @Test fun rejectsOmittedLimitsUnknownReferencesAndDuplicates() {
        assertFalse(AiExplanationValidator.validate(facts, validClaims.take(1)).accepted)
        assertFalse(AiExplanationValidator.validate(facts, validClaims + validClaims[0]).accepted)
        assertFalse(AiExplanationValidator.validate(facts, listOf(ModelExplanationClaim("invented", "x"))).accepted)
    }

    @Test fun blankEvidenceReturnsUnknownWithoutModelClaims() {
        val result = AiExplanationValidator.validate(emptyList(), emptyList())
        assertFalse(result.accepted)
        assertTrue(result.text.startsWith("未知"))
    }
}
