package com.jaysato.xiaomihealthbridge

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgorithmicInsightProvenanceTest {
    private val start = Instant.parse("2026-09-28T10:00:00Z")
    private val end = start.plusSeconds(600)

    @Test
    fun metricRecordsRequireOneKnownOrigin() {
        assertTrue(AlgorithmicInsightProvenance.hasSingleOrigin(listOf("watch.app", "watch.app")))
        assertFalse(AlgorithmicInsightProvenance.hasSingleOrigin(emptyList()))
        assertFalse(AlgorithmicInsightProvenance.hasSingleOrigin(listOf("watch.app", "phone.app")))
        assertFalse(AlgorithmicInsightProvenance.hasSingleOrigin(listOf("watch.app", "")))
    }

    @Test
    fun compositeScoresRequireEveryInputToShareOneKnownOrigin() {
        assertTrue(AlgorithmicInsightProvenance.sameOrigin("watch.app", "watch.app", "watch.app"))
        assertFalse(AlgorithmicInsightProvenance.sameOrigin("watch.app", "phone.app", "watch.app"))
        assertFalse(AlgorithmicInsightProvenance.sameOrigin("watch.app", null, "watch.app"))
        assertFalse(AlgorithmicInsightProvenance.sameOrigin("watch.app", "", "watch.app"))
    }

    @Test
    fun heartRateReserveActivityAllowsTimestampPairedMeasurementsFromKnownDifferentSources() {
        assertTrue(HealthInsightCompositeInputPolicy.canComputeHeartRateReserveEquivalentMinutes(true, true, true, true))
        assertFalse(HealthInsightCompositeInputPolicy.canComputeHeartRateReserveEquivalentMinutes(true, true, false, true))
        assertFalse(HealthInsightCompositeInputPolicy.canComputeHeartRateReserveEquivalentMinutes(false, true, true, true))
        assertFalse(HealthInsightCompositeInputPolicy.canComputeHeartRateReserveEquivalentMinutes(true, false, true, true))
        assertFalse(HealthInsightCompositeInputPolicy.canComputeHeartRateReserveEquivalentMinutes(true, true, true, false))
    }

    @Test
    fun selectsOneMostCompleteSourceInsteadOfDroppingAllSources() {
        data class Sample(val source: String, val count: Int, val time: Instant)
        val selected = AlgorithmicInsightProvenance.selectMostCompleteOrigin(
            listOf(
                Sample("watch", 2, start),
                Sample("phone", 4, end),
                Sample("phone", 3, start)
            ),
            origin = { it.source },
            completeness = { it.count },
            measuredAt = { it.time }
        )
        assertEquals("phone", selected?.origin)
        assertEquals(2, selected?.records?.size)
    }

    @Test
    fun sourceSelectionRejectsUnknownOriginsAndBreaksTiesByRecency() {
        data class Sample(val source: String, val count: Int, val time: Instant)
        val selected = AlgorithmicInsightProvenance.selectMostCompleteOrigin(
            listOf(
                Sample("", 9, end),
                Sample("watch", 2, start),
                Sample("phone", 2, end)
            ),
            origin = { it.source },
            completeness = { it.count },
            measuredAt = { it.time }
        )
        assertEquals("phone", selected?.origin)
    }

    @Test
    fun sourceSelectionWithholdsExactCompletenessAndRecencyTies() {
        data class Sample(val source: String, val count: Int, val time: Instant)
        val selected = AlgorithmicInsightProvenance.selectMostCompleteOrigin(
            listOf(
                Sample("watch", 2, end),
                Sample("phone", 2, end)
            ),
            origin = { it.source },
            completeness = { it.count },
            measuredAt = { it.time }
        )

        assertNull(selected)
    }

    @Test
    fun intervalSelectionFallsBackWhenMostCompleteOriginOverlaps() {
        data class Interval(val source: String, val start: Instant, val end: Instant)
        val records = listOf(
            Interval("watch", start, start.plusSeconds(400)),
            Interval("watch", start.plusSeconds(300), end),
            Interval("phone", start, end)
        )
        val selected = AlgorithmicInsightProvenance.selectMostCompleteUnambiguousOrigin(
            records,
            origin = { it.source },
            completeness = { 1 },
            measuredAt = { it.end },
            toMeasurement = { SourceIntervalMeasurement(it.source, it.start, it.end, 1.0) }
        )

        assertEquals("phone", selected?.origin)
        assertEquals(1, selected?.records?.size)
    }

    @Test
    fun intervalSelectionRejectsExactCompletenessAndRecencyTieAcrossOrigins() {
        data class Interval(val source: String, val start: Instant, val end: Instant)
        val records = listOf(
            Interval("watch", start, end),
            Interval("phone", start, end)
        )

        val selected = AlgorithmicInsightProvenance.selectMostCompleteUnambiguousOrigin(
            records,
            origin = { it.source },
            completeness = { 600 },
            measuredAt = { it.end },
            toMeasurement = { SourceIntervalMeasurement(it.source, it.start, it.end, 1.0) }
        )

        assertNull(selected)
    }

    @Test
    fun intervalCompletenessTotalsDoNotOverflowInt() {
        data class Interval(val source: String, val start: Instant, val end: Instant)
        val middle = start.plusSeconds(300)
        val records = listOf(
            Interval("watch", start, middle),
            Interval("watch", middle, end),
            Interval("phone", start, end)
        )

        val selected = AlgorithmicInsightProvenance.selectMostCompleteUnambiguousOrigin(
            records,
            origin = { it.source },
            completeness = { if (it.source == "watch") Int.MAX_VALUE else 2 },
            measuredAt = { it.end },
            toMeasurement = { SourceIntervalMeasurement(it.source, it.start, it.end, 1.0) }
        )

        assertEquals("watch", selected?.origin)
    }

    @Test
    fun intervalSourceSelectionRanksCoveredTimeAheadOfRecordCount() {
        data class Interval(val source: String, val start: Instant, val end: Instant)
        val records = listOf(
            Interval("watch", start, start.plusSeconds(100)),
            Interval("watch", start.plusSeconds(100), start.plusSeconds(200)),
            Interval("phone", start, end)
        )
        val selected = AlgorithmicInsightProvenance.selectMostCompleteUnambiguousOrigin(
            records,
            origin = { it.source },
            completeness = { java.time.Duration.between(it.start, it.end).seconds.toInt() },
            measuredAt = { it.end },
            toMeasurement = { SourceIntervalMeasurement(it.source, it.start, it.end, 1.0) }
        )

        assertEquals("phone", selected?.origin)
        assertEquals(600L, selected?.records?.sumOf { java.time.Duration.between(it.start, it.end).seconds })
    }

    @Test
    fun workoutHeartRateRequiresAtLeastTwoSamplesInsideHalfOpenSessionWindow() {
        assertTrue(
            AlgorithmicInsightProvenance.hasPairedSamples(
                start,
                end,
                listOf(start, start.plusSeconds(30), end)
            )
        )
        assertFalse(AlgorithmicInsightProvenance.hasPairedSamples(start, end, listOf(start, end)))
        assertFalse(AlgorithmicInsightProvenance.hasPairedSamples(start, end, listOf(start.minusSeconds(1), end)))
        assertFalse(AlgorithmicInsightProvenance.hasPairedSamples(end, start, listOf(start, end)))
    }
}
