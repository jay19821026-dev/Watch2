package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricDetailAggregationTest {
    @Test
    fun temperatureChartUsesHalfHourDayAndMonthBucketsInLocalTime() {
        val zone = ZoneId.of("Asia/Taipei")
        val morning = Instant.parse("2026-10-09T00:29:59Z")
        val nextHalfHour = Instant.parse("2026-10-09T00:30:00Z")
        assertEquals(Instant.parse("2026-10-09T00:00:00Z"), TemperatureChartWindowPolicy.bucketStart(morning, 0, zone))
        assertEquals(nextHalfHour, TemperatureChartWindowPolicy.bucketStart(nextHalfHour, 0, zone))
        assertEquals(Instant.parse("2026-10-08T16:00:00Z"), TemperatureChartWindowPolicy.bucketStart(morning, 1, zone))
        assertEquals(Instant.parse("2026-10-08T16:00:00Z"), TemperatureChartWindowPolicy.bucketStart(morning, 2, zone))
        assertEquals(Instant.parse("2026-09-30T16:00:00Z"), TemperatureChartWindowPolicy.bucketStart(morning, 3, zone))
    }

    @Test
    fun ambiguousIntervalPreviewPreservesOriginalRowsWithoutSummingAndIsBounded() {
        val intervals = (0 until 30).map { index ->
            val start = Instant.parse("2026-09-28T00:00:00Z").plusSeconds(index * 60L)
            SourceIntervalMeasurement("watch.source", start, start.plusSeconds(120), index.toDouble())
        }

        val preview = MetricDetailAggregation.rawIntervalPreview(intervals)

        assertEquals(24, preview.size)
        assertEquals(29.0, preview.first().value, 0.0)
        assertEquals(6.0, preview.last().value, 0.0)
        assertTrue(preview.zipWithNext().all { (first, second) -> first.startTime.isAfter(second.startTime) })
        assertTrue(MetricDetailAggregation.rawIntervalPreview(intervals, 0).isEmpty())
    }

    @Test
    fun invalidPointValuesSourcesAndOutOfWindowTimestampsFailIntegrityCheck() {
        val start = Instant.parse("2026-09-28T00:00:00Z")
        val end = Instant.parse("2026-09-29T00:00:00Z")
        fun valid(time: Instant = start, value: Double = 70.0, source: String = "watch.app") =
            MetricPointIntegrityPolicy.isValid(MetricPointEvidence(time, value, source, interval = false), start, end)

        assertEquals(true, valid())
        assertEquals(false, valid(value = Double.NaN))
        assertEquals(false, valid(source = " "))
        assertEquals(false, valid(time = end))
        assertEquals(false, valid(time = start.minusSeconds(1)))
        assertEquals(true, MetricPointIntegrityPolicy.isValid(
            MetricPointEvidence(end, 5.0, "watch.app", interval = true), start, end
        ))
    }

    @Test
    fun periodPointsUseInclusiveStartAndExclusiveEnd() {
        val start = Instant.parse("2026-09-28T00:00:00Z")
        val end = Instant.parse("2026-09-29T00:00:00Z")
        assertEquals(true, MetricPointIntegrityPolicy.isInHalfOpenRange(start, start, end))
        assertEquals(false, MetricPointIntegrityPolicy.isInHalfOpenRange(end, start, end))
        assertEquals(false, MetricPointIntegrityPolicy.isInHalfOpenRange(start.minusNanos(1), start, end))
    }

    @Test
    fun invalidIntervalRecordsFailTheWholeDetailRead() {
        val start = Instant.parse("2026-09-28T00:00:00Z")
        assertThrows(MetricDataIntegrityException::class.java) {
            MetricRecordIntervalPolicy.validate(start, start)
        }
        assertThrows(MetricDataIntegrityException::class.java) {
            MetricRecordIntervalPolicy.validate(start.plusSeconds(1), start)
        }
        MetricRecordIntervalPolicy.validate(start, start.plusSeconds(1))
    }

    @Test
    fun duplicateHealthConnectRecordIdsInvalidateTheReadWithoutDedupingByValue() {
        assertEquals(false, HealthConnectRecordIdentityPolicy.hasDuplicates(listOf("id-a", "id-b")))
        assertEquals(true, HealthConnectRecordIdentityPolicy.hasDuplicates(listOf("id-a", "id-a")))
        assertEquals(false, HealthConnectRecordIdentityPolicy.hasDuplicates(emptyList()))
        assertEquals(true, HealthConnectRecordIdentityPolicy.isValid(listOf("id-a", "id-b")))
        assertEquals(false, HealthConnectRecordIdentityPolicy.isValid(listOf("")))
        assertEquals(false, HealthConnectRecordIdentityPolicy.isValid(listOf("id-a", "id-a")))
    }

    private val zone = ZoneId.of("UTC")

    @Test
    fun allIntervalTotalsUseTheCumulativeSourceAndOverlapGate() {
        listOf("steps", "distance", "calories", "sleep").forEach {
            assertEquals(true, MetricDetailPolicy.isCumulative(it))
        }
        listOf("heart_rate", "oxygen", "weight", "temperature").forEach {
            assertEquals(false, MetricDetailPolicy.isCumulative(it))
        }
    }

    @Test
    fun sourceSelectionPreservesChoiceAndHasNoBrandPreference() {
        val origins = listOf("org.xiaomi.health", "com.example.health")
        assertEquals("com.example.health", MetricDetailPolicy.selectSource(null, origins))
        assertEquals("org.xiaomi.health", MetricDetailPolicy.selectSource("org.xiaomi.health", origins))
        assertEquals("com.example.health", MetricDetailPolicy.selectSource("removed.source", origins))
    }

    @Test
    fun sourceDisplayUsesFriendlyLabelsAndDoesNotExposeOpaqueProviderIds() {
        val opaquePackage = "com.android.healthconnect.phone.j23.27dac0db93865bd1a003c69756bbe"
        val label = MetricDetailPolicy.displaySource(opaquePackage, "my.app", null)
        assertTrue(label.startsWith("Health Connect 匯入來源"))
        assertFalse(label.contains(opaquePackage))
        val providerLabel = MetricDetailPolicy.displaySource(opaquePackage, "my.app", opaquePackage)
        assertTrue(providerLabel.startsWith("Health Connect 匯入來源"))
        assertFalse(providerLabel.contains(opaquePackage))
        assertEquals(providerLabel, MetricDetailPolicy.healthConnectImportLabel(opaquePackage))
        assertEquals(null, MetricDetailPolicy.healthConnectImportLabel("org.xiaomi.health"))
        assertEquals("步數來源 App", MetricDetailPolicy.displaySource("example.package", "my.app", "步數來源 App"))
        assertEquals("小米運動健康", MetricDetailPolicy.displaySource("com.xiaomi.health", "my.app", "Xiaomi Health"))
    }

    @Test
    fun healthConnectStepAggregateBucketsProduceDailyTotalsWithoutRawIntervalOverlapGate() {
        val buckets = listOf(
            Instant.parse("2026-09-28T08:00:00Z") to 1_200.0,
            Instant.parse("2026-09-28T09:00:00Z") to 800.0,
            Instant.parse("2026-09-29T00:00:00Z") to 500.0
        )

        assertEquals(
            mapOf(LocalDate.parse("2026-09-28") to 2_000.0, LocalDate.parse("2026-09-29") to 500.0),
            MetricDetailAggregation.aggregateDailyStepBuckets(buckets, zone)
        )
        assertEquals(emptyMap<LocalDate, Double>(), MetricDetailAggregation.aggregateDailyStepBuckets(
            listOf(Instant.parse("2026-09-28T08:00:00Z") to Double.NaN), zone
        ))
    }

    @Test
    fun crossMidnightSleepBelongsToWakeDateAndAveragesObservedNights() {
        val sessions = listOf(
            SleepSessionValue(Instant.parse("2026-09-28T23:00:00Z"), Instant.parse("2026-09-29T07:00:00Z")),
            SleepSessionValue(Instant.parse("2026-09-29T22:00:00Z"), Instant.parse("2026-09-30T06:00:00Z")),
            SleepSessionValue(Instant.parse("2026-09-30T23:00:00Z"), Instant.parse("2026-10-01T05:00:00Z"))
        )

        val byWakeDate = MetricDetailAggregation.sleepHoursByWakeDate(sessions, zone)

        assertEquals(mapOf(
            LocalDate.parse("2026-09-29") to 8.0,
            LocalDate.parse("2026-09-30") to 8.0,
            LocalDate.parse("2026-10-01") to 6.0
        ), byWakeDate)
        assertEquals(22.0 / 3.0, MetricDetailAggregation.meanObservedDayTotal(byWakeDate.values)!!, 0.0001)
    }

    @Test
    fun overlappingSleepSessionsAreRejectedInsteadOfDoubleCounted() {
        val sessions = listOf(
            SleepSessionValue(Instant.parse("2026-09-28T22:00:00Z"), Instant.parse("2026-09-29T06:00:00Z")),
            SleepSessionValue(Instant.parse("2026-09-29T05:00:00Z"), Instant.parse("2026-09-29T07:00:00Z"))
        )

        assertEquals(false, MetricDetailAggregation.sleepSessionsAreNonOverlapping(sessions))
        assertEquals(emptyMap<LocalDate, Double>(), MetricDetailAggregation.sleepHoursByWakeDate(sessions, zone))
    }

    @Test
    fun sleepSessionAndStagesAreClippedToSelectedWindow() {
        val start = Instant.parse("2026-09-28T22:00:00Z")
        val end = Instant.parse("2026-09-29T06:00:00Z")
        val windowStart = Instant.parse("2026-09-29T00:00:00Z")
        val windowEnd = Instant.parse("2026-09-29T04:00:00Z")
        val session = SleepSessionValue(
            start,
            end,
            awakeIntervals = listOf(SleepAwakeInterval(
                Instant.parse("2026-09-29T03:30:00Z"), Instant.parse("2026-09-29T04:30:00Z")
            )),
            stageIntervals = listOf(SleepAwakeInterval(start, end))
        )

        val clipped = MetricDetailAggregation.clipSleepSession(session, windowStart, windowEnd)!!

        assertEquals(windowStart, clipped.start)
        assertEquals(windowEnd, clipped.end)
        assertEquals(listOf(SleepAwakeInterval(
            Instant.parse("2026-09-29T03:30:00Z"), windowEnd
        )), clipped.awakeIntervals)
        assertEquals(listOf(SleepAwakeInterval(windowStart, windowEnd)), clipped.stageIntervals)
        assertEquals(3.5, MetricDetailAggregation.sleepDisplayHours(clipped), 0.0)
        assertEquals(null, MetricDetailAggregation.clipSleepSession(session, end, end.plusSeconds(60)))
    }

    @Test
    fun sessionEndingAtMidnightBelongsToPreviousWakeDate() {
        val session = SleepSessionValue(
            Instant.parse("2026-09-28T22:00:00Z"),
            Instant.parse("2026-09-29T00:00:00Z")
        )

        assertEquals(
            mapOf(LocalDate.parse("2026-09-28") to 2.0),
            MetricDetailAggregation.sleepHoursByWakeDate(listOf(session), zone)
        )
    }

    @Test
    fun awakeStageIsExcludedFromSleepDuration() {
        val session = SleepSessionValue(
            Instant.parse("2026-09-28T22:00:00Z"),
            Instant.parse("2026-09-29T06:00:00Z"),
            listOf(SleepAwakeInterval(
                Instant.parse("2026-09-29T02:00:00Z"),
                Instant.parse("2026-09-29T02:30:00Z")
            ))
        )

        assertEquals(7.5, MetricDetailAggregation.sleepHours(session), 0.0)
    }

    @Test
    fun incompleteSleepStagesDisplaySessionWindowWithoutSubtractingPartialAwakeData() {
        val start = Instant.parse("2026-09-28T22:00:00Z")
        val end = Instant.parse("2026-09-29T06:00:00Z")
        val incomplete = SleepSessionValue(
            start,
            end,
            awakeIntervals = listOf(SleepAwakeInterval(
                Instant.parse("2026-09-29T02:00:00Z"), Instant.parse("2026-09-29T02:30:00Z")
            )),
            hasStageData = true,
            stageIntervals = listOf(SleepAwakeInterval(start, Instant.parse("2026-09-29T02:00:00Z"))),
            stageTypesKnown = true
        )

        assertEquals(8.0, MetricDetailAggregation.sleepDisplayHours(incomplete), 0.0)
    }

    @Test
    fun overlappingSleepStagesFallBackToSessionWindowAndAreNotStageComplete() {
        val start = Instant.parse("2026-09-28T22:00:00Z")
        val middle = Instant.parse("2026-09-29T02:00:00Z")
        val end = Instant.parse("2026-09-29T06:00:00Z")
        val overlapping = SleepSessionValue(
            start,
            end,
            awakeIntervals = listOf(SleepAwakeInterval(middle, middle.plusSeconds(1800))),
            hasStageData = true,
            stageIntervals = listOf(
                SleepAwakeInterval(start, middle.plusSeconds(1)),
                SleepAwakeInterval(middle, end)
            ),
            stageTypesKnown = true
        )

        assertEquals(false, HealthInsightInputPolicy.sleepStagesCoverSessions(listOf(overlapping), start, end))
        assertEquals(8.0, MetricDetailAggregation.sleepDisplayHours(overlapping), 0.0)
    }

    @Test
    fun fullyCoveredSleepStagesDisplayDurationAfterAwakeIntervals() {
        val start = Instant.parse("2026-09-28T22:00:00Z")
        val awakeStart = Instant.parse("2026-09-29T02:00:00Z")
        val awakeEnd = Instant.parse("2026-09-29T02:30:00Z")
        val end = Instant.parse("2026-09-29T06:00:00Z")
        val complete = SleepSessionValue(
            start,
            end,
            awakeIntervals = listOf(SleepAwakeInterval(awakeStart, awakeEnd)),
            hasStageData = true,
            stageIntervals = listOf(
                SleepAwakeInterval(start, awakeStart),
                SleepAwakeInterval(awakeStart, awakeEnd),
                SleepAwakeInterval(awakeEnd, end)
            ),
            stageTypesKnown = true
        )

        assertEquals(7.5, MetricDetailAggregation.sleepDisplayHours(complete), 0.0)
    }

    @Test
    fun overlappingAwakeStagesAreUnionedAndClippedToSleepSession() {
        val session = SleepSessionValue(
            Instant.parse("2026-09-28T22:00:00Z"),
            Instant.parse("2026-09-29T06:00:00Z"),
            listOf(
                SleepAwakeInterval(Instant.parse("2026-09-28T21:50:00Z"), Instant.parse("2026-09-29T02:20:00Z")),
                SleepAwakeInterval(Instant.parse("2026-09-29T02:00:00Z"), Instant.parse("2026-09-29T02:30:00Z")),
                SleepAwakeInterval(Instant.parse("2026-09-29T05:30:00Z"), Instant.parse("2026-09-29T06:10:00Z"))
            )
        )

        assertEquals(3.0, MetricDetailAggregation.sleepHours(session), 0.0)
    }

    @Test
    fun splittingOneDayStepIntervalDoesNotChangeObservedDailyMean() {
        val dateStart = Instant.parse("2026-09-28T09:00:00Z")
        val single = listOf(SourceIntervalMeasurement(
            "watch.app", dateStart, dateStart.plusSeconds(3600), 1_000.0
        ))
        val split = listOf(
            SourceIntervalMeasurement("watch.app", dateStart, dateStart.plusSeconds(1800), 500.0),
            SourceIntervalMeasurement("watch.app", dateStart.plusSeconds(1800), dateStart.plusSeconds(3600), 500.0)
        )

        assertEquals(1_000.0, MetricDetailAggregation.meanObservedDailySteps(single, zone)!!, 0.0)
        assertEquals(1_000.0, MetricDetailAggregation.meanObservedDailySteps(split, zone)!!, 0.0)
    }

    @Test
    fun crossMidnightStepIntervalExcludesBothPartialDaysFromObservedMean() {
        val intervals = listOf(
            SourceIntervalMeasurement("watch.app", Instant.parse("2026-09-28T23:30:00Z"), Instant.parse("2026-09-29T00:30:00Z"), 100.0),
            SourceIntervalMeasurement("watch.app", Instant.parse("2026-09-29T10:00:00Z"), Instant.parse("2026-09-29T11:00:00Z"), 400.0)
        )

        assertEquals(emptyMap<LocalDate, Double>(),
            MetricDetailAggregation.observedDailyStepTotals(intervals, zone))
    }

    @Test
    fun yearRangeCannotReturnPartiallyFetchedRecordsPastPageLimit() = runBlocking {
        var calls = 0

        assertThrows(MetricHistoryLimitException::class.java) {
            runBlocking {
                collectMetricRecordPages(maxPages = 1, maxRecords = 10) { _ ->
                    calls++
                    MetricRecordPage(listOf(1), "next")
                }
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun recordLimitRejectsWholeResultRatherThanReturningPrefix() {
        assertThrows(MetricHistoryLimitException::class.java) {
            runBlocking {
                collectMetricRecordPages(maxPages = 4, maxRecords = 2) { token ->
                    if (token == null) MetricRecordPage(listOf(1, 2), "next")
                    else MetricRecordPage(listOf(3), null)
                }
            }
        }
    }

    @Test
    fun repeatedPageTokenFailsImmediatelyWithoutReturningPartialResults() {
        var calls = 0
        assertThrows(MetricHistoryLimitException::class.java) {
            runBlocking {
                collectMetricRecordPages(maxPages = 10, maxRecords = 20) { token ->
                    calls++
                    if (token == null) MetricRecordPage(listOf(1), "loop")
                    else MetricRecordPage(listOf(2), "loop")
                }
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun cancellingBetweenPagesStopsCollection() = runBlocking {
        var calls = 0
        val reader = launch {
            collectMetricRecordPages(maxPages = 4, maxRecords = 10) { token ->
                calls++
                if (token == null) MetricRecordPage(listOf(1), "next") else awaitCancellation()
            }
        }

        yield()
        reader.cancelAndJoin()

        assertEquals(2, calls)
        assertEquals(true, reader.isCancelled)
    }
}
