package com.jaysato.xiaomihealthbridge.engine

data class PpgQualityReport(
    val finiteRatio: Double,
    val missingRatio: Double,
    val amplitudeRange: Double?,
    val flatlineRatio: Double?,
    val clippingRatio: Double?,
    val motionArtifactScore: Double?,
    val qualityScore: Double,
    val combinedQualityScore: Double?,
    val reasonCodes: Set<String>
)

object PpgCompositionQualityEngine {
    const val GAP_SENTINEL = 524287.0

    fun assess(raw: List<Double?>, motionArtifactScore: Double? = null, adcRange: ClosedFloatingPointRange<Double>? = null): PpgQualityReport {
        require(raw.size <= 100_000)
        require(motionArtifactScore == null || motionArtifactScore.isFinite() && motionArtifactScore in 0.0..1.0)
        require(adcRange == null || adcRange.start.isFinite() && adcRange.endInclusive.isFinite() && adcRange.start < adcRange.endInclusive)
        val values = raw.filterNotNull().filter { it.isFinite() && it != GAP_SENTINEL }
        val finiteRatio = if (raw.isEmpty()) 0.0 else values.size.toDouble() / raw.size
        val reasons = mutableSetOf<String>()
        if (values.isEmpty()) reasons.add("NO_FINITE_OBSERVATIONS")
        if (finiteRatio < 1.0) reasons.add("EXPLICIT_MISSING_VALUES")
        if (motionArtifactScore == null) reasons.add("MOTION_UNKNOWN")
        if (adcRange == null) reasons.add("ADC_RANGE_UNKNOWN")
        val amplitude = if (values.isEmpty()) null else values.max() - values.min()
        val adjacent = raw.zipWithNext().filter { (before, after) ->
            before?.isFinite() == true && before != GAP_SENTINEL &&
                after?.isFinite() == true && after != GAP_SENTINEL
        }
        val flatline = if (adjacent.isEmpty()) null else adjacent.count { (before, after) -> before == after }.toDouble() / adjacent.size
        if (flatline != null && flatline >= 0.9) reasons.add("FLATLINE")
        val clipping = if (adcRange == null || values.isEmpty()) null else values.count { it <= adcRange.start || it >= adcRange.endInclusive }.toDouble() / values.size
        if (clipping != null && clipping > 0) reasons.add("AT_OR_BEYOND_ADC_BOUNDARY")
        if (amplitude != null && !amplitude.isFinite()) reasons.add("NUMERIC_RANGE_OVERFLOW")
        val waveformQuality = if (values.size < 3 || amplitude?.isFinite() != true || amplitude <= 0) 0.0 else
            100.0 * finiteRatio * (1.0 - (flatline ?: 0.0))
        val combinedQuality = if (motionArtifactScore != null && clipping != null) {
            waveformQuality * (1.0 - clipping) * (1.0 - motionArtifactScore)
        } else null
        return PpgQualityReport(
            finiteRatio,
            1.0 - finiteRatio,
            amplitude?.takeIf { it.isFinite() },
            flatline,
            clipping,
            motionArtifactScore,
            waveformQuality.coerceIn(0.0, 100.0),
            combinedQuality?.coerceIn(0.0, 100.0),
            reasons
        )
    }
}

enum class SampleRateTrust { VERIFIED, ESTIMATED, UNKNOWN }
enum class ReconstructionMethod { LINEAR, KALMAN, LEARNED, STATISTICAL_BASELINE, UNAVAILABLE }

data class ReconstructionContext(
    val gapSamples: Int,
    val gapDurationSeconds: Double?,
    val neighboringMeasuredSamples: Int,
    val quality: PpgQualityReport,
    val sampleRateTrust: SampleRateTrust,
    val timestampSource: TimestampSource,
    val channelMappingVerified: Boolean,
    val orderingVerified: Boolean,
    val kalmanAvailable: Boolean = false,
    val learnedModelVersion: String? = null,
    val learnedModelConfidence: Double? = null,
    val learnedModelValidatedForDomain: Boolean = false
)

data class ReconstructionDecision(
    val selectedMethod: ReconstructionMethod,
    val expectedConfidence: Double,
    val rejectionReason: String?,
    val fallbackMethod: ReconstructionMethod,
    val numericalDomain: String
)

object ReconstructionPolicyEngine {
    fun select(context: ReconstructionContext): ReconstructionDecision {
        require(context.gapSamples > 0 && context.neighboringMeasuredSamples >= 0)
        require(context.gapDurationSeconds == null || context.gapDurationSeconds.isFinite() && context.gapDurationSeconds > 0)
        require(context.learnedModelConfidence == null || context.learnedModelConfidence.isFinite() && context.learnedModelConfidence in 0.0..1.0)
        val domain = if (context.sampleRateTrust == SampleRateTrust.UNKNOWN || context.timestampSource == TimestampSource.UNKNOWN) "NORMALIZED_INDEX" else "TIME_DOMAIN"
        fun decision(method: ReconstructionMethod, confidence: Double, reason: String? = null) =
            ReconstructionDecision(method, confidence, reason, ReconstructionMethod.STATISTICAL_BASELINE, domain)
        if (!context.orderingVerified || context.neighboringMeasuredSamples == 0) {
            return ReconstructionDecision(ReconstructionMethod.UNAVAILABLE, 0.0, "UNVERIFIED_DOMAIN_OR_NO_CONTEXT", ReconstructionMethod.UNAVAILABLE, domain)
        }
        val acceptable = context.quality.qualityScore >= 60 &&
            context.quality.motionArtifactScore != null && context.quality.motionArtifactScore <= 0.2 &&
            context.quality.clippingRatio == 0.0
        if (!acceptable) return decision(ReconstructionMethod.STATISTICAL_BASELINE, 0.05, "QUALITY_OR_MOTION_OR_ADC_EVIDENCE_INSUFFICIENT")
        if (context.gapSamples <= 2 && context.neighboringMeasuredSamples >= 2) {
            return decision(
                ReconstructionMethod.LINEAR,
                if (!context.channelMappingVerified) 0.15 else if (domain == "NORMALIZED_INDEX") 0.25 else 0.5,
                if (!context.channelMappingVerified) "CHANNEL_MAPPING_UNVERIFIED_INDEX_DOMAIN" else null
            )
        }
        if (domain == "TIME_DOMAIN" && context.sampleRateTrust == SampleRateTrust.VERIFIED &&
            context.gapDurationSeconds != null && context.gapDurationSeconds <= 0.5 &&
            context.neighboringMeasuredSamples >= 4 && context.kalmanAvailable) {
            return decision(ReconstructionMethod.KALMAN, 0.3)
        }
        if (context.learnedModelValidatedForDomain && !context.learnedModelVersion.isNullOrBlank() && context.learnedModelConfidence != null &&
            context.learnedModelConfidence > 0 && context.neighboringMeasuredSamples >= 4) {
            return decision(ReconstructionMethod.LEARNED, minOf(0.2, context.learnedModelConfidence))
        }
        return decision(ReconstructionMethod.STATISTICAL_BASELINE, 0.05, "VALIDATED_RECONSTRUCTOR_UNAVAILABLE")
    }
}
