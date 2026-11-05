package com.jaysato.xiaomihealthbridge

object EcgPayloadInterpretationPolicy {
    const val UNMAPPED_EVENT_PREVIEW = "hal_event_values_unmapped_preview"
    const val VERIFIED_WAVEFORM_ENCODING = "verified_ecg_samples_v1"
    const val VERIFIED_ECG_EVENT_CONTRACT_AVAILABLE = false

    fun canInterpretAsWaveform(encoding: String?): Boolean =
        encoding == VERIFIED_WAVEFORM_ENCODING

    fun canInterpretAsResearchWaveform(encoding: String?): Boolean =
        canInterpretAsWaveform(encoding) || encoding == "ecg_baseline_relative_unitless" ||
            encoding == "ecg_external_reference_mv"

    fun canUsePersistedResearchWaveform(encoding: String?, status: String?): Boolean =
        canInterpretAsWaveform(encoding) && status == "decoded_research_unscaled"

    fun canUseDerivedRhythm(
        encoding: String?,
        sourceClockVerified: Boolean,
        sampleRateHz: Float,
        channelMappingVerified: Boolean,
        samplesPerEventKnown: Boolean,
        producerContractVerified: Boolean
    ): Boolean = canInterpretAsWaveform(encoding) && sourceClockVerified &&
        sampleRateHz.isFinite() && sampleRateHz > 0f && channelMappingVerified && samplesPerEventKnown &&
        producerContractVerified
}
