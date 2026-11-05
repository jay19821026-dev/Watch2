package com.jaysato.xiaomihealthbridge

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateHomeSummaryPolicyTest {
    private val start = Instant.now().minusSeconds(3_600)

    @Test
    fun overlappingSourcesKeepLatestReadingAndDoNotMergeTrend() {
        val result = HeartRateHomeSummaryPolicy.resolve(
            listOf(
                HeartRateHomeSummaryPolicy.Sample("Watch", start, 68.0),
                HeartRateHomeSummaryPolicy.Sample("Health Connect", start.plusSeconds(60), 72.0)
            ),
            SourceIntervalAggregation.Assessment(
                eligible = false,
                rejection = SourceIntervalAggregation.Rejection.MULTIPLE_DATA_ORIGINS
            )
        )

        assertEquals("72", result.value)
        assertTrue(result.detail.contains("多來源未合併"))
        assertEquals(listOf(72.0), result.trendValues)
        assertEquals(start.plusSeconds(60), result.measuredAt)
    }

    @Test
    fun eligibleSingleSourceSamplesShowLatestReadingAndChronologicalTrend() {
        val result = HeartRateHomeSummaryPolicy.resolve(
            listOf(
                HeartRateHomeSummaryPolicy.Sample("Watch", start, 64.0),
                HeartRateHomeSummaryPolicy.Sample("Watch", start.plusSeconds(60), 78.0)
            ),
            SourceIntervalAggregation.Assessment(eligible = true)
        )

        assertEquals("78", result.value)
        assertTrue(result.detail.contains("24 小時趨勢 · 2 筆"))
        assertEquals(listOf(64.0, 78.0), result.trendValues)
        assertEquals(start.plusSeconds(60), result.measuredAt)
    }

    @Test
    fun oneEligibleReadingDisplaysOneNumberInsteadOfDegenerateRange() {
        val result = HeartRateHomeSummaryPolicy.resolve(
            listOf(HeartRateHomeSummaryPolicy.Sample("Health Connect", start, 77.0)),
            SourceIntervalAggregation.Assessment(eligible = true)
        )

        assertEquals("77", result.value)
        assertTrue(result.detail.contains("24 小時趨勢 · 1 筆"))
        assertEquals(listOf(77.0), result.trendValues)
    }

    @Test
    fun futureHealthConnectSamplesAreExcludedFromSummary() {
        val now = start.plusSeconds(60)
        val result = HeartRateHomeSummaryPolicy.resolve(
            listOf(
                HeartRateHomeSummaryPolicy.Sample("Health Connect", start, 68.0),
                HeartRateHomeSummaryPolicy.Sample("Health Connect", now.plusSeconds(1), 180.0)
            ),
            SourceIntervalAggregation.Assessment(eligible = true),
            now
        )

        assertEquals("68", result.value)
        assertEquals(listOf(68.0), result.trendValues)
    }

    @Test
    fun samplesOlderThanTwentyFourHoursAreNotShownAsCurrent() {
        val now = start.plusSeconds(24 * 60 * 60L + 1L)
        val result = HeartRateHomeSummaryPolicy.resolve(
            listOf(HeartRateHomeSummaryPolicy.Sample("Health Connect", start, 68.0)),
            SourceIntervalAggregation.Assessment(eligible = true),
            now
        )

        assertEquals("待量測", result.value)
        assertTrue(result.detail.contains("近 24 小時沒有有效心率紀錄"))
        assertTrue(result.trendValues.isEmpty())
        assertEquals(null, result.measuredAt)
    }

    @Test
    fun emptyOrInvalidReadingsRemainUnavailable() {
        val result = HeartRateHomeSummaryPolicy.resolve(
            listOf(HeartRateHomeSummaryPolicy.Sample("Watch", start, Double.NaN)),
            SourceIntervalAggregation.Assessment(
                eligible = false,
                rejection = SourceIntervalAggregation.Rejection.INVALID_VALUE
            )
        )

        assertEquals("待量測", result.value)
        assertTrue(result.trendValues.isEmpty())
    }

    @Test
    fun excludesOutOfRangeSamplesAndKeepsInclusiveWearableBoundaries() {
        val result = HeartRateHomeSummaryPolicy.resolve(
            listOf(
                HeartRateHomeSummaryPolicy.Sample("Watch", start, 29.9),
                HeartRateHomeSummaryPolicy.Sample("Watch", start.plusSeconds(1), 30.0),
                HeartRateHomeSummaryPolicy.Sample("Watch", start.plusSeconds(2), 220.0),
                HeartRateHomeSummaryPolicy.Sample("Watch", start.plusSeconds(3), 220.1),
                HeartRateHomeSummaryPolicy.Sample("Watch", start.plusSeconds(4), 500.0)
            ),
            SourceIntervalAggregation.Assessment(eligible = true)
        )

        assertEquals("220", result.value)
        assertEquals(listOf(30.0, 220.0), result.trendValues)
        assertTrue(result.detail.contains("排除 3 筆"))
    }

    @Test
    fun onlyOutOfRangeSamplesDoNotProduceAHeartRateSummary() {
        val result = HeartRateHomeSummaryPolicy.resolve(
            listOf(
                HeartRateHomeSummaryPolicy.Sample("Watch", start, 0.0),
                HeartRateHomeSummaryPolicy.Sample("Watch", start.plusSeconds(1), 500.0)
            ),
            SourceIntervalAggregation.Assessment(eligible = false)
        )

        assertEquals("待量測", result.value)
        assertTrue(result.detail.contains("近 24 小時沒有有效心率紀錄"))
        assertTrue(result.detail.contains("排除 2 筆"))
        assertTrue(result.trendValues.isEmpty())
    }

    @Test
    fun trustedRecentWatchReadingFillsHealthConnectEmptyHomeCard() {
        val base = HeartRateHomeSummaryPolicy.resolve(
            emptyList(),
            SourceIntervalAggregation.Assessment(eligible = false)
        )
        val result = HeartRateHomeSummaryPolicy.withRecentWatchReading(
            base,
            listOf(
                HeartRateHomeSummaryPolicy.WatchCandidate(
                    bpm = 79f,
                    measuredAtMillis = start.toEpochMilli(),
                    sourceKind = "whs_heart_rate",
                    timestampSource = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE,
                    status = "has_data"
                )
            ),
            nowMillis = start.toEpochMilli() + 60_000
        )

        assertEquals("79 bpm", result.value)
        assertTrue(result.detail.contains("近 5 分鐘手錶讀值"))
        assertEquals(listOf(79.0), result.trendValues)
    }

    @Test
    fun staleOrUntrustedWatchValueDoesNotFillHomeCard() {
        val base = HeartRateHomeSummaryPolicy.resolve(
            emptyList(),
            SourceIntervalAggregation.Assessment(eligible = false)
        )
        val result = HeartRateHomeSummaryPolicy.withRecentWatchReading(
            base,
            listOf(
                HeartRateHomeSummaryPolicy.WatchCandidate(
                    bpm = 79f,
                    measuredAtMillis = start.toEpochMilli(),
                    sourceKind = "whs_heart_rate",
                    timestampSource = "callback_receipt_time",
                    status = "has_data"
                )
            ),
            nowMillis = start.toEpochMilli() + 60_000
        )

        assertEquals("待量測", result.value)
        assertTrue(result.trendValues.isEmpty())
    }

    @Test
    fun recentWatchReadingTakesPrecedenceOverHealthConnectDailySummary() {
        val healthConnect = HeartRateHomeSummaryPolicy.resolve(
            listOf(HeartRateHomeSummaryPolicy.Sample("Health Connect", start, 66.0)),
            SourceIntervalAggregation.Assessment(eligible = true)
        )
        val result = HeartRateHomeSummaryPolicy.withRecentWatchReading(
            healthConnect,
            listOf(
                HeartRateHomeSummaryPolicy.WatchCandidate(
                    79f, start.plusSeconds(1).toEpochMilli(), "whs_heart_rate",
                    HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, "has_data"
                )
            ),
            nowMillis = start.toEpochMilli() + 60_000
        )

        assertEquals("79 bpm", result.value)
        assertTrue(result.detail.contains("近 5 分鐘手錶讀值"))
        assertTrue(result.detail.contains("與手錶讀值分開"))
        assertEquals(listOf(66.0), result.trendValues)
        assertEquals(start.plusSeconds(1).toEpochMilli(), result.measuredAt?.toEpochMilli())
    }

    @Test
    fun staleWatchReadingKeepsHealthConnectSummary() {
        val healthConnect = HeartRateHomeSummaryPolicy.resolve(
            listOf(HeartRateHomeSummaryPolicy.Sample("Health Connect", start, 66.0)),
            SourceIntervalAggregation.Assessment(eligible = true)
        )
        val result = HeartRateHomeSummaryPolicy.withRecentWatchReading(
            healthConnect,
            listOf(
                HeartRateHomeSummaryPolicy.WatchCandidate(
                    79f, start.toEpochMilli(), "whs_heart_rate",
                    HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, "has_data"
                )
            ),
            nowMillis = start.toEpochMilli() + HeartRatePresentationPolicy.WEAR_STATE_FRESHNESS_MILLIS + 1
        )

        assertEquals("66", result.value)
        assertEquals(listOf(66.0), result.trendValues)
    }

    @Test
    fun watchCandidateRequiresPersistedValidStatus() {
        val base = HeartRateHomeSummaryPolicy.resolve(
            emptyList(), SourceIntervalAggregation.Assessment(eligible = false), start
        )
        val result = HeartRateHomeSummaryPolicy.withRecentWatchReading(
            base,
            listOf(
                HeartRateHomeSummaryPolicy.WatchCandidate(
                    79f, start.toEpochMilli(), "whs_heart_rate",
                    HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, "needs_oem_mode"
                )
            ),
            nowMillis = start.toEpochMilli() + 60_000
        )

        assertEquals("待量測", result.value)
    }

    @Test
    fun exactFiveMinuteWatchBoundaryIsAcceptedButFutureTimeIsRejected() {
        val base = HeartRateHomeSummaryPolicy.resolve(
            emptyList(), SourceIntervalAggregation.Assessment(eligible = false), start
        )
        val boundaryTime = start.toEpochMilli()
        val accepted = HeartRateHomeSummaryPolicy.withRecentWatchReading(
            base,
            listOf(
                HeartRateHomeSummaryPolicy.WatchCandidate(
                    79f, boundaryTime, "whs_heart_rate",
                    HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, "has_data"
                )
            ),
            nowMillis = boundaryTime + HeartRatePresentationPolicy.WEAR_STATE_FRESHNESS_MILLIS
        )
        val future = HeartRateHomeSummaryPolicy.withRecentWatchReading(
            base,
            listOf(
                HeartRateHomeSummaryPolicy.WatchCandidate(
                    79f, boundaryTime + 1, "whs_heart_rate",
                    HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, "has_data"
                )
            ),
            nowMillis = boundaryTime
        )

        assertEquals("79 bpm", accepted.value)
        assertEquals("待量測", future.value)
    }

    @Test
    fun newestEligibleWatchSourceWins() {
        val base = HeartRateHomeSummaryPolicy.resolve(
            emptyList(), SourceIntervalAggregation.Assessment(eligible = false), start
        )
        val result = HeartRateHomeSummaryPolicy.withRecentWatchReading(
            base,
            listOf(
                HeartRateHomeSummaryPolicy.WatchCandidate(
                    70f, start.toEpochMilli(), "whs_heart_rate",
                    HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE, "has_data"
                ),
                HeartRateHomeSummaryPolicy.WatchCandidate(
                    82f, start.plusSeconds(30).toEpochMilli(), "heart_rate_raw",
                    "android_sensor_event_elapsed_realtime", "valid_signal"
                )
            ),
            nowMillis = start.toEpochMilli() + 60_000
        )

        assertEquals("82 bpm", result.value)
    }
}
