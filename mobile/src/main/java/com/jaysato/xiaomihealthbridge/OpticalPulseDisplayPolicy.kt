package com.jaysato.xiaomihealthbridge

internal object OpticalPulseDisplayPolicy {
    private val opticalKinds = setOf("ppg", "max_hr", "lifeq_ott")
    private val acceptedStatuses = setOf("has_data", "valid_signal", "research_optical")
    const val FRESHNESS_MILLIS = 30 * 60 * 1000L

    fun researchEstimateBpm(
        kind: String,
        estimateBpm: Float?,
        qualityScore: Int,
        status: String?,
        measuredAtMillis: Long,
        nowMillis: Long,
        sampleRateHz: Float = 0f,
        channelMappingVerified: Boolean = false,
        timestampSource: String? = null,
        wearQualified: Boolean? = null
    ): Float? {
        if (kind !in opticalKinds || status !in acceptedStatuses) return null
        if (wearQualified != true) return null
        if (!channelMappingVerified || !sampleRateHz.isFinite() || sampleRateHz < MIN_VALID_SAMPLE_RATE_HZ) return null
        val minimumQuality = if (kind == "ppg") 50 else 70
        if (qualityScore < minimumQuality) return null
        if (!HealthAnalysisFreshnessPolicy.isFreshSensor(measuredAtMillis, timestampSource, nowMillis, FRESHNESS_MILLIS)) return null
        return estimateBpm?.takeIf { it.isFinite() && it in 30f..220f }
    }

    fun isOpticalKind(kind: String): Boolean = kind in opticalKinds

    private const val MIN_VALID_SAMPLE_RATE_HZ = 25f
}
