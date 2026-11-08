package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal object RecentDailyEnergyFallback {
    data class Record(val origin: String, val start: Instant, val end: Instant, val kilocalories: Double)
    data class DailyTotal(val date: LocalDate, val kilocalories: Double, val originCount: Int) {
        val isSourceMaximum: Boolean get() = originCount > 1
    }

    fun latestDailyTotal(records: List<Record>, zoneId: ZoneId, today: LocalDate): DailyTotal? =
        records.asSequence()
            .filter { it.start.atZone(zoneId).toLocalDate() <= today }
            .groupBy { it.start.atZone(zoneId).toLocalDate() }
            .mapNotNull { (date, dayRecords) ->
                val nextMidnight = date.plusDays(1).atStartOfDay(zoneId).toInstant()
                if (dayRecords.any { it.end.isAfter(nextMidnight) }) return@mapNotNull null
                val sourceTotals = dayRecords.groupBy { it.origin }.values.map { sourceRecords ->
                    val intervals = sourceRecords.map {
                        SourceIntervalMeasurement(it.origin, it.start, it.end, it.kilocalories)
                    }
                    SourceIntervalAggregation.sumOrNull(intervals) ?: return@mapNotNull null
                }
                DailyTotal(date, sourceTotals.maxOrNull() ?: return@mapNotNull null, sourceTotals.size)
            }
            .maxByOrNull { it.date }
}
