package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgPayloadInterpretationPolicyTest {
    @Test
    fun unverifiedAndLegacyEncodingsCannotEnterWaveformInterpretation() {
        listOf(null, "", "hal_event_values_unscaled", "ecg_packed10", "ecg_baseline_relative_unitless")
            .forEach { assertFalse(EcgPayloadInterpretationPolicy.canInterpretAsWaveform(it)) }
    }

    @Test
    fun onlyExplicitVerifiedWaveformContractCanEnterInterpretation() {
        assertTrue(
            EcgPayloadInterpretationPolicy.canInterpretAsWaveform(
                EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
            )
        )
    }

    @Test
    fun relativeResearchWaveformRemainsVisibleWithoutPhysicalCalibration() {
        assertTrue(EcgPayloadInterpretationPolicy.canInterpretAsResearchWaveform("ecg_baseline_relative_unitless"))
        assertFalse(EcgPayloadInterpretationPolicy.canInterpretAsWaveform("ecg_baseline_relative_unitless"))
    }

    @Test
    fun legacyResearchWaveformsCannotBeReplayedAsDecodedSignals() {
        assertFalse(EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
            "max86178_hal_10_signal_values_research_v1", "decoded_research_unscaled"
        ))
        assertFalse(EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
            EcgPayloadInterpretationPolicy.UNMAPPED_EVENT_PREVIEW, "decoded_research_unscaled"
        ))
        assertFalse(EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
            EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING, "candidate_values_unverified"
        ))
        assertTrue(EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
            EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING, "decoded_research_unscaled"
        ))
    }

    @Test
    fun oldCachedEncodingsCannotEnterVerifiedWaveformDisplay() {
        listOf("ecg_external_reference_mv", "ecg_baseline_relative_unitless", "legacy_unverified")
            .forEach { encoding -> assertFalse(EcgPayloadInterpretationPolicy.canInterpretAsWaveform(encoding)) }
    }

    @Test
    fun derivedRhythmNeedsVerifiedEncodingClockAndRate() {
        val encoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        assertFalse(EcgPayloadInterpretationPolicy.canUseDerivedRhythm("legacy_unverified", true, 250f, true, true, true))
        assertFalse(EcgPayloadInterpretationPolicy.canUseDerivedRhythm(encoding, false, 250f, true, true, true))
        assertFalse(EcgPayloadInterpretationPolicy.canUseDerivedRhythm(encoding, true, 0f, true, true, true))
        assertFalse(EcgPayloadInterpretationPolicy.canUseDerivedRhythm(encoding, true, 250f, false, true, true))
        assertFalse(EcgPayloadInterpretationPolicy.canUseDerivedRhythm(encoding, true, 250f, true, false, true))
        assertTrue(EcgPayloadInterpretationPolicy.canUseDerivedRhythm(encoding, true, 250f, true, true, true))
        assertFalse(EcgPayloadInterpretationPolicy.canUseDerivedRhythm(encoding, true, 250f, true, true, false))
    }
}
