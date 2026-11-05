package com.jaysato.xiaomihealthbridge

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceIntervalAggregationTest {
    private val start = Instant.parse("2026-09-28T00:00:00Z")

    @Test
    fun nonOverlappingIntervalsFromOneOriginAreEligible() {
        val result = SourceIntervalAggregation.assess(
            listOf(
                measurement("watch.app", start, start.plusSeconds(60)),
                measurement("watch.app", start.plusSeconds(60), start.plusSeconds(120))
            )
        )

        assertTrue(result.eligible)
        assertNull(result.rejection)
    }

    @Test
    fun measuredZeroIsValidWhenItHasAValidSourceAndInterval() {
        val records = listOf(measurement("watch.app", start, start.plusSeconds(60), 0.0))
        val result = SourceIntervalAggregation.assess(records)

        assertTrue(result.eligible)
        assertEquals(0.0, SourceIntervalAggregation.sumOrNull(records)!!, 0.0)
        assertNull(SourceIntervalAggregation.sumOrNull(emptyList()))
    }

    @Test
    fun multipleOriginsAreNotSilentlySummedEvenWhenIntervalsMatch() {
        val result = SourceIntervalAggregation.assess(
            listOf(
                measurement("watch.app", start, start.plusSeconds(60), 100.0),
                measurement("phone.app", start, start.plusSeconds(60), 100.0)
            )
        )

        assertFalse(result.eligible)
        assertEquals(SourceIntervalAggregation.Rejection.MULTIPLE_DATA_ORIGINS, result.rejection)
    }

    @Test
    fun overlappingIntervalsFromOneOriginAreNotSilentlySummed() {
        val result = SourceIntervalAggregation.assess(
            listOf(
                measurement("watch.app", start, start.plusSeconds(90)),
                measurement("watch.app", start.plusSeconds(60), start.plusSeconds(120))
            )
        )

        assertFalse(result.eligible)
        assertEquals(SourceIntervalAggregation.Rejection.OVERLAPPING_INTERVALS, result.rejection)
    }

    @Test
    fun exactRepeatedIntervalFromOneOriginIsNotSilentlyDeduplicated() {
        val result = SourceIntervalAggregation.assess(
            listOf(
                measurement("watch.app", start, start.plusSeconds(60)),
                measurement("watch.app", start, start.plusSeconds(60))
            )
        )

        assertFalse(result.eligible)
        assertEquals(SourceIntervalAggregation.Rejection.OVERLAPPING_INTERVALS, result.rejection)
    }

    @Test
    fun emptyInputIsNotTreatedAsAValidZeroTotal() {
        val result = SourceIntervalAggregation.assess(emptyList())

        assertFalse(result.eligible)
        assertEquals(SourceIntervalAggregation.Rejection.NO_RECORDS, result.rejection)
    }

    @Test
    fun invalidIntervalAndValueAreRejected() {
        val reversed = SourceIntervalAggregation.assess(
            listOf(measurement("watch.app", start.plusSeconds(60), start))
        )
        val nonFinite = SourceIntervalAggregation.assess(
            listOf(measurement("watch.app", start, start.plusSeconds(60), Double.NaN))
        )
        val infinite = SourceIntervalAggregation.assess(
            listOf(measurement("watch.app", start, start.plusSeconds(60), Double.POSITIVE_INFINITY))
        )
        val negative = SourceIntervalAggregation.assess(
            listOf(measurement("watch.app", start, start.plusSeconds(60), -1.0))
        )
        val zeroDuration = SourceIntervalAggregation.assess(
            listOf(measurement("watch.app", start, start))
        )

        assertEquals(SourceIntervalAggregation.Rejection.INVALID_INTERVAL, reversed.rejection)
        assertEquals(SourceIntervalAggregation.Rejection.INVALID_INTERVAL, zeroDuration.rejection)
        assertEquals(SourceIntervalAggregation.Rejection.INVALID_VALUE, nonFinite.rejection)
        assertEquals(SourceIntervalAggregation.Rejection.INVALID_VALUE, infinite.rejection)
        assertEquals(SourceIntervalAggregation.Rejection.INVALID_VALUE, negative.rejection)
    }

    @Test
    fun twoMetricsCanBeCombinedOnlyWhenSourceAndTimeCoverageMatch() {
        val same = listOf(measurement("watch.app", start, start.plusSeconds(60)))
        val differentOrigin = listOf(measurement("phone.app", start, start.plusSeconds(60)))
        val differentCoverage = listOf(measurement("watch.app", start.plusSeconds(1), start.plusSeconds(60)))

        assertTrue(SourceIntervalAggregation.hasEquivalentCoverage(same, same))
        assertFalse(SourceIntervalAggregation.hasEquivalentCoverage(same, differentOrigin))
        assertFalse(SourceIntervalAggregation.hasEquivalentCoverage(same, differentCoverage))
    }

    @Test
    fun matchingCoverageAcrossMultipleRecordsDoesNotThrow() {
        val middle = start.plusSeconds(60)
        val end = middle.plusSeconds(60)
        val stepIntervals = listOf(
            measurement("watch.app", start, middle),
            measurement("watch.app", middle, end)
        )
        val distanceIntervals = listOf(
            measurement("watch.app", start, middle),
            measurement("watch.app", middle, end)
        )

        assertTrue(SourceIntervalAggregation.hasEquivalentCoverage(stepIntervals, distanceIntervals))
    }

    @Test
    fun dailyTotalsRequireAnObservedBucketInsteadOfTreatingMissingDaysAsZero() {
        assertNull(DailyObservedAggregation.sumCompleteBucketsOrNull(listOf(listOf(12.0), emptyList())))
        assertEquals(
            listOf(12.0, 0.0),
            DailyObservedAggregation.sumCompleteBucketsOrNull(listOf(listOf(12.0), listOf(0.0)))
        )
    }

    @Test
    fun aBucketIsCompleteOnlyWhenIntervalsCoverItsEntireRangeWithoutGaps() {
        val end = start.plusSeconds(3600)
        val covered = listOf(
            measurement("watch.app", start, start.plusSeconds(1800), 10.0),
            measurement("watch.app", start.plusSeconds(1800), end, 10.0)
        )
        val gap = listOf(
            measurement("watch.app", start, start.plusSeconds(1800), 10.0),
            measurement("watch.app", start.plusSeconds(1801), end, 10.0)
        )
        val partial = listOf(measurement("watch.app", start, end.minusSeconds(1), 10.0))

        assertTrue(IntervalBucketAggregation.coversRange(covered, start, end))
        assertFalse(IntervalBucketAggregation.coversRange(gap, start, end))
        assertFalse(IntervalBucketAggregation.coversRange(partial, start, end))
    }

    @Test
    fun exactCoverageSupportsLocalDaysAcrossDaylightSavingChanges() {
        val zone = java.time.ZoneId.of("America/New_York")
        val springStart = java.time.LocalDate.parse("2026-03-08").atStartOfDay(zone).toInstant()
        val springEnd = java.time.LocalDate.parse("2026-03-09").atStartOfDay(zone).toInstant()
        val fallStart = java.time.LocalDate.parse("2026-11-01").atStartOfDay(zone).toInstant()
        val fallEnd = java.time.LocalDate.parse("2026-11-02").atStartOfDay(zone).toInstant()

        assertEquals(23L, java.time.Duration.between(springStart, springEnd).toHours())
        assertEquals(25L, java.time.Duration.between(fallStart, fallEnd).toHours())
        assertTrue(IntervalBucketAggregation.coversRange(listOf(measurement("watch.app", springStart, springEnd)), springStart, springEnd))
        assertTrue(IntervalBucketAggregation.coversRange(listOf(measurement("watch.app", fallStart, fallEnd)), fallStart, fallEnd))
    }

    @Test
    fun intervalsAreNeverProratedAcrossChartBuckets() {
        val nextHour = start.plusSeconds(3600)
        val intervals = listOf(
            measurement("watch.app", start, start.plusSeconds(1800), 20.0),
            measurement("watch.app", start.plusSeconds(1800), nextHour, 30.0),
            measurement("watch.app", nextHour.minusSeconds(30), nextHour.plusSeconds(30), 99.0)
        )

        val result = IntervalBucketAggregation.sumWholeIntervals(
            intervals,
            bucketStart = { value -> if (value < nextHour) start else nextHour },
            bucketEnd = { value -> value.plusSeconds(3600) }
        )

        assertEquals(listOf(50.0), result.buckets.map { it.value })
        assertEquals(1, result.excludedCrossBucketIntervals)
    }

    @Test
    fun intervalAtExactBucketEndBelongsOnlyToTheNextBucket() {
        val nextHour = start.plusSeconds(3600)
        val result = IntervalBucketAggregation.sumWholeIntervals(
            listOf(measurement("watch.app", nextHour, nextHour.plusSeconds(60), 7.0)),
            bucketStart = { value -> if (value < nextHour) start else nextHour },
            bucketEnd = { value -> value.plusSeconds(3600) }
        )

        assertEquals(listOf(IntervalBucketAggregation.Bucket(nextHour, 7.0)), result.buckets)
        assertEquals(0, result.excludedCrossBucketIntervals)
    }

    private fun measurement(
        origin: String,
        startTime: Instant,
        endTime: Instant,
        value: Double = 10.0
    ) = SourceIntervalMeasurement(origin, startTime, endTime, value)
}
