package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthInsightInputPolicyTest {
    @Test
    fun dailyAggregateBucketsKeepTheirLocalCalendarDateAcrossDstChanges() {
        val zone = ZoneId.of("America/Los_Angeles")
        val springStart = java.time.LocalDateTime.of(2026, 3, 8, 0, 0)
        val springNext = java.time.LocalDateTime.of(2026, 3, 9, 0, 0)
        val fallStart = java.time.LocalDateTime.of(2026, 11, 1, 0, 0)
        val fallNext = java.time.LocalDateTime.of(2026, 11, 2, 0, 0)

        assertEquals(23L, java.time.Duration.between(
            springStart.atZone(zone).toInstant(), springNext.atZone(zone).toInstant()
        ).toHours())
        assertEquals(25L, java.time.Duration.between(
            fallStart.atZone(zone).toInstant(), fallNext.atZone(zone).toInstant()
        ).toHours())
        assertEquals(java.time.LocalDate.of(2026, 3, 8),
            HealthConnectActivityObservationPolicy.fromDailyPeriodBucket(springStart, 1200.0, setOf("source-a")).date)
        assertEquals(java.time.LocalDate.of(2026, 11, 1),
            HealthConnectActivityObservationPolicy.fromDailyPeriodBucket(fallStart, 2400.0, setOf("source-a")).date)
    }

    @Test
    fun blankAggregateOriginsDoNotCountAsSourceEvidence() {
        val dateTime = java.time.LocalDateTime.of(2026, 9, 29, 12, 0)
        val blankOriginRow = HealthConnectActivityObservationPolicy.fromDailyPeriodBucket(
            dateTime, 1200.0, setOf("", "  ")
        )
        val mixedOriginRow = HealthConnectActivityObservationPolicy.fromDailyPeriodBucket(
            dateTime, 1200.0, setOf("", " watch.app ")
        )

        assertEquals(0, blankOriginRow.sourceCount)
        assertTrue(blankOriginRow.sourceIds!!.isEmpty())
        assertEquals(1, mixedOriginRow.sourceCount)
        assertEquals(setOf("watch.app"), mixedOriginRow.sourceIds)
        assertEquals(1, HealthConnectActivityObservationPolicy.validSourceCount(listOf("", "watch.app", "watch.app")))

        assertEquals(HealthConnectActivityObservationPolicy.State.NO_RECORDS,
            HealthConnectActivityObservationPolicy.stateAfterAggregateReads(
                HealthConnectActivityObservationPolicy.State.AVAILABLE,
                dailyReadFailed = false,
                intervalReadFailed = false,
                dailyRows = listOf(blankOriginRow),
                intervalTotal = 1200.0,
                intervalSourceCount = 0
            ))
    }

    @Test
    fun dailyPeriodQueryUsesLocalDateTimeRangeAcrossDaylightSavingTransition() {
        val zone = ZoneId.of("America/Los_Angeles")
        val end = Instant.parse("2026-03-09T15:45:00Z")

        val (startLocal, endLocal) = HealthConnectActivityObservationPolicy.localDailyPeriodRange(
            java.time.LocalDate.of(2026, 3, 3), end, zone
        )

        assertEquals(java.time.LocalDateTime.of(2026, 3, 3, 0, 0), startLocal)
        assertEquals(java.time.LocalDateTime.of(2026, 3, 9, 8, 45), endLocal)
    }

    @Test
    fun dailyPeriodQueryKeepsLocalMidnightAcrossAutumnRepeatedHour() {
        val zone = ZoneId.of("America/Los_Angeles")
        val start = java.time.LocalDate.of(2026, 11, 1)
        val end = Instant.parse("2026-11-02T08:00:00Z")

        val (startLocal, endLocal) = HealthConnectActivityObservationPolicy.localDailyPeriodRange(start, end, zone)

        assertEquals(java.time.LocalDateTime.of(2026, 11, 1, 0, 0), startLocal)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 2, 0, 0), endLocal)
        assertEquals(25L, java.time.Duration.between(start.atStartOfDay(zone).toInstant(), end).toHours())
    }

    @Test
    fun emptyRecordMessageMatchesTheQueriedTimeWindow() {
        assertEquals("今天沒有同步紀錄", HealthConnectEmptyInputPolicy.message(HealthConnectEmptyInputPolicy.Window.TODAY))
        assertEquals("近 7 天沒有同步紀錄", HealthConnectEmptyInputPolicy.message(HealthConnectEmptyInputPolicy.Window.LAST_SEVEN_DAYS))
    }

    @Test
    fun recordedTotalCaloriesSeparatesPermissionReadAndDataQualityStates() {
        val start = Instant.parse("2026-09-29T00:00:00Z")
        val end = start.plusSeconds(60)
        val valid = listOf(SourceIntervalMeasurement("watch", start, end, 12.0))
        val overlap = valid + SourceIntervalMeasurement("watch", start, end, 13.0)
        val multipleSources = valid + SourceIntervalMeasurement("phone", end, end.plusSeconds(60), 4.0)

        assertEquals(RecordedTotalCaloriesState.AVAILABLE,
            RecordedTotalCaloriesPolicy.classify(HealthConnectPermissionPolicy.State.GRANTED, valid))
        assertEquals(RecordedTotalCaloriesState.PERMISSION_DENIED,
            RecordedTotalCaloriesPolicy.classify(HealthConnectPermissionPolicy.State.DENIED, valid))
        assertEquals(RecordedTotalCaloriesState.PERMISSION_QUERY_FAILED,
            RecordedTotalCaloriesPolicy.classify(HealthConnectPermissionPolicy.State.QUERY_FAILED, valid))
        assertEquals(RecordedTotalCaloriesState.READ_FAILED,
            RecordedTotalCaloriesPolicy.classify(HealthConnectPermissionPolicy.State.GRANTED, null))
        assertEquals(RecordedTotalCaloriesState.NO_RECORDS,
            RecordedTotalCaloriesPolicy.classify(HealthConnectPermissionPolicy.State.GRANTED, emptyList()))
        assertEquals(RecordedTotalCaloriesState.BOUNDARY_EXCLUDED,
            RecordedTotalCaloriesPolicy.classify(HealthConnectPermissionPolicy.State.GRANTED, emptyList(), 2))
        assertEquals(RecordedTotalCaloriesState.PARTIAL,
            RecordedTotalCaloriesPolicy.classify(HealthConnectPermissionPolicy.State.GRANTED, valid, 1))
        assertEquals(RecordedTotalCaloriesState.AMBIGUOUS,
            RecordedTotalCaloriesPolicy.classify(HealthConnectPermissionPolicy.State.GRANTED, overlap))
        assertEquals(RecordedTotalCaloriesState.AMBIGUOUS,
            RecordedTotalCaloriesPolicy.classify(HealthConnectPermissionPolicy.State.GRANTED, multipleSources))
        assertTrue(RecordedTotalCaloriesPolicy.display(RecordedTotalCaloriesState.NO_RECORDS, null, null).contains("今天沒有同步紀錄"))
        assertTrue(RecordedTotalCaloriesPolicy.display(RecordedTotalCaloriesState.AMBIGUOUS, null, null).contains("無法可靠合計"))
        assertTrue(RecordedTotalCaloriesPolicy.display(RecordedTotalCaloriesState.AVAILABLE, 12, "watch", 1, 0).contains("不代表全天總量"))
        assertTrue(RecordedTotalCaloriesPolicy.display(RecordedTotalCaloriesState.BOUNDARY_EXCLUDED, null, null, 0, 2).contains("跨日紀錄"))
    }

    @Test
    fun aggregateActivitySummaryKeepsMissingDaysNullAndUsesPlatformIntervalTotal() {
        val zone = ZoneId.of("Asia/Taipei")
        val dates = (6 downTo 0).map { java.time.LocalDate.parse("2026-09-28").minusDays(it.toLong()) }
        val start = dates.first().atStartOfDay(zone).toInstant()
        val end = Instant.parse("2026-09-28T12:00:00Z")
        val summary = HealthConnectActivityObservationPolicy.summarize(
            HealthConnectActivityObservationPolicy.State.AVAILABLE,
            start,
            end,
            dates,
            listOf(
                HealthConnectActivityObservationPolicy.DayValue(dates[0], 1200.0, 1),
                HealthConnectActivityObservationPolicy.DayValue(dates[2], 0.0, 2),
                HealthConnectActivityObservationPolicy.DayValue(dates[6], 340.0, 1)
            ),
            intervalTotal = 1540.0,
            intervalSourceCount = 3
        )

        assertEquals(3, summary.observedDays)
        assertEquals(1540.0, summary.total!!, 0.0)
        assertEquals(3, summary.sourceCount)
        assertEquals(null, summary.days[1].value)
        assertEquals(0.0, summary.days[2].value!!, 0.0)
        assertEquals(0, summary.days[1].sourceCount)
        assertEquals(start, summary.windowStart)
        assertEquals(end, summary.windowEnd)
    }

    @Test
    fun stepRegularityUsesObservedDeduplicatedAggregateDaysWithoutTreatingMissingDaysAsZero() {
        val zone = ZoneId.of("Asia/Taipei")
        val dates = (6 downTo 0).map { java.time.LocalDate.parse("2026-09-28").minusDays(it.toLong()) }
        val daily = dates.take(3).mapIndexed { index, date ->
            HealthConnectActivityObservationPolicy.DayValue(date, listOf(2000.0, 4000.0, 3000.0)[index], 2)
        }
        val summary = HealthConnectActivityObservationPolicy.summarize(
            HealthConnectActivityObservationPolicy.State.AVAILABLE,
            dates.first().atStartOfDay(zone).toInstant(),
            Instant.parse("2026-09-28T12:00:00Z"),
            dates,
            daily,
            intervalTotal = 9000.0,
            intervalSourceCount = 2
        )

        assertEquals(listOf(2000L, 4000L, 3000L), HealthConnectActivityObservationPolicy.observedDailyStepCounts(summary))
        assertTrue(SensorHealthAlgorithms.activityRegularity(
            HealthConnectActivityObservationPolicy.observedDailyStepCounts(summary)
        ) != null)
        assertEquals(emptyList<Long>(), HealthConnectActivityObservationPolicy.observedDailyStepCounts(
            summary.copy(state = HealthConnectActivityObservationPolicy.State.READ_FAILED)
        ))
    }

    @Test
    fun aggregateActivitySummaryPreservesPermissionReadAndNoRecordStates() {
        val start = Instant.parse("2026-09-21T00:00:00Z")
        val end = Instant.parse("2026-09-28T00:00:00Z")
        val states = listOf(
            HealthConnectActivityObservationPolicy.State.PERMISSION_DENIED,
            HealthConnectActivityObservationPolicy.State.PERMISSION_STATE_FAILED,
            HealthConnectActivityObservationPolicy.State.READ_FAILED,
            HealthConnectActivityObservationPolicy.State.NO_RECORDS,
            HealthConnectActivityObservationPolicy.State.DEFERRED
        )

        states.forEach { state ->
            val summary = HealthConnectActivityObservationPolicy.summarize(
                state, start, end, emptyList(), emptyList(), null, 0
            )
            assertEquals(state, summary.state)
            assertNull(summary.total)
            assertEquals(0, summary.observedDays)
        }
        val emptyAggregate = HealthConnectActivityObservationPolicy.summarize(
            HealthConnectActivityObservationPolicy.State.AVAILABLE,
            start, end, emptyList(), emptyList(), null, 0
        )
        assertEquals(HealthConnectActivityObservationPolicy.State.NO_RECORDS, emptyAggregate.state)
        assertNull(emptyAggregate.total)
    }

    @Test
    fun aggregateActivitySummaryRequiresSourcesForTrueZeroAndRejectsMissingOrNegativeSourceCounts() {
        val dates = listOf(
            java.time.LocalDate.parse("2026-09-27"),
            java.time.LocalDate.parse("2026-09-28")
        )
        val summary = HealthConnectActivityObservationPolicy.summarize(
            HealthConnectActivityObservationPolicy.State.AVAILABLE,
            Instant.parse("2026-09-27T00:00:00Z"),
            Instant.parse("2026-09-28T12:00:00Z"),
            dates,
            listOf(
                HealthConnectActivityObservationPolicy.DayValue(dates[0], 0.0, 2),
                HealthConnectActivityObservationPolicy.DayValue(dates[1], 999.0, -1)
            ),
            intervalTotal = 0.0,
            intervalSourceCount = 2
        )

        assertEquals(HealthConnectActivityObservationPolicy.State.AVAILABLE, summary.state)
        assertEquals(1, summary.observedDays)
        assertEquals(0.0, summary.total!!, 0.0)
        assertEquals(0.0, summary.days[0].value!!, 0.0)
        assertEquals(2, summary.days[0].sourceCount)
        assertNull(summary.days[1].value)
        assertEquals(0, summary.days[1].sourceCount)

        val noSourceZero = HealthConnectActivityObservationPolicy.summarize(
            HealthConnectActivityObservationPolicy.State.AVAILABLE,
            summary.windowStart,
            summary.windowEnd,
            dates,
            listOf(HealthConnectActivityObservationPolicy.DayValue(dates[0], 0.0, 0)),
            intervalTotal = 0.0,
            intervalSourceCount = 0
        )
        assertEquals(HealthConnectActivityObservationPolicy.State.NO_RECORDS, noSourceZero.state)
        assertNull(noSourceZero.total)

        val invalidIntervalSources = HealthConnectActivityObservationPolicy.summarize(
            HealthConnectActivityObservationPolicy.State.AVAILABLE,
            summary.windowStart,
            summary.windowEnd,
            dates,
            emptyList(),
            intervalTotal = 12.0,
            intervalSourceCount = -1
        )
        assertEquals(HealthConnectActivityObservationPolicy.State.READ_FAILED, invalidIntervalSources.state)
        assertNull(invalidIntervalSources.total)
    }

    @Test
    fun dailyAggregateValuesRemainExplicitPartialSubtotalWhenIntervalAggregateFails() {
        val date = java.time.LocalDate.parse("2026-09-28")
        val summary = HealthConnectActivityObservationPolicy.summarize(
            HealthConnectActivityObservationPolicy.State.AVAILABLE,
            Instant.parse("2026-09-28T00:00:00Z"),
            Instant.parse("2026-09-28T12:00:00Z"),
            listOf(date),
            listOf(HealthConnectActivityObservationPolicy.DayValue(date, 42.0, 1)),
            intervalTotal = null,
            intervalSourceCount = 0
        )

        assertEquals(HealthConnectActivityObservationPolicy.State.PARTIAL, summary.state)
        assertEquals(42.0, summary.total!!, 0.0)
        assertEquals(1, summary.observedDays)
        assertEquals(0, summary.sourceCount)
        org.junit.Assert.assertTrue(summary.display("步數", "步", ZoneId.of("UTC")).contains("不可視為完整 7 日總量"))
    }

    @Test
    fun successfulDailyAggregateSurvivesIntervalQueryFailureAsPartial() {
        val date = java.time.LocalDate.parse("2026-09-28")
        val daily = listOf(HealthConnectActivityObservationPolicy.DayValue(date, 42.0, 1))
        val state = HealthConnectActivityObservationPolicy.stateAfterAggregateReads(
            HealthConnectActivityObservationPolicy.State.AVAILABLE,
            dailyReadFailed = false,
            intervalReadFailed = true,
            dailyRows = daily,
            intervalTotal = null,
            intervalSourceCount = 0
        )

        assertEquals(HealthConnectActivityObservationPolicy.State.PARTIAL, HealthConnectActivityObservationPolicy.summarize(
            state, Instant.parse("2026-09-28T00:00:00Z"), Instant.parse("2026-09-28T12:00:00Z"),
            listOf(date), daily, null, 0
        ).state)
        assertEquals(HealthConnectActivityObservationPolicy.State.READ_FAILED,
            HealthConnectActivityObservationPolicy.stateAfterAggregateReads(
                HealthConnectActivityObservationPolicy.State.AVAILABLE,
                dailyReadFailed = true,
                intervalReadFailed = true,
                dailyRows = emptyList(),
                intervalTotal = null,
                intervalSourceCount = 0
            ))
    }

    @Test
    fun successfulIntervalAggregateSurvivesDailyQueryFailureWithPartialBreakdown() {
        val date = java.time.LocalDate.parse("2026-09-28")
        val state = HealthConnectActivityObservationPolicy.stateAfterAggregateReads(
            HealthConnectActivityObservationPolicy.State.AVAILABLE,
            dailyReadFailed = true,
            intervalReadFailed = false,
            dailyRows = emptyList(),
            intervalTotal = 1234.0,
            intervalSourceCount = 1
        )
        val summary = HealthConnectActivityObservationPolicy.summarize(
            state, Instant.parse("2026-09-22T00:00:00Z"), Instant.parse("2026-09-29T00:00:00Z"),
            listOf(date), emptyList(), 1234.0, 1
        )

        assertEquals(HealthConnectActivityObservationPolicy.State.PARTIAL, summary.state)
        assertEquals(1234.0, summary.total!!, 0.0)
        assertEquals(1, summary.sourceCount)
        org.junit.Assert.assertTrue(summary.display("步數", "步", ZoneId.of("UTC")).contains("逐日明細部分未取得"))
    }

    @Test
    fun trimpBaselineMustBeLatestMeasurementEarlierOnExerciseLocalDate() {
        val zone = ZoneId.of("Asia/Taipei")
        val anchor = Instant.parse("2026-09-28T12:00:00Z")
        val values = listOf(
            HealthInsightInputPolicy.TimedSourceValue("previous-day", Instant.parse("2026-09-27T15:00:00Z"), "watch"),
            HealthInsightInputPolicy.TimedSourceValue("earlier", Instant.parse("2026-09-28T09:00:00Z"), "watch"),
            HealthInsightInputPolicy.TimedSourceValue("latest-before", Instant.parse("2026-09-28T11:00:00Z"), "watch"),
            HealthInsightInputPolicy.TimedSourceValue("after", Instant.parse("2026-09-28T13:00:00Z"), "watch")
        )

        assertEquals("latest-before", HealthInsightInputPolicy.latestSameDayBefore(values, anchor, zone)?.value)
        assertNull(HealthInsightInputPolicy.latestSameDayBefore(values.take(1), anchor, zone))
    }

    @Test
    fun trimpBaselinePrefersWorkoutHeartRateSourceWhenAvailable() {
        val zone = ZoneId.of("Asia/Taipei")
        val anchor = Instant.parse("2026-09-28T12:00:00Z")
        val values = listOf(
            HealthInsightInputPolicy.TimedSourceValue("latest-other", Instant.parse("2026-09-28T11:50:00Z"), "other"),
            HealthInsightInputPolicy.TimedSourceValue("matched-source", Instant.parse("2026-09-28T11:00:00Z"), "watch")
        )

        assertEquals(
            "matched-source",
            HealthInsightInputPolicy.latestSameDayBefore(values, anchor, zone, preferredSource = "watch")?.value
        )
        assertNull(HealthInsightInputPolicy.latestSameDayBefore(values, anchor, zone, preferredSource = "missing"))
    }

    @Test
    fun conflictingDailyAggregateBucketsAreWithheldWithoutDiscardingValidIntervalTotal() {
        val date = java.time.LocalDate.parse("2026-09-28")
        val summary = HealthConnectActivityObservationPolicy.summarize(
            state = HealthConnectActivityObservationPolicy.State.AVAILABLE,
            windowStart = Instant.parse("2026-09-28T00:00:00Z"),
            windowEnd = Instant.parse("2026-09-29T00:00:00Z"),
            dates = listOf(date),
            returnedDays = listOf(
                HealthConnectActivityObservationPolicy.DayValue(date, 100.0, 1),
                HealthConnectActivityObservationPolicy.DayValue(date, 140.0, 2)
            ),
            intervalTotal = 140.0,
            intervalSourceCount = 1
        )

        assertEquals(HealthConnectActivityObservationPolicy.State.PARTIAL, summary.state)
        assertEquals(140.0, summary.total!!, 0.0)
        assertEquals(null, summary.days.single().value)
        assertEquals(0, summary.observedDays)
    }

    @Test
    fun identicalDuplicateDailyAggregateBucketsCollapseWithoutInflatingCoverage() {
        val date = java.time.LocalDate.parse("2026-09-28")
        val summary = HealthConnectActivityObservationPolicy.summarize(
            state = HealthConnectActivityObservationPolicy.State.AVAILABLE,
            windowStart = Instant.parse("2026-09-28T00:00:00Z"),
            windowEnd = Instant.parse("2026-09-29T00:00:00Z"),
            dates = listOf(date),
            returnedDays = listOf(
                HealthConnectActivityObservationPolicy.DayValue(date, 100.0, 1, setOf("source-a")),
                HealthConnectActivityObservationPolicy.DayValue(date, 100.0, 1, setOf("source-a"))
            ),
            intervalTotal = 100.0,
            intervalSourceCount = 1
        )

        assertEquals(HealthConnectActivityObservationPolicy.State.AVAILABLE, summary.state)
        assertEquals(100.0, summary.days.single().value!!, 0.0)
        assertEquals(1, summary.observedDays)
    }

    @Test
    fun completeDailyBucketsThatDisagreeWithIntervalArePartialAndExcludedFromTrends() {
        val date = java.time.LocalDate.parse("2026-09-28")
        val summary = HealthConnectActivityObservationPolicy.summarize(
            state = HealthConnectActivityObservationPolicy.State.AVAILABLE,
            windowStart = Instant.parse("2026-09-28T00:00:00Z"),
            windowEnd = Instant.parse("2026-09-29T00:00:00Z"),
            dates = listOf(date),
            returnedDays = listOf(
                HealthConnectActivityObservationPolicy.DayValue(date, 100.0, 1, setOf("source-a"))
            ),
            intervalTotal = 140.0,
            intervalSourceCount = 1
        )

        assertEquals(HealthConnectActivityObservationPolicy.State.PARTIAL, summary.state)
        assertEquals(true, summary.dailyIntervalMismatch)
        assertEquals(140.0, summary.total!!, 0.0)
        assertEquals(emptyList<Long>(), HealthConnectActivityObservationPolicy.observedDailyStepCounts(summary))
        assertTrue(summary.display("步數", "步", ZoneId.of("UTC")).contains("日加總與區間 aggregate 不一致"))
    }

    @Test
    fun incompleteDailyCoverageDoesNotTreatMissingDaysAsZeroOrFlagMismatch() {
        val first = java.time.LocalDate.parse("2026-09-27")
        val second = first.plusDays(1)
        val summary = HealthConnectActivityObservationPolicy.summarize(
            state = HealthConnectActivityObservationPolicy.State.AVAILABLE,
            windowStart = Instant.parse("2026-09-27T00:00:00Z"),
            windowEnd = Instant.parse("2026-09-29T00:00:00Z"),
            dates = listOf(first, second),
            returnedDays = listOf(HealthConnectActivityObservationPolicy.DayValue(first, 100.0, 1)),
            intervalTotal = 140.0,
            intervalSourceCount = 1
        )

        assertEquals(HealthConnectActivityObservationPolicy.State.AVAILABLE, summary.state)
        assertEquals(false, summary.dailyIntervalMismatch)
        assertEquals(1, summary.observedDays)
    }

    @Test
    fun duplicateRequestedDatesDoNotInflateDailyCoverage() {
        val date = java.time.LocalDate.parse("2026-09-28")
        val summary = HealthConnectActivityObservationPolicy.summarize(
            state = HealthConnectActivityObservationPolicy.State.AVAILABLE,
            windowStart = Instant.parse("2026-09-28T00:00:00Z"),
            windowEnd = Instant.parse("2026-09-29T00:00:00Z"),
            dates = listOf(date, date),
            returnedDays = listOf(
                HealthConnectActivityObservationPolicy.DayValue(date, 100.0, 1, setOf("source-a"))
            ),
            intervalTotal = 100.0,
            intervalSourceCount = 1
        )

        assertEquals(1, summary.days.size)
        assertEquals(1, summary.observedDays)
        assertEquals(listOf(date), summary.days.map { it.date })
    }

    @Test
    fun stressInputsRequireSameLocalDateAndSource() {
        val zone = ZoneId.of("Asia/Taipei")
        val resting = listOf(
            HealthInsightInputPolicy.TimedSourceValue("yesterday", Instant.parse("2026-09-26T03:00:00Z"), "watch"),
            HealthInsightInputPolicy.TimedSourceValue("old", Instant.parse("2026-09-26T23:30:00Z"), "watch"),
            HealthInsightInputPolicy.TimedSourceValue("today", Instant.parse("2026-09-27T02:00:00Z"), "watch"),
            HealthInsightInputPolicy.TimedSourceValue("other-source", Instant.parse("2026-09-27T04:00:00Z"), "other")
        )
        val hrv = listOf(
            HealthInsightInputPolicy.TimedSourceValue(5, Instant.parse("2026-09-26T03:05:00Z"), "watch"),
            HealthInsightInputPolicy.TimedSourceValue(10, Instant.parse("2026-09-26T23:45:00Z"), "watch"),
            HealthInsightInputPolicy.TimedSourceValue(20, Instant.parse("2026-09-27T03:00:00Z"), "watch")
        )

        val pair = HealthInsightInputPolicy.latestSameDayPair(resting, hrv, zone)

        assertEquals("old", pair?.first?.value)
        assertEquals(10, pair?.second?.value)
        assertNull(HealthInsightInputPolicy.latestSameDayPair(resting.takeLast(1), hrv, zone))
    }

    @Test
    fun halfOpenIntervalEndingAtLocalMidnightBelongsToPreviousDay() {
        val zone = ZoneId.of("Asia/Taipei")
        val midnight = java.time.LocalDate.parse("2026-09-28").atStartOfDay(zone).toInstant()

        assertEquals(false, HealthInsightInputPolicy.crossesLocalDate(midnight.minusSeconds(900), midnight, zone))
        assertEquals(true, HealthInsightInputPolicy.crossesLocalDate(midnight.minusSeconds(900), midnight.plusNanos(1), zone))
    }

    @Test
    fun recordedSleepSubtractsUnionOfAwakeStagesAndClipsWindow() {
        val sessionStart = Instant.parse("2026-09-27T20:00:00Z")
        val sessionEnd = Instant.parse("2026-09-28T04:00:00Z")
        val sessions = listOf(
            SleepSessionValue(
                sessionStart,
                sessionEnd,
                listOf(
                    SleepAwakeInterval(Instant.parse("2026-09-27T22:00:00Z"), Instant.parse("2026-09-27T23:00:00Z")),
                    SleepAwakeInterval(Instant.parse("2026-09-27T22:30:00Z"), Instant.parse("2026-09-27T23:30:00Z"))
                ),
                hasStageData = true,
                stageIntervals = listOf(
                    SleepAwakeInterval(sessionStart, Instant.parse("2026-09-27T22:00:00Z")),
                    SleepAwakeInterval(Instant.parse("2026-09-27T22:00:00Z"), Instant.parse("2026-09-27T23:30:00Z")),
                    SleepAwakeInterval(Instant.parse("2026-09-27T23:30:00Z"), sessionEnd)
                )
            )
        )

        assertEquals(6.5, HealthInsightInputPolicy.sleepHours(sessions, sessionStart, sessionEnd)!!, 0.0001)
        assertEquals(4.0, HealthInsightInputPolicy.sleepHours(
            sessions,
            Instant.parse("2026-09-28T00:00:00Z"),
            sessionEnd
        )!!, 0.0001)
    }

    @Test
    fun incompleteSleepStagesAreUnknownRatherThanZeroHours() {
        val start = Instant.parse("2026-09-27T20:00:00Z")
        val end = start.plusSeconds(8 * 60 * 60L)
        val session = SleepSessionValue(start, end, hasStageData = false)

        assertEquals(null, HealthInsightInputPolicy.sleepHoursWhenStagesComplete(false, listOf(session), start, end))
    }

    @Test
    fun sleepStageCoverageRejectsMissingGapsAndUnknownStages() {
        val start = Instant.parse("2026-09-27T20:00:00Z")
        val middle = start.plusSeconds(3600)
        val end = middle.plusSeconds(3600)
        val complete = SleepSessionValue(
            start,
            end,
            hasStageData = true,
            stageIntervals = listOf(SleepAwakeInterval(start, middle), SleepAwakeInterval(middle, end)),
            stageTypesKnown = true
        )
        val gap = complete.copy(stageIntervals = listOf(SleepAwakeInterval(start, middle.minusNanos(1))))
        val unknown = complete.copy(stageTypesKnown = false)

        assertEquals(true, HealthInsightInputPolicy.sleepStagesCoverSessions(listOf(complete), start, end))
        assertEquals(false, HealthInsightInputPolicy.sleepStagesCoverSessions(listOf(gap), start, end))
        assertEquals(false, HealthInsightInputPolicy.sleepStagesCoverSessions(listOf(unknown), start, end))
    }

    @Test
    fun sleepStageCoverageRejectsOverlapsAndStagesOutsideSession() {
        val start = Instant.parse("2026-09-27T20:00:00Z")
        val middle = start.plusSeconds(3600)
        val end = middle.plusSeconds(3600)
        val valid = SleepSessionValue(
            start,
            end,
            hasStageData = true,
            stageIntervals = listOf(SleepAwakeInterval(start, middle), SleepAwakeInterval(middle, end)),
            stageTypesKnown = true
        )
        val overlapping = valid.copy(stageIntervals = listOf(
            SleepAwakeInterval(start, middle.plusSeconds(1)),
            SleepAwakeInterval(middle, end)
        ))
        val outside = valid.copy(stageIntervals = listOf(
            SleepAwakeInterval(start.minusSeconds(1), middle),
            SleepAwakeInterval(middle, end)
        ))

        assertEquals(true, HealthInsightInputPolicy.sleepStagesCoverSessions(listOf(valid), start, end))
        assertEquals(false, HealthInsightInputPolicy.sleepStagesCoverSessions(listOf(overlapping), start, end))
        assertEquals(false, HealthInsightInputPolicy.sleepStagesCoverSessions(listOf(outside), start, end))
    }
}
