package com.jaysato.xiaomihealthbridge.engine

import kotlin.math.sqrt
import kotlin.math.abs

data class TimedPpgObservation(val timestampNanos: Long, val value: Double)

data class TimedAccelerationObservation(
    val timestampNanos: Long,
    val xMetersPerSecondSquared: Double,
    val yMetersPerSecondSquared: Double,
    val zMetersPerSecondSquared: Double
)

data class PpgMotionEvidence(
    val available: Boolean,
    val reasonCode: String,
    val pairedObservationCount: Int,
    val matchedCoverage: Double?,
    val overlapDurationNanos: Long?,
    val ppgWindowDurationNanos: Long?,
    val overlapOfPpgWindow: Double?,
    val dynamicAccelerationRmsMetersPerSecondSquared: Double?,
    val motionActivityIndex: Double?,
    val ppgMotionChangeCorrelation: Double?,
    val ppgMotionChangeCorrelationPairCount: Int,
    val algorithmVersion: String
)

object PpgMotionEvidenceEngine {
    const val VERSION = "ppg-motion-evidence-v3"
    private const val GRAVITY_METERS_PER_SECOND_SQUARED = 9.80665
    private const val MINIMUM_PAIRED_OBSERVATIONS = 8
    private const val MINIMUM_MATCHED_COVERAGE = 0.9

    fun evaluate(
        ppg: List<TimedPpgObservation>,
        acceleration: List<TimedAccelerationObservation>,
        sameSession: Boolean,
        sameMonotonicClock: Boolean,
        maximumPairSkewNanos: Long
    ): PpgMotionEvidence {
        val unavailableReason = when {
            !sameSession -> "SESSION_MISMATCH"
            !sameMonotonicClock -> "COMMON_MONOTONIC_CLOCK_UNCONFIRMED"
            maximumPairSkewNanos !in 1L..100_000_000L -> "PAIR_SKEW_LIMIT_INVALID"
            ppg.size < MINIMUM_PAIRED_OBSERVATIONS || acceleration.size < MINIMUM_PAIRED_OBSERVATIONS -> "INSUFFICIENT_OBSERVATIONS"
            ppg.any { it.timestampNanos <= 0L || !it.value.isFinite() } -> "PPG_TIME_OR_VALUE_INVALID"
            acceleration.any {
                it.timestampNanos <= 0L || !it.xMetersPerSecondSquared.isFinite() ||
                    !it.yMetersPerSecondSquared.isFinite() || !it.zMetersPerSecondSquared.isFinite()
            } -> "ACCELERATION_TIME_OR_VALUE_INVALID"
            ppg.zipWithNext().any { (a, b) -> b.timestampNanos <= a.timestampNanos } -> "PPG_TIME_NOT_MONOTONIC"
            acceleration.zipWithNext().any { (a, b) -> b.timestampNanos <= a.timestampNanos } -> "ACCELERATION_TIME_NOT_MONOTONIC"
            else -> null
        }
        if (unavailableReason != null) return unavailable(unavailableReason)

        val overlapStart = maxOf(ppg.first().timestampNanos, acceleration.first().timestampNanos)
        val overlapEnd = minOf(ppg.last().timestampNanos, acceleration.last().timestampNanos)
        if (overlapStart >= overlapEnd) return unavailable("NO_TIME_OVERLAP")
        val overlappingPpg = ppg.filter { it.timestampNanos in overlapStart..overlapEnd }
        val overlappingAcceleration = acceleration.filter { it.timestampNanos in overlapStart..overlapEnd }
        if (overlappingPpg.size < MINIMUM_PAIRED_OBSERVATIONS ||
            overlappingAcceleration.size < MINIMUM_PAIRED_OBSERVATIONS
        ) return unavailable("INSUFFICIENT_OVERLAPPING_OBSERVATIONS")

        val matchedPairs = mutableListOf<Pair<TimedPpgObservation, TimedAccelerationObservation>>()
        if (overlappingAcceleration.size <= overlappingPpg.size) {
            var ppgIndex = 0
            var lastMatchedPpgIndex = -1
            overlappingAcceleration.forEach { accelerationPoint ->
                while (ppgIndex + 1 < overlappingPpg.size &&
                    distance(overlappingPpg[ppgIndex + 1].timestampNanos, accelerationPoint.timestampNanos) <=
                    distance(overlappingPpg[ppgIndex].timestampNanos, accelerationPoint.timestampNanos)
                ) ppgIndex++
                if (ppgIndex > lastMatchedPpgIndex &&
                    distance(overlappingPpg[ppgIndex].timestampNanos, accelerationPoint.timestampNanos) <= maximumPairSkewNanos
                ) {
                    matchedPairs += overlappingPpg[ppgIndex] to accelerationPoint
                    lastMatchedPpgIndex = ppgIndex
                }
            }
        } else {
            var accelerationIndex = 0
            var lastMatchedAccelerationIndex = -1
            overlappingPpg.forEach { ppgPoint ->
                while (accelerationIndex + 1 < overlappingAcceleration.size &&
                    distance(overlappingAcceleration[accelerationIndex + 1].timestampNanos, ppgPoint.timestampNanos) <=
                    distance(overlappingAcceleration[accelerationIndex].timestampNanos, ppgPoint.timestampNanos)
                ) accelerationIndex++
                if (accelerationIndex > lastMatchedAccelerationIndex &&
                    distance(overlappingAcceleration[accelerationIndex].timestampNanos, ppgPoint.timestampNanos) <= maximumPairSkewNanos
                ) {
                    matchedPairs += ppgPoint to overlappingAcceleration[accelerationIndex]
                    lastMatchedAccelerationIndex = accelerationIndex
                }
            }
        }
        val coverage = matchedPairs.size.toDouble() / minOf(overlappingPpg.size, overlappingAcceleration.size)
        val ppgWindowDuration = ppg.last().timestampNanos - ppg.first().timestampNanos
        val overlapDuration = overlapEnd - overlapStart
        val overlapOfPpgWindow = overlapDuration.toDouble() / ppgWindowDuration
        if (matchedPairs.size < MINIMUM_PAIRED_OBSERVATIONS || coverage < MINIMUM_MATCHED_COVERAGE) {
            return unavailable("INSUFFICIENT_TIME_ALIGNED_COVERAGE", matchedPairs.size, coverage)
        }

        val meanX = matchedPairs.map { it.second.xMetersPerSecondSquared }.average()
        val meanY = matchedPairs.map { it.second.yMetersPerSecondSquared }.average()
        val meanZ = matchedPairs.map { it.second.zMetersPerSecondSquared }.average()
        val dynamicMagnitudes = matchedPairs.map { pair ->
            val point = pair.second
            val x = point.xMetersPerSecondSquared - meanX
            val y = point.yMetersPerSecondSquared - meanY
            val z = point.zMetersPerSecondSquared - meanZ
            sqrt(x * x + y * y + z * z)
        }
        val rms = sqrt(dynamicMagnitudes.sumOf { it * it } / matchedPairs.size)
        val index = rms / (rms + GRAVITY_METERS_PER_SECOND_SQUARED)
        val ppgChanges = mutableListOf<Double>()
        val motionLevels = mutableListOf<Double>()
        val maxIntervalDifference = maximumPairSkewNanos
        for (pairIndex in 0 until matchedPairs.lastIndex) {
            val before = matchedPairs[pairIndex]
            val after = matchedPairs[pairIndex + 1]
            val ppgInterval = after.first.timestampNanos - before.first.timestampNanos
            val motionInterval = after.second.timestampNanos - before.second.timestampNanos
            val intervalDifference = if (ppgInterval >= motionInterval) {
                ppgInterval - motionInterval
            } else motionInterval - ppgInterval
            if (intervalsAligned(ppgInterval, motionInterval, maxIntervalDifference)) {
                ppgChanges += abs(after.first.value - before.first.value) / ppgInterval.toDouble()
                val averageMotion = (dynamicMagnitudes[pairIndex] + dynamicMagnitudes[pairIndex + 1]) / 2.0
                motionLevels += averageMotion / motionInterval.toDouble()
            }
        }
        val motionCorrelation = correlation(ppgChanges, motionLevels)
        return PpgMotionEvidence(
            available = true,
            reasonCode = "ALIGNED_MOTION_EVIDENCE_ONLY",
            pairedObservationCount = matchedPairs.size,
            matchedCoverage = coverage,
            overlapDurationNanos = overlapDuration,
            ppgWindowDurationNanos = ppgWindowDuration,
            overlapOfPpgWindow = overlapOfPpgWindow,
            dynamicAccelerationRmsMetersPerSecondSquared = rms,
            motionActivityIndex = index.coerceIn(0.0, 1.0),
            ppgMotionChangeCorrelation = motionCorrelation,
            ppgMotionChangeCorrelationPairCount = ppgChanges.size,
            algorithmVersion = VERSION
        )
    }

    private fun distance(first: Long, second: Long): Long =
        if (first >= second) first - second else second - first

    internal fun intervalsAligned(ppgIntervalNanos: Long, motionIntervalNanos: Long, maximumDifferenceNanos: Long): Boolean {
        if (ppgIntervalNanos <= 0L || motionIntervalNanos <= 0L || maximumDifferenceNanos < 0L) return false
        val difference = if (ppgIntervalNanos >= motionIntervalNanos) {
            ppgIntervalNanos - motionIntervalNanos
        } else motionIntervalNanos - ppgIntervalNanos
        return difference <= maximumDifferenceNanos
    }

    private fun correlation(first: List<Double>, second: List<Double>): Double? {
        if (first.size < 3 || first.size != second.size || first.any { !it.isFinite() } || second.any { !it.isFinite() }) return null
        val firstMean = first.average()
        val secondMean = second.average()
        val covariance = first.indices.sumOf { (first[it] - firstMean) * (second[it] - secondMean) }
        val firstSquares = first.sumOf { (it - firstMean) * (it - firstMean) }
        val secondSquares = second.sumOf { (it - secondMean) * (it - secondMean) }
        val denominator = sqrt(firstSquares * secondSquares)
        return if (denominator > 0.0 && denominator.isFinite()) (covariance / denominator).coerceIn(-1.0, 1.0) else null
    }

    private fun unavailable(reason: String, count: Int = 0, coverage: Double? = null) = PpgMotionEvidence(
        available = false,
        reasonCode = reason,
        pairedObservationCount = count,
        matchedCoverage = coverage,
        overlapDurationNanos = null,
        ppgWindowDurationNanos = null,
        overlapOfPpgWindow = null,
        dynamicAccelerationRmsMetersPerSecondSquared = null,
        motionActivityIndex = null,
        ppgMotionChangeCorrelation = null,
        ppgMotionChangeCorrelationPairCount = 0,
        algorithmVersion = VERSION
    )
}
