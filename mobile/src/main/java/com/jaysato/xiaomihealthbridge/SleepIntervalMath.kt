package com.jaysato.xiaomihealthbridge

import java.time.Duration
import java.time.Instant

internal object SleepIntervalMath {
    fun unionMinutes(
        intervals: Iterable<Pair<Instant, Instant>>,
        from: Instant,
        until: Instant
    ): Long {
        val clipped = intervals.mapNotNull { (start, end) ->
            val clippedStart = maxOf(start, from)
            val clippedEnd = minOf(end, until)
            (clippedStart to clippedEnd).takeIf { clippedEnd.isAfter(clippedStart) }
        }.sortedBy { it.first }
        if (clipped.isEmpty()) return 0L

        var mergedStart = clipped.first().first
        var mergedEnd = clipped.first().second
        var totalMinutes = 0L
        clipped.drop(1).forEach { (start, end) ->
            if (!start.isAfter(mergedEnd)) {
                if (end.isAfter(mergedEnd)) mergedEnd = end
            } else {
                totalMinutes += Duration.between(mergedStart, mergedEnd).toMinutes()
                mergedStart = start
                mergedEnd = end
            }
        }
        return totalMinutes + Duration.between(mergedStart, mergedEnd).toMinutes()
    }
}
