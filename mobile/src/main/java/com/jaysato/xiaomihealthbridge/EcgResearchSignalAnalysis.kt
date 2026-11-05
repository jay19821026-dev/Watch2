package com.jaysato.xiaomihealthbridge

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal object EcgResearchSignalAnalysis {
    data class Result(
        val filtered: List<Float>,
        val baselinePowerRatio: Double,
        val ecgBandPowerRatio: Double,
        val mains60PowerRatio: Double,
        val highFrequencyPowerRatio: Double,
        val dominantFrequencyHz: Double,
        val rawEngineeringQualityScore: Int,
        val correctedBaselinePowerRatio: Double,
        val correctedEcgBandPowerRatio: Double,
        val correctedMains60PowerRatio: Double,
        val correctedHighFrequencyPowerRatio: Double,
        val engineeringQualityScore: Int,
        val stableWindowQualityScore: Int?,
        val stableWindowCoverageRatio: Double,
        val stableWindowCount: Int,
        val issues: List<String>
    )

    fun analyze(input: List<Float>, sampleRateHz: Float): Result? {
        if (input.size < 128 || sampleRateHz !in 100f..2_000f || input.any { !it.isFinite() }) return null
        val raw = input.map(Float::toDouble).toDoubleArray()
        val notch = biquad(raw, sampleRateHz.toDouble(), Type.NOTCH, 60.0, 30.0)
        val highPass = biquad(notch, sampleRateHz.toDouble(), Type.HIGH_PASS, 0.5, 1.0 / sqrt(2.0))
        val filtered = biquad(highPass, sampleRateHz.toDouble(), Type.LOW_PASS, 40.0, 1.0 / sqrt(2.0))
        val spectrum = spectrumRatios(raw, sampleRateHz.toDouble())
        val settlingPoints = sampleRateHz.toInt().coerceAtMost((filtered.size / 3).coerceAtLeast(0))
        val correctedForMetrics = filtered.drop(settlingPoints).toDoubleArray().takeIf { it.size >= 128 } ?: filtered
        val correctedSpectrum = spectrumRatios(correctedForMetrics, sampleRateHz.toDouble())
        val rawEngineeringScore = engineeringScore(spectrum)
        val engineeringScore = engineeringScore(correctedSpectrum)
        val stableWindows = stableWindowScores(filtered, sampleRateHz.toDouble(), correctedSpectrum.baseline)
        val stableWindowScore = stableWindows.scores.sorted().let { scores ->
            if (scores.isEmpty()) null else scores[scores.size / 2]
        }
        val issues = buildList {
            if (spectrum.mains60 > 0.10) add("60 Hz 工頻干擾偏高；研究濾波後 ${"%.1f".format(correctedSpectrum.mains60 * 100)}%")
            if (spectrum.baseline > 0.10) add("低頻基線漂移偏高；研究濾波後 ${"%.1f".format(correctedSpectrum.baseline * 100)}%")
            if (spectrum.high > 0.10) add("40 Hz 以上高頻雜訊偏高；研究濾波後 ${"%.1f".format(correctedSpectrum.high * 100)}%")
            if (spectrum.ecgBand < 0.60) add("0.5–40 Hz ECG 常用頻帶占比偏低；研究濾波後 ${"%.1f".format(correctedSpectrum.ecgBand * 100)}%")
        }
        return Result(
            filtered = filtered.map(Double::toFloat),
            baselinePowerRatio = spectrum.baseline,
            ecgBandPowerRatio = spectrum.ecgBand,
            mains60PowerRatio = spectrum.mains60,
            highFrequencyPowerRatio = spectrum.high,
            dominantFrequencyHz = spectrum.dominantFrequency,
            rawEngineeringQualityScore = rawEngineeringScore,
            correctedBaselinePowerRatio = correctedSpectrum.baseline,
            correctedEcgBandPowerRatio = correctedSpectrum.ecgBand,
            correctedMains60PowerRatio = correctedSpectrum.mains60,
            correctedHighFrequencyPowerRatio = correctedSpectrum.high,
            engineeringQualityScore = engineeringScore,
            stableWindowQualityScore = stableWindowScore,
            stableWindowCoverageRatio = stableWindows.coverageRatio,
            stableWindowCount = stableWindows.scores.size,
            issues = issues
        )
    }

    private data class StableWindows(val scores: List<Int>, val coverageRatio: Double)

    private fun stableWindowScores(
        filtered: DoubleArray,
        sampleRate: Double,
        wholeCaptureBaselineRatio: Double
    ): StableWindows {
        val windowSize = (sampleRate * 2.0).toInt()
        val settlingPoints = sampleRate.toInt()
        if (windowSize < 128 || filtered.size < settlingPoints + windowSize) return StableWindows(emptyList(), 0.0)
        val step = windowSize / 2
        val scores = mutableListOf<Int>()
        var evaluated = 0
        var start = settlingPoints
        while (start + windowSize <= filtered.size) {
            val window = filtered.copyOfRange(start, start + windowSize)
            evaluated += 1
            if (hasUsableDynamics(window)) {
                val spectrum = spectrumRatios(window, sampleRate)
                val score = (engineeringScore(spectrum) - wholeCaptureBaselineRatio * 20.0)
                    .toInt()
                    .coerceIn(1, 99)
                scores += score
            }
            start += step
        }
        return StableWindows(scores, if (evaluated == 0) 0.0 else scores.size.toDouble() / evaluated)
    }

    private fun hasUsableDynamics(values: DoubleArray): Boolean {
        if (values.isEmpty()) return false
        val minimum = values.minOrNull() ?: return false
        val maximum = values.maxOrNull() ?: return false
        val range = maximum - minimum
        if (!range.isFinite() || range <= 1e-9) return false
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return variance.isFinite() && variance > 1e-12
    }

    private fun engineeringScore(spectrum: Spectrum): Int = (
            100.0 -
                spectrum.mains60 * 45.0 -
                spectrum.high * 30.0 -
                spectrum.baseline * 20.0 -
                (0.60 - spectrum.ecgBand).coerceAtLeast(0.0) * 50.0
            ).toInt().coerceIn(1, 99)

    private data class Spectrum(
        val baseline: Double,
        val ecgBand: Double,
        val mains60: Double,
        val high: Double,
        val dominantFrequency: Double
    )

    private fun spectrumRatios(values: DoubleArray, sampleRate: Double): Spectrum {
        val size = minOf(values.size, 2_048)
        val source = values.takeLast(size).toDoubleArray()
        val meanIndex = (size - 1) / 2.0
        val meanValue = source.average()
        var covariance = 0.0
        var indexVariance = 0.0
        for (index in source.indices) {
            val centeredIndex = index - meanIndex
            covariance += centeredIndex * (source[index] - meanValue)
            indexVariance += centeredIndex * centeredIndex
        }
        val slope = covariance / indexVariance.coerceAtLeast(1e-12)
        val intercept = meanValue - slope * meanIndex
        val centered = DoubleArray(size) { index ->
            val window = 0.5 - 0.5 * cos(2.0 * PI * index / max(1, size - 1))
            (source[index] - intercept - slope * index) * window
        }
        val maxBin = size / 2
        var total = 0.0
        var baseline = 0.0
        var ecgBand = 0.0
        var mains60 = 0.0
        var high = 0.0
        var dominantPower = 0.0
        var dominantFrequency = 0.0
        for (bin in 1..maxBin) {
            val frequency = bin * sampleRate / size
            var real = 0.0
            var imaginary = 0.0
            for (index in centered.indices) {
                val angle = 2.0 * PI * bin * index / size
                real += centered[index] * cos(angle)
                imaginary -= centered[index] * sin(angle)
            }
            val power = real * real + imaginary * imaginary
            total += power
            when {
                frequency < 0.5 -> baseline += power
                frequency < 40.0 -> ecgBand += power
                else -> high += power
            }
            if (frequency in 59.0..61.0) mains60 += power
            if (power > dominantPower) {
                dominantPower = power
                dominantFrequency = frequency
            }
        }
        val denominator = total.coerceAtLeast(1e-12)
        return Spectrum(
            baseline / denominator,
            ecgBand / denominator,
            mains60 / denominator,
            high / denominator,
            dominantFrequency
        )
    }

    private enum class Type { NOTCH, HIGH_PASS, LOW_PASS }

    private fun biquad(values: DoubleArray, sampleRate: Double, type: Type, frequency: Double, q: Double): DoubleArray {
        val omega = 2.0 * PI * frequency / sampleRate
        val cosine = cos(omega)
        val alpha = sin(omega) / (2.0 * q)
        val coefficients = when (type) {
            Type.NOTCH -> doubleArrayOf(1.0, -2.0 * cosine, 1.0, 1.0 + alpha, -2.0 * cosine, 1.0 - alpha)
            Type.LOW_PASS -> doubleArrayOf((1.0 - cosine) / 2.0, 1.0 - cosine, (1.0 - cosine) / 2.0, 1.0 + alpha, -2.0 * cosine, 1.0 - alpha)
            Type.HIGH_PASS -> doubleArrayOf((1.0 + cosine) / 2.0, -(1.0 + cosine), (1.0 + cosine) / 2.0, 1.0 + alpha, -2.0 * cosine, 1.0 - alpha)
        }
        val b0 = coefficients[0] / coefficients[3]
        val b1 = coefficients[1] / coefficients[3]
        val b2 = coefficients[2] / coefficients[3]
        val a1 = coefficients[4] / coefficients[3]
        val a2 = coefficients[5] / coefficients[3]
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        return DoubleArray(values.size) { index ->
            val x0 = values[index]
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            y0
        }
    }
}
