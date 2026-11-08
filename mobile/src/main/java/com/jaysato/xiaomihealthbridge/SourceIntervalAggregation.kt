package com.jaysato.xiaomihealthbridge

import java.time.Instant

internal data class SourceIntervalMeasurement(
    val dataOrigin: String,
    val startTime: Instant,
    val endTime: Instant,
    val value: Double
)

internal object SourceIntervalAggregation {
    enum class Rejection {
        NO_RECORDS,
        MULTIPLE_DATA_ORIGINS,
        INVALID_INTERVAL,
        INVALID_VALUE,
        OVERLAPPING_INTERVALS,
        DUPLICATE_SAMPLE_TIMESTAMPS
    }

    data class Assessment(val eligible: Boolean, val rejection: Rejection? = null)

    fun assess(records: List<SourceIntervalMeasurement>): Assessment {
        if (records.isEmpty()) return Assessment(eligible = false, rejection = Rejection.NO_RECORDS)
        if (records.any { it.dataOrigin.isBlank() || !it.startTime.isBefore(it.endTime) }) {
            return Assessment(eligible = false, rejection = Rejection.INVALID_INTERVAL)
        }
        if (records.any { !it.value.isFinite() || it.value < 0.0 }) {
            return Assessment(eligible = false, rejection = Rejection.INVALID_VALUE)
        }
        if (records.map { it.dataOrigin }.distinct().size > 1) {
            return Assessment(eligible = false, rejection = Rejection.MULTIPLE_DATA_ORIGINS)
        }
        val chronological = records.sortedWith(compareBy<SourceIntervalMeasurement> { it.startTime }.thenBy { it.endTime })
        if (chronological.zipWithNext().any { (previous, current) -> current.startTime.isBefore(previous.endTime) }) {
            return Assessment(eligible = false, rejection = Rejection.OVERLAPPING_INTERVALS)
        }
        return Assessment(eligible = true)
    }

    fun hasEquivalentCoverage(
        first: List<SourceIntervalMeasurement>,
        second: List<SourceIntervalMeasurement>
    ): Boolean {
        if (!assess(first).eligible || !assess(second).eligible) return false
        val firstCoverage = first.map { it.startTime to it.endTime }.sortedWith(
            compareBy<Pair<Instant, Instant>> { it.first }.thenBy { it.second }
        )
        val secondCoverage = second.map { it.startTime to it.endTime }.sortedWith(
            compareBy<Pair<Instant, Instant>> { it.first }.thenBy { it.second }
        )
        return first.first().dataOrigin == second.first().dataOrigin && firstCoverage == secondCoverage
    }

    fun sumOrNull(records: List<SourceIntervalMeasurement>): Double? {
        if (!assess(records).eligible) return null
        return records.sumOf { it.value }.takeIf { it.isFinite() }
    }
}

internal object DailyObservedAggregation {
    fun sumCompleteBucketsOrNull(buckets: List<List<Double>>): List<Double>? {
        if (buckets.isEmpty() || buckets.any { it.isEmpty() || it.any { value -> !value.isFinite() || value < 0.0 } }) {
            return null
        }
        return buckets.map { it.sum() }.takeIf { totals -> totals.all { it.isFinite() } }
    }
}

internal object IntervalBucketAggregation {
    data class Bucket(val start: Instant, val value: Double)
    data class Result(val buckets: List<Bucket>, val excludedCrossBucketIntervals: Int)

    fun coversRange(intervals: List<SourceIntervalMeasurement>, start: Instant, end: Instant): Boolean {
        if (!start.isBefore(end) || !SourceIntervalAggregation.assess(intervals).eligible) return false
        val ordered = intervals.sortedBy { it.startTime }
        var coveredUntil = start
        for (interval in ordered) {
            if (interval.startTime < start || interval.endTime > end || interval.startTime != coveredUntil) return false
            coveredUntil = interval.endTime
        }
        return coveredUntil == end
    }

    fun sumWholeIntervals(
        intervals: List<SourceIntervalMeasurement>,
        bucketStart: (Instant) -> Instant,
        bucketEnd: (Instant) -> Instant
    ): Result {
        val included = mutableMapOf<Instant, Double>()
        var excluded = 0
        intervals.forEach { interval ->
            val start = bucketStart(interval.startTime)
            if (interval.startTime < start || interval.endTime > bucketEnd(start)) {
                excluded++
            } else {
                included[start] = (included[start] ?: 0.0) + interval.value
            }
        }
        return Result(
            buckets = included.entries.sortedBy { it.key }.map { Bucket(it.key, it.value) },
            excludedCrossBucketIntervals = excluded
        )
    }
}
