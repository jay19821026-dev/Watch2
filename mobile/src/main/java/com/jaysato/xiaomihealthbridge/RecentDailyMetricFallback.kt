package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal object RecentDailyMetricFallback {
    data class Record(val origin: String, val start: Instant, val end: Instant, val count: Long)
    data class DailyTotal(val date: LocalDate, val total: Long, val originCount: Int) {
        val isSourceMaximum: Boolean get() = originCount > 1
    }

    fun latestDailyTotal(
        records: List<Record>,
        zoneId: ZoneId,
        today: LocalDate
    ): DailyTotal? = records.asSequence()
        .filter { it.start.atZone(zoneId).toLocalDate() <= today }
        .groupBy { it.start.atZone(zoneId).toLocalDate() }
        .mapNotNull { (date, dayRecords) ->
            val nextMidnight = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            if (dayRecords.any { it.origin.isBlank() || it.count < 0L ||
                    !it.start.isBefore(it.end) || it.end.isAfter(nextMidnight) }) {
                return@mapNotNull null
            }
            val totals = dayRecords.groupBy { it.origin }.values.map { sourceRecords ->
                val ordered = sourceRecords.sortedBy { it.start }
                if (ordered.zipWithNext().any { (left, right) -> right.start.isBefore(left.end) }) {
                    return@mapNotNull null
                }
                try {
                    ordered.fold(0L) { sum, record -> Math.addExact(sum, record.count) }
                } catch (_: ArithmeticException) {
                    return@mapNotNull null
                }
            }
            DailyTotal(date, totals.maxOrNull() ?: return@mapNotNull null, totals.size)
        }
        .maxByOrNull { it.date }
}

