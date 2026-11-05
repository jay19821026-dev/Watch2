package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HealthConnectAggregatePolicyTest {
    @Test
    fun invalidRecordTotalsCannotPassInfiniteOrTinyTolerance() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.0000001).forEach { total ->
            assertFalse(HealthConnectAggregatePolicy.acceptsSingleOriginRecordAggregate(
                0.0, listOf("manual.app"), total, listOf("manual.app")
            ))
        }
        assertTrue(HealthConnectAggregatePolicy.acceptsSingleOriginRecordAggregate(
            0.0, listOf("manual.app"), 0.0, listOf("manual.app")
        ))
    }

    @Test
    fun platformAggregatesAreNotInvalidatedByRawOverlapOrMultipleOrigins() {
        assertTrue(HealthConnectAggregatePolicy.acceptsPlatformTotal(12_345.0, listOf("watch.app", "phone.app")))
        assertTrue(HealthConnectAggregatePolicy.acceptsPlatformTotal(0.0, listOf("watch.app")))
        assertFalse(HealthConnectAggregatePolicy.acceptsPlatformTotal(0.0, emptyList()))
        assertFalse(HealthConnectAggregatePolicy.acceptsPlatformTotal(12_345.0, listOf("")))
        assertFalse(HealthConnectAggregatePolicy.acceptsPlatformTotal(null, listOf("watch.app")))
        assertFalse(HealthConnectAggregatePolicy.acceptsPlatformTotal(Double.NaN, listOf("watch.app")))
        assertFalse(HealthConnectAggregatePolicy.acceptsPlatformTotal(Double.POSITIVE_INFINITY, listOf("watch.app")))
        assertFalse(HealthConnectAggregatePolicy.acceptsPlatformTotal(-1.0, listOf("watch.app")))
    }

    @Test
    fun platformAggregateRejectionsExposeMissingInvalidAndSourceFailuresSeparately() {
        assertEquals(
            HealthConnectAggregatePolicy.PlatformTotalRejection.MISSING_TOTAL,
            HealthConnectAggregatePolicy.platformTotalRejection(null, listOf("watch.app"))
        )
        assertEquals(
            HealthConnectAggregatePolicy.PlatformTotalRejection.INVALID_TOTAL,
            HealthConnectAggregatePolicy.platformTotalRejection(Double.NaN, listOf("watch.app"))
        )
        assertEquals(
            HealthConnectAggregatePolicy.PlatformTotalRejection.MISSING_SOURCE,
            HealthConnectAggregatePolicy.platformTotalRejection(0.0, listOf("", " "))
        )
        assertEquals(null, HealthConnectAggregatePolicy.platformTotalRejection(0.0, listOf("watch.app")))
    }

    @Test
    fun healthAggregatesRunOnlyWhileDeviceIsInteractive() {
        assertTrue(HealthConnectAggregatePolicy.shouldQuery(isInteractive = true))
        assertFalse(HealthConnectAggregatePolicy.shouldQuery(isInteractive = false))
    }

    @Test
    fun deduplicatedActivityTotalAcceptsValidMultiOriginPlatformResult() {
        assertTrue(HealthConnectAggregatePolicy.acceptsDeduplicatedActivityTotal(125.0, listOf("watch.app", "phone.app")))
        assertTrue(HealthConnectAggregatePolicy.acceptsDeduplicatedActivityTotal(0.0, listOf("watch.app")))
        assertFalse(HealthConnectAggregatePolicy.acceptsDeduplicatedActivityTotal(125.0, emptyList()))
        assertFalse(HealthConnectAggregatePolicy.acceptsDeduplicatedActivityTotal(125.0, listOf("")))
        assertFalse(HealthConnectAggregatePolicy.acceptsDeduplicatedActivityTotal(-1.0, listOf("watch.app")))
        assertFalse(HealthConnectAggregatePolicy.acceptsDeduplicatedActivityTotal(Double.NaN, listOf("watch.app")))
    }

    @Test
    fun allAggregateMetricsRequireOneOriginAndMatchingValidRecords() {
        assertTrue(HealthConnectAggregatePolicy.acceptsAggregate(4200.0, listOf("watch.app"), 4200.0, listOf("watch.app")))
        assertFalse(HealthConnectAggregatePolicy.acceptsAggregate(4200.0, listOf("watch.app", "phone.app"), 4200.0, listOf("watch.app")))
        assertFalse(HealthConnectAggregatePolicy.acceptsAggregate(4200.0, listOf("watch.app"), null, listOf("watch.app")))
        assertFalse(HealthConnectAggregatePolicy.acceptsAggregate(4201.0, listOf("watch.app"), 4200.0, listOf("watch.app")))
        assertFalse(HealthConnectAggregatePolicy.acceptsAggregate(4200.0, listOf("watch.app"), 4200.0, listOf("other.app")))
    }

    @Test
    fun nonDeduplicatedRecordTotalsRequireMatchingSingleOriginEvidence() {
        assertTrue(HealthConnectAggregatePolicy.acceptsSingleOriginRecordAggregate(
            650.0, listOf("manual.app"), 650.0, listOf("manual.app", "manual.app")
        ))
        assertFalse(HealthConnectAggregatePolicy.acceptsSingleOriginRecordAggregate(
            1_300.0, listOf("manual.app", "watch.app"), 1_300.0, listOf("manual.app", "watch.app")
        ))
        assertFalse(HealthConnectAggregatePolicy.acceptsSingleOriginRecordAggregate(
            700.0, listOf("manual.app"), 650.0, listOf("manual.app")
        ))
        assertFalse(HealthConnectAggregatePolicy.acceptsSingleOriginRecordAggregate(
            650.0, listOf("manual.app"), null, listOf("manual.app")
        ))
    }

    @Test
    fun manualTotalsRequireAReportedSingleNonBlankOrigin() {
        assertTrue(HealthConnectAggregatePolicy.hasSingleOrigin(listOf("app.one", "app.one")))
        assertEquals("app.one", HealthConnectAggregatePolicy.singleOriginOrNull(listOf("app.one", "app.one")))
        assertEquals(null, HealthConnectAggregatePolicy.singleOriginOrNull(listOf("app.one", "app.two")))
        assertEquals(null, HealthConnectAggregatePolicy.singleOriginOrNull(listOf("")))
        assertFalse(HealthConnectAggregatePolicy.hasSingleOrigin(emptyList()))
        assertFalse(HealthConnectAggregatePolicy.hasSingleOrigin(listOf("app.one", "app.two")))
        assertFalse(HealthConnectAggregatePolicy.hasSingleOrigin(listOf("")))
    }

    @Test
    fun trendRecordsStayWithinTheLatestReadingOrigin() {
        val records = listOf("watch:96", "phone:98", "watch:97")
        assertEquals(listOf("watch:96", "watch:97"), HealthConnectAggregatePolicy.recordsForOrigin(records, "watch") { it.substringBefore(':') })
        assertTrue(HealthConnectAggregatePolicy.recordsForOrigin(records, null) { it.substringBefore(':') }.isEmpty())
    }

    @Test
    fun sleepTrendIncludesOnlyWholeSessionsInsideSevenDayWindow() {
        val windowStart = Instant.parse("2026-09-21T12:00:00Z")
        val windowEnd = Instant.parse("2026-09-28T12:00:00Z")
        val sessions = listOf(
            windowStart.minusSeconds(1) to windowStart.plusSeconds(3_600),
            windowStart to windowStart.plusSeconds(3_600),
            windowEnd.minusSeconds(3_600) to windowEnd,
            windowEnd.minusSeconds(3_600) to windowEnd.plusSeconds(1)
        )

        val included = HealthConnectAggregatePolicy.completeIntervalsWithinRange(
            sessions, windowStart, windowEnd, { it.first }, { it.second }
        )

        assertEquals(listOf(sessions[1], sessions[2]), included)
    }

    @Test
    fun invalidSessionDurationsCannotEnterSleepTrend() {
        val start = Instant.parse("2026-09-21T12:00:00Z")
        val end = start.plusSeconds(3600)
        val records = listOf(start to start, end to start, start to end)
        assertEquals(listOf(start to end), HealthConnectAggregatePolicy.completeIntervalsWithinRange(
            records, start, end, { it.first }, { it.second }
        ))
        assertTrue(runCatching {
            HealthConnectAggregatePolicy.completeIntervalsWithinRange(
                records, end, start, { it.first }, { it.second }
            )
        }.isFailure)
    }
}
