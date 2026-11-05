package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateDailySummaryPolicyTest {
    @Test
    fun summarizesOnlyFinitePlausibleSamplesWithKnownSources() {
        val summary = HeartRateDailySummaryPolicy.summarize(
            listOf(
                HeartRateDailySummaryPolicy.Sample(100L, 55.0, "watch"),
                HeartRateDailySummaryPolicy.Sample(200L, 104.0, "phone"),
                HeartRateDailySummaryPolicy.Sample(300L, Double.NaN, "watch"),
                HeartRateDailySummaryPolicy.Sample(400L, 72.0, ""),
                HeartRateDailySummaryPolicy.Sample(0L, 60.0, "watch"),
                HeartRateDailySummaryPolicy.Sample(500L, 0.0, "watch")
            )
        )!!
        assertEquals(55.0, summary.minimumBpm, 0.0)
        assertEquals(104.0, summary.maximumBpm, 0.0)
        assertEquals(2, summary.sampleCount)
        assertEquals(2, summary.sourceCount)
    }

    @Test
    fun emptyOrEntirelyInvalidInputsHaveNoSummary() {
        assertNull(HeartRateDailySummaryPolicy.summarize(emptyList()))
        assertNull(HeartRateDailySummaryPolicy.summarize(listOf(
            HeartRateDailySummaryPolicy.Sample(1L, 230.0, "watch")
        )))
    }

    @Test
    fun summarizesOnlySamplesInsideInclusiveDayWindow() {
        val summary = HeartRateDailySummaryPolicy.summarizeWindow(
            listOf(
                HeartRateDailySummaryPolicy.Sample(99L, 45.0, "yesterday"),
                HeartRateDailySummaryPolicy.Sample(100L, 60.0, "watch"),
                HeartRateDailySummaryPolicy.Sample(150L, 90.0, "watch"),
                HeartRateDailySummaryPolicy.Sample(201L, 180.0, "future")
            ),
            startInclusiveMillis = 100L,
            endInclusiveMillis = 200L
        )!!

        assertEquals(60.0, summary.minimumBpm, 0.0)
        assertEquals(90.0, summary.maximumBpm, 0.0)
        assertEquals(2, summary.sampleCount)
    }

    @Test
    fun invalidDayWindowHasNoSummary() {
        assertNull(
            HeartRateDailySummaryPolicy.summarizeWindow(
                listOf(HeartRateDailySummaryPolicy.Sample(100L, 70.0, "watch")),
                startInclusiveMillis = 200L,
                endInclusiveMillis = 100L
            )
        )
    }

    @Test
    fun latestRestingObservationUsesMeasurementTimeAndRejectsInvalidRows() {
        val latest = HeartRateDailySummaryPolicy.latestResting(listOf(
            HeartRateDailySummaryPolicy.Sample(100L, 61.0, "older"),
            HeartRateDailySummaryPolicy.Sample(200L, 58.0, "newer"),
            HeartRateDailySummaryPolicy.Sample(300L, Double.POSITIVE_INFINITY, "invalid")
        ))!!
        assertEquals(200L, latest.timeMillis)
        assertEquals(58.0, latest.bpm, 0.0)
        assertEquals("newer", latest.source)
    }

    @Test
    fun accumulatorKeepsLatestValidMeasurementAcrossPages() {
        val accumulator = HeartRateDailySummaryPolicy.Accumulator()
        accumulator.add(HeartRateDailySummaryPolicy.Sample(200L, 75.0, "watch"))
        accumulator.add(HeartRateDailySummaryPolicy.Sample(300L, Double.NaN, "invalid"))
        accumulator.add(HeartRateDailySummaryPolicy.Sample(100L, 68.0, "phone"))
        assertEquals(200L, accumulator.latestSample()?.timeMillis)
        assertEquals(75.0, accumulator.latestSample()?.bpm ?: Double.NaN, 0.0)
    }
}
