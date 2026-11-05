package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResearchMetricResolverTest {
    @Test fun measuredFreshValueWinsOverResearchFallback() {
        val result = ResearchMetricResolver.resolve(
            listOf(
                ResearchMetricCandidate(72.0, "PPG_RESEARCH", 1_000L, "ESTIMATED", 80, true),
                ResearchMetricCandidate(70.0, "HEALTH_SERVICES", 1_000L,
                    HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, 90, false)
            ), 1_001L
        )
        assertEquals(70.0, result.value!!, 0.0)
        assertEquals(ResearchMetricState.MEASURED, result.state)
    }

    @Test fun staleValueIsStillDisplayedAsHistorical() {
        val result = ResearchMetricResolver.resolve(
            listOf(ResearchMetricCandidate(97.0, "RAW_SPO2", 1L, "android_sensor_event_elapsed_realtime", 55, true)),
            2 * 60 * 60 * 1000L
        )
        assertEquals(ResearchMetricState.HISTORICAL, result.state)
        assertEquals("VALUE_PRESENT_BUT_NOT_FRESH", result.reason)
    }

    @Test fun freshResearchValueIsNotMaskedByStaleMeasuredValue() {
        val result = ResearchMetricResolver.resolve(
            listOf(
                ResearchMetricCandidate(70.0, "HEALTH_SERVICES", 1L, "SENSOR", 90, false),
                ResearchMetricCandidate(72.0, "PPG_RESEARCH", 9_500L,
                    "android_sensor_event_elapsed_realtime", 75, true)
            ),
            nowMillis = 10_000L,
            freshnessMillis = 2_000L
        )

        assertEquals(72.0, result.value!!, 0.0)
        assertEquals("PPG_RESEARCH", result.source)
        assertEquals(ResearchMetricState.RESEARCH_ESTIMATE, result.state)
        assertEquals("HIGHEST_PRIORITY_FRESH_SOURCE", result.reason)
    }

    @Test fun freshMeasuredValueStillWinsOverNewerResearchEstimate() {
        val result = ResearchMetricResolver.resolve(
            listOf(
                ResearchMetricCandidate(72.0, "PPG_RESEARCH", 9_900L, "ESTIMATED", 80, true),
                ResearchMetricCandidate(70.0, "HEALTH_SERVICES", 9_000L,
                    HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, 90, false)
            ),
            nowMillis = 10_000L,
            freshnessMillis = 2_000L
        )

        assertEquals(70.0, result.value!!, 0.0)
        assertEquals(ResearchMetricState.MEASURED, result.state)
    }

    @Test fun invalidCandidatesDoNotBecomeZero() {
        val result = ResearchMetricResolver.resolve(
            listOf(ResearchMetricCandidate(null, "NONE", 0L, null, 0, true)), 1L
        )
        assertNull(result.value)
        assertEquals(ResearchMetricState.UNAVAILABLE, result.state)
    }

    @Test fun untrustedTimeIsNotMisclassifiedAsHistorical() {
        listOf(null, "UNKNOWN", "ESTIMATED", "callback_receipt_time").forEach { timestampSource ->
            val result = ResearchMetricResolver.resolve(
                listOf(ResearchMetricCandidate(72.0, "PPG_RESEARCH", 9_500L, timestampSource, 75, true)),
                nowMillis = 10_000L,
                freshnessMillis = 2_000L
            )

            assertEquals(ResearchMetricState.TIME_UNVERIFIED, result.state)
            assertEquals("TIMESTAMP_SOURCE_UNTRUSTED", result.reason)
        }
    }

    @Test fun futureTimestampDoesNotMaskAvailablePastHistoricalValue() {
        val result = ResearchMetricResolver.resolve(
            listOf(
                ResearchMetricCandidate(80.0, "FUTURE", 20_000L, "UNKNOWN", 80, false),
                ResearchMetricCandidate(70.0, "PAST", 1_000L, "UNKNOWN", 70, false)
            ),
            nowMillis = 10_000L
        )

        assertEquals(70.0, result.value!!, 0.0)
        assertEquals("PAST", result.source)
        assertEquals(ResearchMetricState.TIME_UNVERIFIED, result.state)
    }

    @Test fun futureTimestampIsInvalidInsteadOfHistorical() {
        val result = ResearchMetricResolver.resolve(
            listOf(ResearchMetricCandidate(80.0, "FUTURE", 20_000L, "UNKNOWN", 80, false)),
            nowMillis = 10_000L
        )

        assertEquals(ResearchMetricState.TIME_INVALID, result.state)
        assertEquals("FUTURE_TIMESTAMP", result.reason)
    }

    @Test fun missingTimestampIsUnverifiedInsteadOfHistorical() {
        val result = ResearchMetricResolver.resolve(
            listOf(ResearchMetricCandidate(72.0, "PPG_RESEARCH", 0L, null, 75, true)),
            nowMillis = 10_000L
        )

        assertEquals(ResearchMetricState.TIME_UNVERIFIED, result.state)
        assertEquals("TIMESTAMP_MISSING", result.reason)
    }
}
