package com.jaysato.xiaomihealthbridge.engine

data class EcgDecoderReadiness(
    val status: Status,
    val blockers: List<String>
) {
    enum class Status { READY, RESEARCH_BLOCKED }

    companion object {
        fun assess(
            decodedSampleCount: Int,
            mappingVerified: Boolean,
            sourceClockVerified: Boolean,
            sampleRateVerified: Boolean
        ): EcgDecoderReadiness {
            val blockers = buildList {
                if (decodedSampleCount <= 0) add("no_verified_ecg_samples")
                if (!mappingVerified) add("field_mapping_unverified")
                if (!sourceClockVerified) add("source_clock_unverified")
                if (!sampleRateVerified) add("sample_rate_unverified")
            }
            return EcgDecoderReadiness(
                if (blockers.isEmpty()) Status.READY else Status.RESEARCH_BLOCKED,
                blockers
            )
        }
    }
}
