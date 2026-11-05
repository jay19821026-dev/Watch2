package com.jaysato.xiaomihealthbridge.engine

enum class DataOrigin {
    MEASURED, FILTERED, RECONSTRUCTED, IMPUTED, SYNTHETIC_ESTIMATE,
    MODEL_ESTIMATE, CALIBRATED_MODEL_ESTIMATE, UNKNOWN
}

enum class TimestampSource { SENSOR, ESTIMATED, UNKNOWN }

data class SensorProvenance(
    val sessionId: String,
    val source: String,
    val provenance: String,
    val algorithmVersion: String,
    val modelVersion: String?,
    val confidence: Double,
    val validationStatus: String,
    val timestampSource: TimestampSource,
    val calibrationSource: String?,
    val createdAtEpochMillis: Long
) {
    init {
        require(sessionId.isNotBlank() && source.isNotBlank() && provenance.isNotBlank())
        require(algorithmVersion.isNotBlank() && validationStatus.isNotBlank())
        require(confidence.isFinite() && confidence in 0.0..1.0)
        require(createdAtEpochMillis >= 0)
    }
}

data class ClockEvidence(
    val sourceTimestampsNanos: List<Long>,
    val callbackTimestampsNanos: List<Long>,
    val nominalSampleRateHz: Double?,
    val sampleRateSource: String?,
    val eventOrderingVerified: Boolean,
    val samplesPerEventKnown: Boolean,
    val sessionOrderingVerified: Boolean,
    val sourceClockVerified: Boolean
)

data class ClockClassification(
    val timestampSource: TimestampSource,
    val sourceClockTrustworthy: Boolean,
    val reason: String
)

object SensorClockClassifier {
    private fun strictlyIncreasing(values: List<Long>): Boolean =
        values.size >= 2 && values.all { it > 0 } && values.zipWithNext().all { (before, after) -> after > before }

    fun classify(evidence: ClockEvidence): ClockClassification {
        if (evidence.sourceClockVerified && strictlyIncreasing(evidence.sourceTimestampsNanos)) {
            return ClockClassification(TimestampSource.SENSOR, true, "VERIFIED_SOURCE_CLOCK")
        }
        val rate = evidence.nominalSampleRateHz
        val estimationAllowed = rate != null && rate.isFinite() && rate > 0 &&
            !evidence.sampleRateSource.isNullOrBlank() && evidence.eventOrderingVerified &&
            evidence.samplesPerEventKnown && evidence.sessionOrderingVerified &&
            strictlyIncreasing(evidence.callbackTimestampsNanos)
        return if (estimationAllowed) {
            ClockClassification(TimestampSource.ESTIMATED, false, "ENVELOPE_ESTIMATION_ELIGIBLE_NOT_SENSOR_CLOCK")
        } else {
            ClockClassification(TimestampSource.UNKNOWN, false, "INSUFFICIENT_CLOCK_CONSTRAINTS")
        }
    }
}

object EcgClockEvidencePolicy {
    private fun strictlyIncreasing(values: List<Long>): Boolean =
        values.size >= 2 && values.all { it > 0L } && values.zipWithNext().all { (before, after) -> after > before }

    fun eventOrderingVerified(sourceTimestampsNanos: List<Long>, callbackTimestampsNanos: List<Long>): Boolean =
        strictlyIncreasing(sourceTimestampsNanos) ||
            (sourceTimestampsNanos.isNotEmpty() && sourceTimestampsNanos.size == callbackTimestampsNanos.size &&
                strictlyIncreasing(callbackTimestampsNanos))

    fun samplesPerEventKnown(
        eventSampleCounts: List<Int>,
        eventCount: Int,
        decodedEventCount: Int,
        decodedSampleCount: Int,
        decodedSamplesPerEvent: Int
    ): Boolean {
        if (eventCount <= 0) return false
        val countMetadataKnown = eventSampleCounts.size == eventCount && eventSampleCounts.all { it > 0 }
        val decodedLayoutKnown = decodedEventCount == eventCount && decodedSamplesPerEvent > 0 &&
            decodedSampleCount.toLong() == decodedEventCount.toLong() * decodedSamplesPerEvent
        val decoderEvidencePresent = decodedEventCount > 0 || decodedSampleCount > 0 || decodedSamplesPerEvent > 0
        if (decoderEvidencePresent) {
            if (!decodedLayoutKnown) return false
            if (eventSampleCounts.any { it < 0 }) return false
            return eventSampleCounts.none { it > 0 } ||
                (eventSampleCounts.size == eventCount && eventSampleCounts.all { it == decodedSamplesPerEvent })
        }
        return countMetadataKnown
    }
}

data class ShortGapPolicy(
    val maxGapSamples: Int = 2,
    val maxGapSeconds: Double = 0.1,
    val minimumQualityScore: Double = 80.0,
    val maximumMotionArtifactScore: Double = 0.2
) {
    init {
        require(maxGapSamples in 1..100)
        require(maxGapSeconds.isFinite() && maxGapSeconds > 0)
        require(minimumQualityScore.isFinite() && minimumQualityScore in 0.0..100.0)
        require(maximumMotionArtifactScore.isFinite() && maximumMotionArtifactScore in 0.0..1.0)
    }
}

data class ReconstructedPoint(val index: Int, val value: Double, val provenance: SensorProvenance) {
    val origin: DataOrigin = DataOrigin.RECONSTRUCTED
}

data class ShortGapResult(val points: List<ReconstructedPoint>, val reasonCodes: Set<String>)

object PpgShortGapReconstruction {
    fun reconstruct(
        rawValues: List<Double?>,
        clockEvidence: ClockEvidence,
        qualityScore: Double,
        motionArtifactScore: Double?,
        provenance: SensorProvenance,
        policy: ShortGapPolicy = ShortGapPolicy()
    ): ShortGapResult {
        val clock = SensorClockClassifier.classify(clockEvidence)
        val rate = clockEvidence.nominalSampleRateHz
        val rejection = when {
            rawValues.size > 100_000 -> "INPUT_LIMIT_EXCEEDED"
            rate == null || !rate.isFinite() || rate <= 0 || clockEvidence.sampleRateSource.isNullOrBlank() -> "UNKNOWN_SAMPLE_RATE"
            clock.timestampSource == TimestampSource.UNKNOWN -> "UNKNOWN_TIMELINE"
            !qualityScore.isFinite() || qualityScore !in policy.minimumQualityScore..100.0 -> "LOW_QUALITY"
            motionArtifactScore == null || !motionArtifactScore.isFinite() ||
                motionArtifactScore !in 0.0..policy.maximumMotionArtifactScore -> "MOTION_NOT_ACCEPTABLE_OR_UNKNOWN"
            else -> null
        }
        if (rejection != null) return ShortGapResult(emptyList(), setOf(rejection))
        val points = mutableListOf<ReconstructedPoint>()
        val reasons = mutableSetOf<String>()
        var index = 0
        while (index < rawValues.size) {
            if (rawValues[index]?.isFinite() == true) {
                index++
                continue
            }
            val gapStart = index
            while (index < rawValues.size && rawValues[index]?.isFinite() != true) index++
            val gapSize = index - gapStart
            if (gapStart == 0 || index == rawValues.size) {
                reasons.add("UNBOUNDED_GAP")
                continue
            }
            if (gapSize > policy.maxGapSamples || gapSize / rate!! > policy.maxGapSeconds) {
                reasons.add("LONG_GAP")
                continue
            }
            val before = rawValues[gapStart - 1]!!
            val after = rawValues[index]!!
            val candidate = (0 until gapSize).map { offset ->
                val fraction = (offset + 1).toDouble() / (gapSize + 1)
                before * (1 - fraction) + after * fraction
            }
            if (candidate.any { !it.isFinite() }) {
                reasons.add("NUMERIC_OVERFLOW")
                continue
            }
            val derivedProvenance = provenance.copy(
                algorithmVersion = "ppg-linear-gap-v1",
                modelVersion = null,
                provenance = "${provenance.provenance}; linear interpolation; raw indices ${gapStart - 1} and $index",
                timestampSource = clock.timestampSource,
                validationStatus = "RESEARCH_ONLY_NOT_VALIDATED_FOR_RATE_OR_HRV",
                confidence = minOf(provenance.confidence, 0.5)
            )
            candidate.forEachIndexed { offset, value ->
                points.add(ReconstructedPoint(gapStart + offset, value, derivedProvenance))
            }
        }
        return ShortGapResult(points.toList(), reasons.toSet())
    }
}
