package com.jaysato.xiaomihealthbridge

internal object EcgEvidencePresencePolicy {
    fun hasEvidence(rawSampleCount: Int, eventCount: Int, decodedSampleCount: Int): Boolean =
        rawSampleCount > 0 || eventCount > 0 || decodedSampleCount > 0
}
