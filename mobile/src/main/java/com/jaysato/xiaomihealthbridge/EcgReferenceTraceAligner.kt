package com.jaysato.xiaomihealthbridge

import kotlin.math.abs

internal object EcgReferenceTraceAligner {
    data class Sample(val timestampNanos: Long, val value: Double)

    data class PairPoint(
        val rawTimestampNanos: Long,
        val referenceTimestampNanos: Long,
        val raw: Double,
        val millivolts: Double
    )

    data class Result(
        val pairs: List<PairPoint>,
        val unmatchedRawCount: Int,
        val unmatchedReferenceCount: Int,
        val matchedPairCount: Int,
        val error: String? = null
    )

    fun align(
        raw: List<Sample>,
        reference: List<Sample>,
        maximumSkewNanos: Long,
        maximumPairs: Int
    ): Result {
        if (maximumSkewNanos !in 1L..1_000_000_000L || maximumPairs < 2) {
            return Result(emptyList(), raw.size, reference.size, 0, "配對門檻無效")
        }
        if (!validTrace(raw) || !validTrace(reference)) {
            return Result(emptyList(), raw.size, reference.size, 0, "波形時間戳必須為正值、嚴格遞增且樣本有限")
        }
        val rawPeriod = medianInterval(raw)
        val referencePeriod = medianInterval(reference)
        val pairingSkewLimit = minOf(maximumSkewNanos, minOf(rawPeriod, referencePeriod) / 2L)
        if (pairingSkewLimit < 1L) {
            return Result(emptyList(), raw.size, reference.size, 0, "兩端 ECG 時間戳精度不足以形成半取樣週期內的配對")
        }

        val pairs = ArrayList<PairPoint>(minOf(raw.size, reference.size, maximumPairs))
        var referenceIndex = 0
        var matchedRaw = 0
        for (rawSample in raw) {
            while (referenceIndex + 1 < reference.size &&
                distance(reference[referenceIndex + 1].timestampNanos, rawSample.timestampNanos) <
                distance(reference[referenceIndex].timestampNanos, rawSample.timestampNanos)
            ) {
                referenceIndex++
            }
            val candidate = reference.getOrNull(referenceIndex) ?: continue
            if (distance(candidate.timestampNanos, rawSample.timestampNanos) <= pairingSkewLimit) {
                pairs += PairPoint(
                    rawSample.timestampNanos,
                    candidate.timestampNanos,
                    rawSample.value,
                    candidate.value
                )
                matchedRaw++
                referenceIndex++
            }
        }

        val selected = if (pairs.size <= maximumPairs) pairs else evenlySample(pairs, maximumPairs)
        return Result(
            pairs = selected,
            unmatchedRawCount = raw.size - matchedRaw,
            unmatchedReferenceCount = reference.size - matchedRaw,
            matchedPairCount = matchedRaw,
            error = null
        )
    }

    private fun validTrace(samples: List<Sample>): Boolean =
        samples.isNotEmpty() && samples.all { it.timestampNanos > 0L && it.value.isFinite() } &&
            samples.zipWithNext().all { (before, after) -> after.timestampNanos > before.timestampNanos }

    private fun medianInterval(samples: List<Sample>): Long {
        if (samples.size < 2) return Long.MAX_VALUE
        val intervals = samples.zipWithNext { before, after -> after.timestampNanos - before.timestampNanos }.sorted()
        val middle = intervals.size / 2
        return if (intervals.size % 2 == 1) intervals[middle]
        else intervals[middle - 1] + (intervals[middle] - intervals[middle - 1]) / 2L
    }

    private fun distance(first: Long, second: Long): Long = abs(first - second)

    private fun evenlySample(points: List<PairPoint>, maximumPairs: Int): List<PairPoint> =
        List(maximumPairs) { index ->
            points[(index.toLong() * (points.lastIndex) / (maximumPairs - 1)).toInt()]
        }
}
