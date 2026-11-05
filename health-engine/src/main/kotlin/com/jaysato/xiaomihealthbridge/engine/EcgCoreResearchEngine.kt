package com.jaysato.xiaomihealthbridge.engine

import kotlin.math.abs
import kotlin.math.sqrt

enum class EcgTimeline { SENSOR, ESTIMATED, NORMALIZED_INDEX }

data class EcgCalibrationPairingEvidence(
    val maximumPairSkewNanos: Long,
    val trainingRawTimestampsNanos: List<Long>,
    val trainingReferenceTimestampsNanos: List<Long>,
    val holdoutRawTimestampsNanos: List<Long>,
    val holdoutReferenceTimestampsNanos: List<Long>
) {
    internal fun isValid(trainingPoints: Int, holdoutPoints: Int): Boolean {
        if (maximumPairSkewNanos !in 1L..1_000_000_000L) return false
        if (!validPair(trainingRawTimestampsNanos, trainingReferenceTimestampsNanos, trainingPoints) ||
            !validPair(holdoutRawTimestampsNanos, holdoutReferenceTimestampsNanos, holdoutPoints)) return false
        return disjoint(trainingRawTimestampsNanos, holdoutRawTimestampsNanos) &&
            disjoint(trainingReferenceTimestampsNanos, holdoutReferenceTimestampsNanos)
    }

    private fun validPair(raw: List<Long>, reference: List<Long>, expected: Int): Boolean {
        if (raw.size != expected || reference.size != expected || raw.size < 2 ||
            raw.any { it <= 0L } || reference.any { it <= 0L } ||
            raw.zipWithNext().any { (a, b) -> b <= a } ||
            reference.zipWithNext().any { (a, b) -> b <= a }) return false
        val sampleAlignedLimit = minOf(maximumPairSkewNanos,
            minOf(medianInterval(raw), medianInterval(reference)) / 2L)
        return raw.indices.all { index ->
            val a = raw[index]
            val b = reference[index]
            (if (a >= b) a - b else b - a) <= sampleAlignedLimit
        }
    }

    private fun medianInterval(timestamps: List<Long>): Long {
        val intervals = timestamps.zipWithNext { before, after -> after - before }.sorted()
        val middle = intervals.size / 2
        return if (intervals.size % 2 == 1) intervals[middle]
        else intervals[middle - 1] + (intervals[middle] - intervals[middle - 1]) / 2L
    }

    private fun disjoint(first: List<Long>, second: List<Long>): Boolean {
        val firstStart = first.minOrNull() ?: return false
        val firstEnd = first.maxOrNull() ?: return false
        val secondStart = second.minOrNull() ?: return false
        val secondEnd = second.maxOrNull() ?: return false
        return firstEnd < secondStart || secondEnd < firstStart
    }
}

data class EcgCoreInput(
    val rawValues: List<Double>,
    val clock: ClockEvidence,
    val waveformMappingVerified: Boolean,
    val qualityScore: Int,
    val wearQualified: Boolean,
    val waveformEncoding: String? = null,
    val sensorProfileFingerprint: String? = null
)

class EcgCalibrationProfile private constructor(
    val slopeMvPerRaw: Double,
    val offsetMv: Double,
    val profileId: String,
    val trainingDatasetId: String,
    val holdoutDatasetId: String,
    val inputEncoding: String,
    val trainingPoints: Int,
    val holdoutPoints: Int,
    val holdoutRmseMv: Double,
    val maximumAllowedRmseMv: Double,
    val trainingRawMinimum: Double,
    val trainingRawMaximum: Double,
    val sensorProfileFingerprint: String,
    val pairingEvidence: EcgCalibrationPairingEvidence
) {
    companion object {
        internal fun fitFromPairedReference(
            trainRaw: List<Double>,
            trainReferenceMv: List<Double>,
            holdoutRaw: List<Double>,
            holdoutReferenceMv: List<Double>,
            profileId: String,
            trainingDatasetId: String,
            holdoutDatasetId: String,
            inputEncoding: String,
            maxHoldoutRmseMv: Double,
            pairingEvidence: EcgCalibrationPairingEvidence,
            sensorProfileFingerprint: String?
        ): EcgCalibrationProfile? {
            val holdoutReferenceRange = holdoutReferenceMv.maxOrNull()
                ?.minus(holdoutReferenceMv.minOrNull() ?: return null) ?: return null
            if (!maxHoldoutRmseMv.isFinite() || maxHoldoutRmseMv <= 0.0 ||
                maxHoldoutRmseMv > EcgCoreResearchEngine.MAX_ABSOLUTE_HOLDOUT_RMSE_MV ||
                holdoutReferenceRange <= 0.0 ||
                maxHoldoutRmseMv > holdoutReferenceRange *
                EcgCoreResearchEngine.MAX_HOLDOUT_RMSE_FRACTION_OF_REFERENCE_RANGE) return null
            if (profileId.isBlank() || trainingDatasetId.isBlank() || holdoutDatasetId.isBlank() ||
                trainingDatasetId == holdoutDatasetId || inputEncoding != EcgCoreResearchEngine.ECG_WAVEFORM_ENCODING ||
                sensorProfileFingerprint?.matches(Regex("[0-9a-f]{64}")) != true ||
                trainRaw.size != trainReferenceMv.size || holdoutRaw.size != holdoutReferenceMv.size ||
                trainRaw.size < EcgCoreResearchEngine.MIN_CALIBRATION_POINTS ||
                holdoutRaw.size < EcgCoreResearchEngine.MIN_CALIBRATION_POINTS ||
                (trainRaw + trainReferenceMv + holdoutRaw + holdoutReferenceMv).any { !it.isFinite() } ||
                hasRepeatedPairedWindow(trainRaw, trainReferenceMv, holdoutRaw, holdoutReferenceMv) ||
                holdoutRaw.any {
                    it !in (trainRaw.minOrNull() ?: return null)..(trainRaw.maxOrNull() ?: return null)
                } || !pairingEvidence.isValid(trainRaw.size, holdoutRaw.size)) return null
            if (trainReferenceMv.max() - trainReferenceMv.min() <= 1e-9) return null
            val xMean = trainRaw.average()
            val yMean = trainReferenceMv.average()
            val denominator = trainRaw.sumOf { (it - xMean) * (it - xMean) }
            if (denominator <= 1e-12) return null
            val slope = trainRaw.indices.sumOf {
                (trainRaw[it] - xMean) * (trainReferenceMv[it] - yMean)
            } / denominator
            val offset = yMean - slope * xMean
            val rmse = sqrt(holdoutRaw.indices.map { index ->
                val error = slope * holdoutRaw[index] + offset - holdoutReferenceMv[index]
                error * error
            }.average())
            if (!slope.isFinite() || slope == 0.0 || !offset.isFinite() ||
                !rmse.isFinite() || rmse > maxHoldoutRmseMv) return null
            val profile = EcgCalibrationProfile(
                slope, offset, profileId, trainingDatasetId, holdoutDatasetId, inputEncoding,
                trainRaw.size, holdoutRaw.size, rmse, maxHoldoutRmseMv,
                trainRaw.minOrNull() ?: return null, trainRaw.maxOrNull() ?: return null,
                sensorProfileFingerprint, pairingEvidence
            )
            return profile.takeIf { EcgCoreResearchEngine.validateCalibration(it).allowed }
        }

        private fun hasRepeatedPairedWindow(
            trainingRaw: List<Double>,
            trainingReferenceMv: List<Double>,
            holdoutRaw: List<Double>,
            holdoutReferenceMv: List<Double>
        ): Boolean {
            val windowSize = EcgCoreResearchEngine.MIN_CALIBRATION_POINTS
            fun windows(raw: List<Double>, reference: List<Double>): Set<List<Pair<Long, Long>>> =
                (0..raw.size - windowSize).mapTo(mutableSetOf()) { start ->
                    List(windowSize) { offset ->
                        (raw[start + offset].toBits() to reference[start + offset].toBits())
                    }
                }
            val trainingWindows = windows(trainingRaw, trainingReferenceMv)
            val holdoutWindows = windows(holdoutRaw, holdoutReferenceMv)
            val reversedHoldoutWindows = windows(holdoutRaw.reversed(), holdoutReferenceMv.reversed())
            return trainingWindows.intersect(holdoutWindows).isNotEmpty() ||
                trainingWindows.intersect(reversedHoldoutWindows).isNotEmpty()
        }
    }
}

data class EcgCoreOutput(
    val relativeWaveform: List<Double>,
    val timeline: EcgTimeline,
    val rPeakIndices: List<Int>,
    val rPeakIndexDistancesSamples: List<Int>,
    val rrIntervalsMs: List<Double>,
    val heartRateBpm: Double?,
    val researchMv: List<Double>?,
    val qualityScore: Int,
    val confidence: Double,
    val status: String,
    val reasonCodes: Set<String>
)

data class EcgCalibrationDecision(val allowed: Boolean, val reason: String)

/** Central research-only ECG policy. It never invents an ADC clock or clinical calibration. */
object EcgCoreResearchEngine {
    const val VERSION = "ecg-core-research-v1"
    const val MIN_RR_INTERVALS_FOR_RESEARCH_HR = 2
    const val HAL_SAMPLE_RATE_SOURCE = "sensor_hal_type_sampling"

    fun validateCalibration(profile: EcgCalibrationProfile?): EcgCalibrationDecision {
        if (profile == null) return EcgCalibrationDecision(false, "NO_CALIBRATION_PROFILE")
        if (profile.profileId.isBlank()) return EcgCalibrationDecision(false, "PROFILE_ID_MISSING")
        if (profile.trainingDatasetId.isBlank() || profile.holdoutDatasetId.isBlank() ||
            profile.trainingDatasetId == profile.holdoutDatasetId) {
            return EcgCalibrationDecision(false, "INDEPENDENT_HOLDOUT_DATASET_REQUIRED")
        }
        if (profile.inputEncoding != ECG_WAVEFORM_ENCODING) {
            return EcgCalibrationDecision(false, "WAVEFORM_ENCODING_MISMATCH")
        }
        if (!profile.sensorProfileFingerprint.matches(Regex("[0-9a-f]{64}"))) {
            return EcgCalibrationDecision(false, "SENSOR_PROFILE_FINGERPRINT_MISSING")
        }
        if (!profile.slopeMvPerRaw.isFinite() || profile.slopeMvPerRaw == 0.0 || !profile.offsetMv.isFinite()) {
            return EcgCalibrationDecision(false, "INVALID_RAW_TO_MV_COEFFICIENT")
        }
        if (profile.trainingPoints < MIN_CALIBRATION_POINTS || profile.holdoutPoints < MIN_CALIBRATION_POINTS) {
            return EcgCalibrationDecision(false, "INSUFFICIENT_CALIBRATION_POINTS")
        }
        if (!profile.holdoutRmseMv.isFinite() || profile.holdoutRmseMv < 0.0 ||
            !profile.maximumAllowedRmseMv.isFinite() || profile.maximumAllowedRmseMv <= 0.0 ||
            profile.maximumAllowedRmseMv > MAX_ABSOLUTE_HOLDOUT_RMSE_MV ||
            profile.holdoutRmseMv > profile.maximumAllowedRmseMv) {
            return EcgCalibrationDecision(false, "HOLDOUT_ERROR_GATE_FAILED")
        }
        if (!profile.trainingRawMinimum.isFinite() || !profile.trainingRawMaximum.isFinite() ||
            profile.trainingRawMaximum <= profile.trainingRawMinimum) {
            return EcgCalibrationDecision(false, "INVALID_TRAINING_RAW_DOMAIN")
        }
        if (!profile.pairingEvidence.isValid(profile.trainingPoints, profile.holdoutPoints)) {
            return EcgCalibrationDecision(false, "PAIRED_TIMESTAMP_EVIDENCE_INVALID")
        }
        return EcgCalibrationDecision(true, "RESEARCH_HOLDOUT_GATE_PASSED")
    }

    const val ECG_WAVEFORM_ENCODING = "verified_ecg_samples_v1"
    const val MIN_CALIBRATION_POINTS = 5
    const val MAX_HOLDOUT_RMSE_FRACTION_OF_REFERENCE_RANGE = 0.05
    const val MAX_ABSOLUTE_HOLDOUT_RMSE_MV = 0.05

    /** Fits an experimental raw-to-mV mapping; passing this numeric holdout gate is not clinical validation. */
    fun fitResearchCalibration(
        trainRaw: List<Double>,
        trainReferenceMv: List<Double>,
        holdoutRaw: List<Double>,
        holdoutReferenceMv: List<Double>,
        profileId: String,
        trainingDatasetId: String,
        holdoutDatasetId: String,
        inputEncoding: String,
        maxHoldoutRmseMv: Double,
        pairingEvidence: EcgCalibrationPairingEvidence,
        sensorProfileFingerprint: String? = null,
    ): EcgCalibrationProfile? = EcgCalibrationProfile.fitFromPairedReference(
        trainRaw, trainReferenceMv, holdoutRaw, holdoutReferenceMv, profileId, trainingDatasetId,
        holdoutDatasetId, inputEncoding, maxHoldoutRmseMv, pairingEvidence, sensorProfileFingerprint
    )

    @Deprecated(
        message = "This only fits a research profile; use fitResearchCalibration and do not treat its gate as clinical validation",
        replaceWith = ReplaceWith("fitResearchCalibration(trainRaw, trainReferenceMv, holdoutRaw, holdoutReferenceMv, profileId, trainingDatasetId, holdoutDatasetId, inputEncoding, maxHoldoutRmseMv, pairingEvidence, sensorProfileFingerprint)")
    )
    fun fitValidatedCalibration(
        trainRaw: List<Double>,
        trainReferenceMv: List<Double>,
        holdoutRaw: List<Double>,
        holdoutReferenceMv: List<Double>,
        profileId: String,
        trainingDatasetId: String,
        holdoutDatasetId: String,
        inputEncoding: String,
        maxHoldoutRmseMv: Double,
        pairingEvidence: EcgCalibrationPairingEvidence,
        sensorProfileFingerprint: String? = null,
    ): EcgCalibrationProfile? = fitResearchCalibration(
        trainRaw, trainReferenceMv, holdoutRaw, holdoutReferenceMv, profileId, trainingDatasetId,
        holdoutDatasetId, inputEncoding, maxHoldoutRmseMv, pairingEvidence, sensorProfileFingerprint
    )

    fun analyze(input: EcgCoreInput, calibration: EcgCalibrationProfile? = null): EcgCoreOutput {
        val finite = input.rawValues.filter { it.isFinite() }
        val completeSequence = finite.size == input.rawValues.size
        if (finite.isEmpty()) return empty(input.qualityScore, "NO_FINITE_SAMPLES")
        val scale = finite.maxOf { abs(it) }.takeIf { it > 0.0 } ?: 1.0
        val scaledFinite = finite.map { it / scale }
        val mean = scaledFinite.average()
        val span = scaledFinite.max() - scaledFinite.min()
        val normalizedFinite = if (span > 0.0) {
            scaledFinite.map { ((it - mean) / span * 1000.0).coerceIn(-2000.0, 2000.0) }
        } else {
            finite.map { 0.0 }
        }
        val normalized = if (completeSequence) normalizedFinite else {
            val iterator = normalizedFinite.iterator()
            input.rawValues.map { if (it.isFinite()) iterator.next() else Double.NaN }
        }
        val clock = SensorClockClassifier.classify(input.clock)
        val timeline = when {
            !completeSequence -> EcgTimeline.NORMALIZED_INDEX
            clock.timestampSource == TimestampSource.SENSOR -> EcgTimeline.SENSOR
            clock.timestampSource == TimestampSource.ESTIMATED -> EcgTimeline.ESTIMATED
            else -> EcgTimeline.NORMALIZED_INDEX
        }
        if (finite.size < 32 || span <= 0.0) {
            val reasons = buildSet {
                if (finite.size < 32) add("INSUFFICIENT_FINITE_SAMPLES")
                if (span <= 0.0) add("FLATLINE")
                if (!completeSequence) add("NONFINITE_SAMPLE_GAP")
                if (timeline == EcgTimeline.NORMALIZED_INDEX) add("SOURCE_TIMESTAMP_MISSING")
            }
            val evidenceRatio = finite.size.toDouble() / input.rawValues.size.coerceAtLeast(1)
            return EcgCoreOutput(
                relativeWaveform = normalized,
                timeline = timeline,
                rPeakIndices = emptyList(),
                rPeakIndexDistancesSamples = emptyList(),
                rrIntervalsMs = emptyList(),
                heartRateBpm = null,
                researchMv = null,
                qualityScore = input.qualityScore.coerceIn(0, 100),
                confidence = if (span <= 0.0) 0.0 else {
                    evidenceRatio * input.qualityScore.coerceIn(0, 100) / 100.0 *
                        (finite.size / 32.0).coerceAtMost(1.0)
                },
                status = "LIMITED_RESEARCH_OUTPUT",
                reasonCodes = reasons
            )
        }
        val trustedSampleRate = input.clock.nominalSampleRateHz?.takeIf {
            input.clock.sampleRateSource == HAL_SAMPLE_RATE_SOURCE && it.isFinite() && it > 0.0
        }
        val referenceMapped = calibration?.let {
            input.waveformEncoding == it.inputEncoding &&
                input.sensorProfileFingerprint == it.sensorProfileFingerprint &&
                validateCalibration(it).allowed
        } == true
        val peakDetectionAllowed = (input.waveformMappingVerified || referenceMapped) &&
            input.wearQualified && input.qualityScore >= 50
        val peaks = if (peakDetectionAllowed) {
            detectPeaksBySegment(normalized, trustedSampleRate)
        } else {
            emptyList()
        }
        val trustedForRate = input.waveformMappingVerified && input.wearQualified &&
            input.qualityScore >= 50 && clock.timestampSource == TimestampSource.SENSOR &&
            trustedSampleRate != null
        val contiguousPeakPairs = peaks.zipWithNext().filter { (a, b) ->
            (a until b).all { normalized[it].isFinite() }
        }
        val rr = if (trustedForRate) contiguousPeakPairs.map { (a, b) ->
            (b - a) * 1000.0 / input.clock.nominalSampleRateHz!!
        }.filter { it in 300.0..1500.0 } else emptyList()
        val hr = rr.takeIf { it.size >= MIN_RR_INTERVALS_FOR_RESEARCH_HR }
            ?.let { 60_000.0 / it.average() }
        val mappedMv = calibration?.takeIf {
                referenceMapped &&
                completeSequence &&
                finite.all { value -> value in it.trainingRawMinimum..it.trainingRawMaximum }
        }?.let { profile ->
            finite.map { it * profile.slopeMvPerRaw + profile.offsetMv }
                .takeIf { values -> values.all(Double::isFinite) }
        }
        val mv = mappedMv?.takeIf { values -> values.all { abs(it) <= Float.MAX_VALUE.toDouble() } }
        val reasons = buildSet {
            if (!input.waveformMappingVerified && !referenceMapped) add("WAVEFORM_MAPPING_UNVERIFIED")
            if (!completeSequence) add("NONFINITE_SAMPLE_GAP")
            if (!completeSequence && rr.isNotEmpty()) add("RR_FROM_CONTIGUOUS_SEGMENTS_ONLY")
            if (timeline == EcgTimeline.NORMALIZED_INDEX) add("SOURCE_TIMESTAMP_MISSING")
            if (input.clock.sampleRateSource.isNullOrBlank()) add("SAMPLE_RATE_SOURCE_MISSING")
            else if (input.clock.sampleRateSource != HAL_SAMPLE_RATE_SOURCE) add("SAMPLE_RATE_SOURCE_UNSUPPORTED")
            if (!trustedForRate) add("RATE_OR_QUALITY_GATE_NOT_MET")
            if (!input.waveformMappingVerified && !referenceMapped) add("R_PEAK_CANDIDATES_BLOCKED_MAPPING_UNVERIFIED")
            if (!input.wearQualified) add("R_PEAK_CANDIDATES_BLOCKED_WEAR_NOT_QUALIFIED")
            if (input.qualityScore < 50) add("R_PEAK_CANDIDATES_BLOCKED_LOW_QUALITY")
            if (mv == null) add(when {
                input.waveformEncoding != calibration?.inputEncoding -> "WAVEFORM_ENCODING_MISMATCH"
                calibration == null -> "NO_CALIBRATION_PROFILE"
                input.sensorProfileFingerprint != calibration.sensorProfileFingerprint -> "SENSOR_PROFILE_MISMATCH"
                !input.waveformMappingVerified && !referenceMapped -> "WAVEFORM_MAPPING_UNVERIFIED"
                finite.any { it !in calibration.trainingRawMinimum..calibration.trainingRawMaximum } -> "RAW_VALUE_OUTSIDE_TRAINING_DOMAIN"
                mappedMv?.any { abs(it) > Float.MAX_VALUE.toDouble() } == true -> "RESEARCH_MV_FLOAT_RANGE_OVERFLOW"
                else -> validateCalibration(calibration).reason
            })
        }
        val peakDistances = peaks.zipWithNextNotNull { previous, current ->
            (previous + 1 until current).takeIf { indices -> indices.all { normalized[it].isFinite() } }
                ?.let { current - previous }
        }
        return EcgCoreOutput(normalized, timeline, peaks, peakDistances, rr, hr, mv, input.qualityScore.coerceIn(0, 100),
            (input.qualityScore.coerceIn(0, 100) / 100.0), "RESEARCH_OUTPUT", reasons)
    }

    internal fun detectPeaks(values: List<Double>, sampleRateHz: Double? = null): List<Int> {
        if (values.size < 3 || values.any { !it.isFinite() }) return emptyList()
        val magnitudes = values.map(::abs)
        val peakMagnitude = magnitudes.maxOrNull()?.takeIf { it > 0.0 } ?: return emptyList()
        val threshold = peakMagnitude * 0.35
        val widthLimit = sampleRateHz?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { (it * 0.12).toInt().coerceAtLeast(4) }
            ?: minOf(64, maxOf(8, values.size / 20))
        val refractorySamples = sampleRateHz?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { (it * 0.20).toInt().coerceAtLeast(2) }
            ?: 2
        val peaks = mutableListOf<Int>()
        for (index in 1 until magnitudes.lastIndex) {
            val magnitude = magnitudes[index]
            if (magnitude < threshold || magnitude < magnitudes[index - 1] || magnitude < magnitudes[index + 1]) continue
            var left = index
            while (left > 0 && magnitudes[left - 1] >= magnitude * 0.5) left--
            var right = index
            while (right < magnitudes.lastIndex && magnitudes[right + 1] >= magnitude * 0.5) right++
            if (right - left > widthLimit) continue
            if (peaks.isEmpty() || index - peaks.last() >= refractorySamples) peaks += index
            else if (magnitude > magnitudes[peaks.last()]) peaks[peaks.lastIndex] = index
        }
        return peaks
    }

    private fun detectPeaksBySegment(values: List<Double>, sampleRateHz: Double?): List<Int> {
        if (values.all(Double::isFinite)) return detectPeaks(values, sampleRateHz)
        val peaks = mutableListOf<Int>()
        var segmentStart = 0
        while (segmentStart < values.size) {
            while (segmentStart < values.size && !values[segmentStart].isFinite()) segmentStart++
            var segmentEnd = segmentStart
            while (segmentEnd < values.size && values[segmentEnd].isFinite()) segmentEnd++
            if (segmentEnd - segmentStart >= 3) {
                peaks += detectPeaks(values.subList(segmentStart, segmentEnd), sampleRateHz).map { it + segmentStart }
            }
            segmentStart = segmentEnd + 1
        }
        return peaks
    }

    private inline fun <T, R : Any> List<T>.zipWithNextNotNull(transform: (T, T) -> R?): List<R> =
        (0 until lastIndex).mapNotNull { index -> transform(this[index], this[index + 1]) }

    private fun empty(score: Int, reason: String) = EcgCoreOutput(
        emptyList(), EcgTimeline.NORMALIZED_INDEX, emptyList(), emptyList(), emptyList(), null, null,
        score.coerceIn(0, 100), 0.0, "NO_RESEARCH_OUTPUT", setOf(reason)
    )
}
