package com.jaysato.xiaomihealthbridge

import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.stubs.Stub
import androidx.health.connect.client.testing.FakePermissionController
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.Period
import java.time.ZoneOffset

class HealthConnectIntervalRangeIntegrationTest {
    @Test
    fun fakeClientReturnsOnlyFullyContainedIntervalRecords() = runBlocking {
        val start = Instant.parse("2026-09-01T00:00:00Z")
        val end = Instant.parse("2026-09-08T00:00:00Z")
        val client = FakeHealthConnectClient(
            "com.jaysato.xiaomihealthbridge.test",
            Clock.fixed(end, ZoneOffset.UTC),
            FakePermissionController(true)
        )
        val records = listOf(
            stepsRecord(start.minusSeconds(7_200), start.minusSeconds(3_600), 10L),
            stepsRecord(start.minusSeconds(3_600), start.plusSeconds(3_600), 20L),
            stepsRecord(start, start.plusSeconds(60), 25L),
            stepsRecord(start.plusSeconds(3_600), start.plusSeconds(7_200), 30L),
            stepsRecord(end.minusSeconds(3_600), end.plusSeconds(3_600), 40L),
            stepsRecord(end, end.plusSeconds(60), 45L),
            stepsRecord(end.plusSeconds(3_600), end.plusSeconds(7_200), 50L)
        )
        client.insertRecords(records)

        val result = client.readRecords(
            ReadRecordsRequest(
                StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )

        assertEquals(listOf(25L, 30L), result.records.map { it.count }.sorted())
    }

    @Test
    fun fakeAggregateHonorsIntervalWindowAndReportsDataOrigins() = runBlocking {
        val start = Instant.parse("2026-09-01T00:00:00Z")
        val end = Instant.parse("2026-09-08T00:00:00Z")
        val client = FakeHealthConnectClient(
            "com.jaysato.xiaomihealthbridge.test",
            Clock.fixed(end, ZoneOffset.UTC),
            FakePermissionController(true)
        )
        val origin = DataOrigin("com.example.watch")
        val intervalRequest = mutableListOf<AggregateRequest>()
        val dailyRequest = mutableListOf<AggregateGroupByPeriodRequest>()
        client.overrides.aggregate = Stub { request ->
            intervalRequest += request
            AggregationResult(
                mapOf(StepsRecord.COUNT_TOTAL.metricKey to 40L),
                emptyMap(),
                setOf(origin)
            )
        }
        client.overrides.aggregateGroupByPeriod = Stub { request ->
            dailyRequest += request
            listOf(
                AggregationResultGroupedByPeriod(
                    AggregationResult(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 25L), emptyMap(), setOf(origin)),
                    start.atZone(ZoneOffset.UTC).toLocalDateTime(),
                    start.plusSeconds(86_400).atZone(ZoneOffset.UTC).toLocalDateTime()
                ),
                AggregationResultGroupedByPeriod(
                    AggregationResult(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 15L), emptyMap(), setOf(origin)),
                    end.minusSeconds(86_400).atZone(ZoneOffset.UTC).toLocalDateTime(),
                    end.atZone(ZoneOffset.UTC).toLocalDateTime()
                )
            )
        }

        val reader = HealthConnectActivityAggregateReader(client)
        val interval = reader.intervalSteps(start, end)
        val daily = reader.dailySteps(
            start.atZone(ZoneOffset.UTC).toLocalDateTime(),
            end.atZone(ZoneOffset.UTC).toLocalDateTime()
        )

        assertEquals(40.0, interval.first!!, 0.0)
        assertEquals(1, interval.second)
        assertEquals(2, daily.size)
        assertEquals(listOf(15.0, 25.0), daily.mapNotNull { it.value }.sorted())
        assertEquals(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)),
            intervalRequest.single()
        )
        assertEquals(
            AggregateGroupByPeriodRequest(
                setOf(StepsRecord.COUNT_TOTAL),
                TimeRangeFilter.between(
                    start.atZone(ZoneOffset.UTC).toLocalDateTime(),
                    end.atZone(ZoneOffset.UTC).toLocalDateTime()
                ),
                Period.ofDays(1)
            ),
            dailyRequest.single()
        )
    }

    @Test
    fun aggregateReaderFiltersBlankOriginsFromProviderResults() = runBlocking {
        val start = Instant.parse("2026-09-28T00:00:00Z")
        val end = Instant.parse("2026-09-29T00:00:00Z")
        val dateTime = start.atZone(ZoneOffset.UTC).toLocalDateTime()
        val validOrigin = DataOrigin("com.example.watch")
        val origins = setOf(DataOrigin("  "), validOrigin)
        val client = FakeHealthConnectClient(
            "com.jaysato.xiaomihealthbridge.test",
            Clock.fixed(end, ZoneOffset.UTC),
            FakePermissionController(true)
        )
        client.overrides.aggregate = Stub {
            AggregationResult(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 40L), emptyMap(), origins)
        }
        client.overrides.aggregateGroupByPeriod = Stub {
            listOf(
                AggregationResultGroupedByPeriod(
                    AggregationResult(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 40L), emptyMap(), origins),
                    dateTime,
                    end.atZone(ZoneOffset.UTC).toLocalDateTime()
                )
            )
        }

        val reader = HealthConnectActivityAggregateReader(client)
        val interval = reader.intervalSteps(start, end)
        val daily = reader.dailySteps(dateTime, end.atZone(ZoneOffset.UTC).toLocalDateTime()).single()

        assertEquals(40.0, interval.first!!, 0.0)
        assertEquals(1, interval.second)
        assertEquals(1, daily.sourceCount)
        assertEquals(setOf("com.example.watch"), daily.sourceIds)
    }

    @Test
    fun readerMapsDstPeriodBucketsAndIntervalTotalConsistently() = runBlocking {
        val zone = java.time.ZoneId.of("America/Los_Angeles")
        val firstDay = java.time.LocalDate.of(2026, 3, 8)
        val endDay = firstDay.plusDays(2)
        val startLocal = firstDay.atStartOfDay()
        val endLocal = endDay.atStartOfDay()
        val start = startLocal.atZone(zone).toInstant()
        val end = endLocal.atZone(zone).toInstant()
        val client = FakeHealthConnectClient(
            "com.jaysato.xiaomihealthbridge.test",
            Clock.fixed(end, zone.rules.getOffset(end)),
            FakePermissionController(true)
        )
        val origin = DataOrigin("com.example.watch")
        val requestedIntervals = mutableListOf<AggregateRequest>()
        val requestedPeriods = mutableListOf<AggregateGroupByPeriodRequest>()
        client.overrides.aggregate = Stub { request ->
            requestedIntervals += request
            AggregationResult(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 150L), emptyMap(), setOf(origin))
        }
        client.overrides.aggregateGroupByPeriod = Stub { request ->
            requestedPeriods += request
            listOf(
                AggregationResultGroupedByPeriod(
                    AggregationResult(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 100L), emptyMap(), setOf(origin)),
                    firstDay.atStartOfDay(),
                    firstDay.plusDays(1).atStartOfDay()
                ),
                AggregationResultGroupedByPeriod(
                    AggregationResult(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 50L), emptyMap(), setOf(origin)),
                    firstDay.plusDays(1).atStartOfDay(),
                    endLocal
                )
            )
        }

        val reader = HealthConnectActivityAggregateReader(client)
        val interval = reader.intervalSteps(start, end)
        val daily = reader.dailySteps(startLocal, endLocal)

        assertEquals(23L, java.time.Duration.between(start, firstDay.plusDays(1).atStartOfDay(zone).toInstant()).toHours())
        assertEquals(150.0, interval.first!!, 0.0)
        assertEquals(1, interval.second)
        assertEquals(listOf(100.0, 50.0), daily.mapNotNull { it.value })
        assertTrue(daily.all { it.sourceCount == 1 })
        assertEquals(interval.first, daily.sumOf { it.value ?: 0.0 })
        assertEquals(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)), requestedIntervals.single())
        assertEquals(
            AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(startLocal, endLocal), Period.ofDays(1)),
            requestedPeriods.single()
        )
    }

    private fun stepsRecord(start: Instant, end: Instant, count: Long) = StepsRecord(
        metadata = Metadata.manualEntry(),
        startTime = start,
        startZoneOffset = ZoneOffset.UTC,
        endTime = end,
        endZoneOffset = ZoneOffset.UTC,
        count = count
    )
}
