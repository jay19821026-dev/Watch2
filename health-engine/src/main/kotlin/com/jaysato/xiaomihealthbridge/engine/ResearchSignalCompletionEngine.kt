package com.jaysato.xiaomihealthbridge.engine

data class ResearchSignalCompletionReport(
    val signal: FullCoverageSignal?,
    val quality: PpgQualityReport?,
    val status: String,
    val method: String,
    val reason: String
)

object ResearchSignalCompletionEngine {
    const val algorithmVersion = "research-signal-completion-v1"

    fun completePpg(
        rawValues: List<Double?>,
        provenance: SensorProvenance,
        fullCoverageEnabled: Boolean,
        orderingVerified: Boolean,
        channelMappingVerified: Boolean,
        motionArtifactScore: Double? = null,
        adcRange: ClosedFloatingPointRange<Double>? = null
    ): ResearchSignalCompletionReport {
        val quality = PpgCompositionQualityEngine.assess(rawValues, motionArtifactScore, adcRange)
        val result = FullCoverageSignalComposer.composeIndexDomain(
            rawValues = rawValues,
            provenance = provenance.copy(
                algorithmVersion = algorithmVersion,
                provenance = "${provenance.provenance}; research completion policy",
                validationStatus = "RESEARCH_ONLY_NOT_CLINICAL"
            ),
            fullCoverageEnabled = fullCoverageEnabled,
            orderingVerified = orderingVerified,
            channelMappingVerified = channelMappingVerified
        )
        return when (result) {
            is FullCoverageResult.Complete -> ResearchSignalCompletionReport(
                signal = result.signal,
                quality = quality,
                status = "RESEARCH_COMPLETE_INDEX_DOMAIN",
                method = algorithmVersion,
                reason = quality.reasonCodes.joinToString(",").ifBlank { "OBSERVATIONS_AVAILABLE" }
            )
            is FullCoverageResult.Unavailable -> ResearchSignalCompletionReport(
                signal = null,
                quality = quality,
                status = "RESEARCH_INCOMPLETE",
                method = algorithmVersion,
                reason = result.reason
            )
        }
    }
}
