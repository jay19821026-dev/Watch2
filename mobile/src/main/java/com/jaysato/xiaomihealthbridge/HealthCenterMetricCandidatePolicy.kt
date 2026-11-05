package com.jaysato.xiaomihealthbridge

internal object HealthCenterMetricCandidatePolicy {
    fun ppgResearchCandidate(
        rawEstimateBpm: String?,
        qualityScore: Int,
        status: String,
        measuredAtMillis: Long,
        nowMillis: Long,
        sampleRateHz: Float,
        channelMappingVerified: Boolean,
        timestampSource: String?,
        wearQualified: Boolean
    ): ResearchMetricCandidate? {
        val estimate = rawEstimateBpm?.toFloatOrNull() ?: return null
        val eligibleEstimate = OpticalPulseDisplayPolicy.researchEstimateBpm(
            kind = "ppg",
            estimateBpm = estimate,
            qualityScore = qualityScore,
            status = status,
            measuredAtMillis = measuredAtMillis,
            nowMillis = nowMillis,
            sampleRateHz = sampleRateHz,
            channelMappingVerified = channelMappingVerified,
            timestampSource = timestampSource,
            wearQualified = wearQualified
        ) ?: return null
        return ResearchMetricCandidate(
            value = eligibleEstimate.toDouble(),
            source = "PPG_RESEARCH",
            measuredAtMillis = measuredAtMillis,
            timestampSource = timestampSource,
            qualityScore = qualityScore,
            isResearch = true
        )
    }
}
