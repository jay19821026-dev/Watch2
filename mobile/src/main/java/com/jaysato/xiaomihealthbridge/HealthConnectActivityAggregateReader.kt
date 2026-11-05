package com.jaysato.xiaomihealthbridge

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period

internal class HealthConnectActivityAggregateReader(private val client: HealthConnectClient) {
    suspend fun dailySteps(start: LocalDateTime, end: LocalDateTime): List<HealthConnectActivityObservationPolicy.DayValue> =
        client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Period.ofDays(1)
            )
        ).mapNotNull { group ->
            group.result.get(StepsRecord.COUNT_TOTAL)?.toDouble()?.let { value ->
                HealthConnectActivityObservationPolicy.fromDailyPeriodBucket(
                    group.startTime, value, group.result.dataOrigins.map { it.packageName }.toSet()
                )
            }
        }

    suspend fun intervalSteps(start: Instant, end: Instant): Pair<Double?, Int> {
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return result.get(StepsRecord.COUNT_TOTAL)?.toDouble() to
            HealthConnectActivityObservationPolicy.validSourceCount(result.dataOrigins.map { it.packageName })
    }
}
