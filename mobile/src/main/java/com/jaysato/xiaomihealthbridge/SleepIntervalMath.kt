package com.jaysato.xiaomihealthbridge

import java.time.Duration
import java.time.Instant

internal object SleepIntervalMath {
    data class Summary(val sessionMinutes: Long, val sleepMinutes: Long?, val stagesComplete: Boolean)

    fun summarize(sessions: List<SleepSessionValue>, from: Instant, until: Instant): Summary {
        val sessionMinutes = unionMinutes(sessions.map { it.start to it.end }, from, until)
        val stagesComplete = sessions.isNotEmpty() &&
            HealthInsightInputPolicy.sleepStagesCoverSessions(sessions, from, until)
        val sleepMinutes = if (stagesComplete) {
            HealthInsightInputPolicy.sleepHours(sessions, from, until)
                ?.let { (it * 60.0).toLong() }
        } else null
        return Summary(sessionMinutes, sleepMinutes, stagesComplete && sleepMinutes != null)
    }

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
