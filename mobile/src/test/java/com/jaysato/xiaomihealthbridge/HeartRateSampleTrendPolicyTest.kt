package com.jaysato.xiaomihealthbridge

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateSampleTrendPolicyTest {
    private val start = Instant.parse("2026-10-09T00:00:00Z")
    private val end = start.plusSeconds(3600)
    private fun sample(origin: String, seconds: Long, bpm: Double) =
        HeartRateSampleTrendPolicy.Sample(origin, start.plusSeconds(seconds), bpm)

    @Test fun differentSampleTimesInOneOriginCanFormTrendDespitePackedRecordWindows() {
        val assessment = HeartRateSampleTrendPolicy.assess(
            listOf(sample("mi.fitness", 60, 72.0), sample("mi.fitness", 120, 75.0)), start, end
        )
        assertTrue(assessment.eligible)
    }

    @Test fun distinctSourcesRemainSeparate() {
        val assessment = HeartRateSampleTrendPolicy.assess(
            listOf(sample("mi.fitness", 60, 72.0), sample("other", 120, 75.0)), start, end
        )
        assertEquals(SourceIntervalAggregation.Rejection.MULTIPLE_DATA_ORIGINS, assessment.rejection)
    }

    @Test fun duplicateTimestampWithinSourceCannotBeMerged() {
        val assessment = HeartRateSampleTrendPolicy.assess(
            listOf(sample("mi.fitness", 60, 72.0), sample("mi.fitness", 60, 75.0)), start, end
        )
        assertEquals(SourceIntervalAggregation.Rejection.DUPLICATE_SAMPLE_TIMESTAMPS, assessment.rejection)
    }

    @Test fun invalidAndOutOfRangeSamplesAreNotTrendInputs() {
        val assessment = HeartRateSampleTrendPolicy.assess(
            listOf(sample("mi.fitness", -1, 72.0), sample("mi.fitness", 60, 72.0), sample("mi.fitness", 120, 300.0)), start, end
        )
        assertTrue(assessment.eligible)
    }

    @Test fun noUsableSampleHasExplicitReason() {
        val assessment = HeartRateSampleTrendPolicy.assess(
            listOf(sample("mi.fitness", 60, Double.NaN)), start, end
        )
        assertEquals(SourceIntervalAggregation.Rejection.INVALID_VALUE, assessment.rejection)
    }
}
