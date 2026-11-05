package com.jaysato.xiaomihealthbridge.engine

data class SignalGap(val startIndex: Int, val endExclusive: Int)

object GapDetector {
    fun detect(raw: List<Double?>): List<SignalGap> {
        require(raw.size <= 100_000)
        val gaps = mutableListOf<SignalGap>()
        var index = 0
        while (index < raw.size) {
            if (raw[index]?.isFinite() == true) {
                index++
            } else {
                val start = index
                while (index < raw.size && raw[index]?.isFinite() != true) index++
                gaps.add(SignalGap(start, index))
            }
        }
        return gaps
    }
}

data class FullCoveragePoint(
    val index: Int,
    val value: Double,
    val origin: DataOrigin,
    val method: String,
    val leftMeasuredDistance: Int?,
    val rightMeasuredDistance: Int?,
    val provenance: SensorProvenance
) {
    val measured: Boolean get() = origin == DataOrigin.MEASURED
    val nearestMeasuredDistance: Int? get() = listOfNotNull(leftMeasuredDistance, rightMeasuredDistance).minOrNull()
    init {
        require(index >= 0 && value.isFinite())
        require(method.isNotBlank() && origin != DataOrigin.UNKNOWN)
        require(leftMeasuredDistance == null || leftMeasuredDistance >= 0)
        require(rightMeasuredDistance == null || rightMeasuredDistance >= 0)
    }
}

enum class SignalPurposeGate { PASS, LIMITED, FAIL }

data class FullCoverageSignal(val points: List<FullCoveragePoint>) {
    init {
        require(points.isNotEmpty() && points.size <= 100_000)
        require(points.withIndex().all { (index, point) -> index == point.index })
        require(points.map { it.provenance.sessionId }.distinct().size == 1)
    }
    val originRatios: Map<DataOrigin, Double> get() = DataOrigin.entries.associateWith { origin ->
        points.count { it.origin == origin }.toDouble() / points.size
    }
    val waveformView = SignalPurposeGate.LIMITED
    val trendAnalysis = SignalPurposeGate.FAIL
    val rateEstimation = SignalPurposeGate.FAIL
    val hrv = SignalPurposeGate.FAIL
    val physiologicalConversion = SignalPurposeGate.FAIL
}

sealed class FullCoverageResult {
    data class Complete(val signal: FullCoverageSignal) : FullCoverageResult()
    data class Unavailable(val reason: String) : FullCoverageResult()
}

object FullCoverageSignalComposer {
    fun composeIndexDomain(
        rawValues: List<Double?>,
        provenance: SensorProvenance,
        fullCoverageEnabled: Boolean,
        orderingVerified: Boolean,
        channelMappingVerified: Boolean
    ): FullCoverageResult {
        if (!fullCoverageEnabled) return FullCoverageResult.Unavailable("FULL_COVERAGE_DISABLED")
        if (rawValues.isEmpty() || rawValues.size > 100_000) return FullCoverageResult.Unavailable("INVALID_INPUT_SIZE")
        if (!orderingVerified) return FullCoverageResult.Unavailable("UNVERIFIED_ORDERING")
        val raw = rawValues.toList()
        val measuredIndices = raw.indices.filter { raw[it]?.isFinite() == true }
        if (measuredIndices.isEmpty()) return FullCoverageResult.Unavailable("NO_OBSERVATIONS_OR_VALIDATED_GENERATOR")
        val measuredValues = measuredIndices.map { raw[it]!! }.sorted()
        val medianIndex = measuredValues.size / 2
        val baseline = if (measuredValues.size % 2 == 0) {
            measuredValues[medianIndex - 1] * 0.5 + measuredValues[medianIndex] * 0.5
        } else measuredValues[medianIndex]
        if (!baseline.isFinite()) return FullCoverageResult.Unavailable("NUMERIC_OVERFLOW")
        val left = arrayOfNulls<Int>(raw.size)
        val right = arrayOfNulls<Int>(raw.size)
        var nearest: Int? = null
        for (index in raw.indices) {
            if (raw[index]?.isFinite() == true) nearest = index
            left[index] = nearest
        }
        nearest = null
        for (index in raw.indices.reversed()) {
            if (raw[index]?.isFinite() == true) nearest = index
            right[index] = nearest
        }
        val gapSizes = IntArray(raw.size)
        GapDetector.detect(raw).forEach { gap ->
            for (index in gap.startIndex until gap.endExclusive) gapSizes[index] = gap.endExclusive - gap.startIndex
        }
        val points = raw.indices.map { index ->
            val rawValue = raw[index]
            val leftIndex = left[index]
            val rightIndex = right[index]
            val measured = rawValue?.isFinite() == true
            val shortGap = !measured && gapSizes[index] <= 2 && leftIndex != null && rightIndex != null
            val value = when {
                measured -> rawValue!!
                shortGap -> {
                    val fraction = (index - leftIndex!!).toDouble() / (rightIndex!! - leftIndex)
                    LinearInterpolator.interpolate(raw[leftIndex]!!, raw[rightIndex]!!, fraction)
                }
                else -> baseline
            }
            val origin = if (measured) DataOrigin.MEASURED else DataOrigin.IMPUTED
            val method = when {
                measured -> "raw-preserved"
                shortGap -> "linear-index-domain-v1"
                else -> "session-median-index-domain-v1"
            }
            FullCoveragePoint(
                index, value, origin, method,
                leftIndex?.let { index - it }, rightIndex?.let { it - index },
                provenance.copy(
                    provenance = "${provenance.provenance}; raw index $index; $method; channel_mapping=${if (channelMappingVerified) "verified" else "unverified"}",
                    algorithmVersion = "full-coverage-index-v1",
                    modelVersion = null,
                    timestampSource = TimestampSource.UNKNOWN,
                    calibrationSource = null,
                    confidence = if (measured) provenance.confidence else minOf(provenance.confidence, if (shortGap) 0.25 else 0.05),
                    validationStatus = "INDEX_DOMAIN_RESEARCH_ONLY_NOT_PHYSIOLOGICAL"
                )
            )
        }
        if (points.any { !it.value.isFinite() }) return FullCoverageResult.Unavailable("NUMERIC_OVERFLOW")
        return FullCoverageResult.Complete(FullCoverageSignal(points))
    }
}
