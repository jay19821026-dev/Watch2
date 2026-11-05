package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal object RecentDailyMetricFallback {
    data class DailyTotal(val date: LocalDate, val total: Long)

    fun latestDailyTotal(
        records: List<Pair<Instant, Long>>,
        zoneId: ZoneId,
        today: LocalDate
    ): DailyTotal? = records.asSequence()
        .filter { (_, value) -> value >= 0L }
        .groupBy { (time, _) -> time.atZone(zoneId).toLocalDate() }
        .map { (date, dayRecords) -> DailyTotal(date, dayRecords.sumOf { it.second }) }
        .filter { it.date <= today }
        .maxByOrNull { it.date }
}

