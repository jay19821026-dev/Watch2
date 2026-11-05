package com.jaysato.xiaomihealthbridge.engine

object LinearInterpolator {
    fun interpolate(before: Double, after: Double, fraction: Double): Double {
        require(before.isFinite() && after.isFinite() && fraction.isFinite() && fraction in 0.0..1.0)
        return (before * (1 - fraction) + after * fraction).also { require(it.isFinite()) }
    }
}

object SplineInterpolator {
    fun interpolate(
        before: Double,
        after: Double,
        beforeSlope: Double,
        afterSlope: Double,
        fraction: Double,
        interval: Double
    ): Double {
        require(listOf(before, after, beforeSlope, afterSlope, fraction, interval).all { it.isFinite() })
        require(fraction in 0.0..1.0 && interval > 0)
        val squared = fraction * fraction
        val cubed = squared * fraction
        val value = (2 * cubed - 3 * squared + 1) * before +
            (cubed - 2 * squared + fraction) * interval * beforeSlope +
            (-2 * cubed + 3 * squared) * after +
            (cubed - squared) * interval * afterSlope
        require(value.isFinite())
        return value
    }
}

data class KalmanParameters(
    val processVariance: Double,
    val observationVariance: Double,
    val initialVariance: Double,
    val maxConsecutiveMissing: Int
) {
    init {
        require(processVariance.isFinite() && processVariance > 0)
        require(observationVariance.isFinite() && observationVariance > 0)
        require(initialVariance.isFinite() && initialVariance > 0)
        require(maxConsecutiveMissing in 1..1000)
    }
}

data class KalmanPoint(val index: Int, val value: Double, val variance: Double, val origin: DataOrigin)

object KalmanReconstructor {
    fun reconstructIndexDomain(raw: List<Double?>, parameters: KalmanParameters): List<KalmanPoint> {
        require(raw.size <= 100_000)
        val result = mutableListOf<KalmanPoint>()
        var estimate: Double? = null
        var variance = parameters.initialVariance
        var missingCount = 0
        raw.forEachIndexed { index, observation ->
            val valid = observation?.isFinite() == true
            if (estimate == null) {
                if (valid) {
                    estimate = observation
                    missingCount = 0
                    variance = parameters.initialVariance
                    result.add(KalmanPoint(index, observation!!, variance, DataOrigin.FILTERED))
                }
            } else {
                variance += parameters.processVariance
                require(variance.isFinite())
                if (valid) {
                    val denominator = variance + parameters.observationVariance
                    require(denominator.isFinite())
                    val gain = variance / denominator
                    estimate = estimate!! * (1 - gain) + observation!! * gain
                    variance *= 1 - gain
                    missingCount = 0
                    require(estimate!!.isFinite() && variance.isFinite() && variance >= 0)
                    result.add(KalmanPoint(index, estimate!!, variance, DataOrigin.FILTERED))
                } else {
                    missingCount++
                    if (missingCount <= parameters.maxConsecutiveMissing) {
                        result.add(KalmanPoint(index, estimate!!, variance, DataOrigin.IMPUTED))
                    } else {
                        estimate = null
                    }
                }
            }
        }
        return result.toList()
    }
}
