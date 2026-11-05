package com.jaysato.xiaomihealthbridge

import java.time.LocalDate
import java.time.ZoneId

internal object StepDayAggregation {
    fun totalsByObservedDay(
        intervals: List<StepIntervalMeasurement>,
        zone: ZoneId
    ): Map<LocalDate, Long>? {
        if (intervals.isEmpty()) return null
        val sourceIntervals = intervals.map {
            SourceIntervalMeasurement(it.dataOrigin, it.startTime, it.endTime, it.count.toDouble())
        }
        if (!SourceIntervalAggregation.assess(sourceIntervals).eligible) return null
        if (intervals.any { HealthInsightInputPolicy.crossesLocalDate(it.startTime, it.endTime, zone) }) {
            return null
        }
        val totals = sortedMapOf<LocalDate, Long>()
        return try {
            intervals.forEach { interval ->
                val day = interval.endTime.minusNanos(1).atZone(zone).toLocalDate()
                totals[day] = Math.addExact(totals[day] ?: 0L, interval.count)
            }
            totals
        } catch (_: ArithmeticException) {
            null
        }
    }
}

internal data class StepIntervalMeasurement(
    val dataOrigin: String,
    val startTime: java.time.Instant,
    val endTime: java.time.Instant,
    val count: Long
)
