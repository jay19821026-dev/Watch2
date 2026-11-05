package com.jaysato.xiaomihealthbridge

import kotlin.math.abs
import kotlin.math.sqrt

internal object ResearchSignalSummary {
    const val VERSION = "research-quality-index-v1"
    private const val MAX_ANALYSIS_POINTS = 100_000

    enum class DescriptiveSupport { NONE, LOW, MODERATE, HIGH }

    data class Result(
        val values: List<Double?>,
        val relativeAmplitude: List<Double?>,
        val origins: List<String>,
        val measuredRatio: Double,
        val mean: Double?,
        val standardDeviation: Double?,
        val indexTrend: Double?,
        val meanAbsoluteDifference: Double?,
        val reconstructionCount: Int,
        val descriptiveSupport: DescriptiveSupport
    )

    fun supportLabel(support: DescriptiveSupport): String = when (support) {
        DescriptiveSupport.NONE -> "無有效樣本"
        DescriptiveSupport.LOW -> "低"
        DescriptiveSupport.MODERATE -> "中"
        DescriptiveSupport.HIGH -> "高"
    }

    fun supportFor(finiteCount: Int, totalCount: Int): DescriptiveSupport = when {
        finiteCount <= 0 || totalCount <= 0 -> DescriptiveSupport.NONE
        finiteCount >= 32 && finiteCount.toDouble() / totalCount >= 0.8 -> DescriptiveSupport.HIGH
        finiteCount >= 8 && finiteCount.toDouble() / totalCount >= 0.5 -> DescriptiveSupport.MODERATE
        else -> DescriptiveSupport.LOW
    }

    fun analyze(
        input: List<Double?>,
        mappedOrderedWaveform: Boolean,
        preserveInputOrder: Boolean = false,
        missingSentinels: Set<Double> = emptySet()
    ): Result {
        val boundedInput = input.take(MAX_ANALYSIS_POINTS)
        val isMeasured: (Double?) -> Boolean = { value ->
            value != null && value.isFinite() && value !in missingSentinels
        }
        val values = boundedInput.map { if (isMeasured(it)) it else null }.toMutableList()
        val origins = values.map { if (it == null) "UNKNOWN" else "MEASURED" }.toMutableList()
        var reconstructionCount = 0

        if (mappedOrderedWaveform && !preserveInputOrder) {
            var index = 1
            while (index < values.lastIndex) {
                if (values[index] != null) {
                    index++
                    continue
                }
                val gapStart = index
                while (index < values.size && values[index] == null) index++
                val gapLength = index - gapStart
                val left = values.getOrNull(gapStart - 1)
                val right = values.getOrNull(index)
                if (gapLength == 1 && boundedInput[gapStart] == null && left != null && right != null) {
                    values[gapStart] = (left + right) / 2.0
                    origins[gapStart] = "IMPUTED"
                    reconstructionCount++
                }
            }
        }

        val measured = boundedInput.filter(isMeasured)
        val finiteAnalysis = values.filterNotNull().filter { it.isFinite() }
        val mean = finiteAnalysis.takeIf { it.isNotEmpty() }?.average()
        val standardDeviation = mean?.let { average ->
            sqrt(finiteAnalysis.sumOf { (it - average) * (it - average) } / finiteAnalysis.size)
        }
        val orderedFinite = values.mapIndexedNotNull { pointIndex, value ->
            value?.takeIf { it.isFinite() }?.let { pointIndex.toDouble() to it }
        }
        val canDescribeOrder = mappedOrderedWaveform || preserveInputOrder
        val indexTrend = if (canDescribeOrder && orderedFinite.size >= 2) {
            val xMean = orderedFinite.map { it.first }.average()
            val yMean = orderedFinite.map { it.second }.average()
            val denominator = orderedFinite.sumOf { (x, _) -> (x - xMean) * (x - xMean) }
            if (denominator > 0.0) {
                orderedFinite.sumOf { (x, y) -> (x - xMean) * (y - yMean) } / denominator
            } else null
        } else null
        val adjacentDifferences = if (canDescribeOrder) {
            values.zipWithNext().mapNotNull { (first, second) ->
                if (first != null && second != null && first.isFinite() && second.isFinite()) abs(second - first) else null
            }
        } else emptyList()
        val meanAbsoluteDifference = if (adjacentDifferences.isNotEmpty()) {
            adjacentDifferences.average()
        } else null
        val amplitudeValues = values.filterNotNull().filter { it.isFinite() }
        val min = amplitudeValues.minOrNull()
        val max = amplitudeValues.maxOrNull()
        val relativeAmplitude = values.map { value ->
            if (value == null || !value.isFinite() || min == null || max == null) null
            else if (max == min) 0.5 else (value - min) / (max - min)
        }

        return Result(
            values = values,
            relativeAmplitude = relativeAmplitude,
            origins = origins,
            measuredRatio = if (boundedInput.isEmpty()) 0.0 else measured.size.toDouble() / boundedInput.size,
            mean = mean,
            standardDeviation = standardDeviation,
            indexTrend = indexTrend,
            meanAbsoluteDifference = meanAbsoluteDifference,
            reconstructionCount = reconstructionCount,
            descriptiveSupport = supportFor(measured.size, boundedInput.size)
        )
    }

    fun describe(result: Result): String {
        val amplitudes = result.relativeAmplitude.filterNotNull().sorted()
        if (amplitudes.isEmpty()) return "沒有足夠有限樣本可整理；不代表生理準確度。"
        fun percentile(fraction: Double): Double {
            val position = amplitudes.lastIndex * fraction
            val lowerIndex = position.toInt()
            val upperIndex = (lowerIndex + 1).coerceAtMost(amplitudes.lastIndex)
            val weight = position - lowerIndex
            return amplitudes[lowerIndex] * (1.0 - weight) + amplitudes[upperIndex] * weight
        }
        return "研究波形相對振幅分布：P10 ${"%.2f".format(java.util.Locale.ROOT, percentile(0.1))} · " +
            "中位 ${"%.2f".format(java.util.Locale.ROOT, percentile(0.5))} · " +
            "P90 ${"%.2f".format(java.util.Locale.ROOT, percentile(0.9))}；不代表生理準確度。"
    }
}
