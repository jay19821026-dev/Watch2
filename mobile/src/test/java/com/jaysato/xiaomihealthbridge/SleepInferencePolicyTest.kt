package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepInferencePolicyTest {
    @Test fun invalidHeartRateCannotProduceLowRateIntervalEstimate() {
        val now = Instant.parse("2026-09-28T12:00:00Z")
        val input = samples(now.minusSeconds(29 * 3600L), 180)
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { invalid ->
            assertNull(SleepInferencePolicy.infer(input.map { it.copy(bpm = invalid) }, now, ZoneOffset.UTC))
            assertNull(SleepInferencePolicy.infer(input.mapIndexed { index, sample ->
                if (index == 6) sample.copy(bpm = invalid) else sample
            }, now, ZoneOffset.UTC))
        }
    }
    @Test
    fun infersOnlyFromRecentSamplesAndReturnsTheirTimeBounds() {
        val now = Instant.parse("2026-09-28T12:00:00Z")
        val result = SleepInferencePolicy.infer(samples(now.minusSeconds(28 * 3600L), 180), now, ZoneOffset.UTC)

        assertEquals(3.0, result?.hours ?: 0.0, 0.0)
        assertEquals(now.minusSeconds(28 * 3600L), result?.startAt)
        assertEquals(now.minusSeconds(25 * 3600L), result?.endAt)
    }

    @Test
    fun ignoresLongerSleepLikeSegmentsOutsideRecentWindow() {
        val now = Instant.parse("2026-09-28T12:00:00Z")
        val oldSegment = samples(now.minusSeconds(105 * 3600L), 240)
        val shortRecentSegment = samples(now.minusSeconds(29 * 3600L), 120)

        assertNull(SleepInferencePolicy.infer(oldSegment + shortRecentSegment, now, ZoneOffset.UTC))
    }

    @Test
    fun splitsSleepLikeSamplesWhenGapExceedsFortyFiveMinutesBySeconds() {
        val now = Instant.parse("2026-09-28T12:00:00Z")
        val start = now.minusSeconds(29 * 3600L)
        val firstSegment = (0..8).map { index ->
            SleepInferencePolicy.Sample(start.plusSeconds(index * 15 * 60L), 60.0)
        }
        val secondStart = start.plusSeconds(120 * 60L + 45 * 60L + 59L)
        val secondSegment = (0..8).map { index ->
            SleepInferencePolicy.Sample(secondStart.plusSeconds(index * 15 * 60L), 60.0)
        }

        assertNull(SleepInferencePolicy.infer(firstSegment + secondSegment, now, ZoneOffset.UTC))
    }

    @Test
    fun doesNotBridgeObservedHighHeartRateSample() {
        val now = Instant.parse("2026-09-28T12:00:00Z")
        val start = now.minusSeconds(29 * 3600L)
        val samples = samples(start, 180).mapIndexed { index, sample ->
            if (index == 6) sample.copy(bpm = 100.0) else sample
        }

        assertNull(SleepInferencePolicy.infer(samples, now, ZoneOffset.UTC))
    }

    @Test
    fun rejectsDuplicateSampleTimestampsRatherThanOverweightingThem() {
        val now = Instant.parse("2026-09-28T12:00:00Z")
        val samples = samples(now.minusSeconds(29 * 3600L), 180)

        assertNull(SleepInferencePolicy.infer(samples + samples[6], now, ZoneOffset.UTC))
    }

    @Test
    fun excludesFutureHeartRateSamples() {
        val now = Instant.parse("2026-09-28T20:00:00Z")
        val futureSegment = samples(now.plusSeconds(3600L), 180)

        assertNull(SleepInferencePolicy.infer(futureSegment, now, ZoneOffset.UTC))
    }

    @Test
    fun ambiguousOrRecentSleepRecordsPreventHeartRateInferenceFallback() {
        assertEquals(false, SleepInferencePolicy.shouldInfer(sleepQuerySucceeded = false, hasAmbiguousRecords = false, hasRecentRecordedSession = false))
        assertEquals(false, SleepInferencePolicy.shouldInfer(sleepQuerySucceeded = true, hasAmbiguousRecords = true, hasRecentRecordedSession = false))
        assertEquals(false, SleepInferencePolicy.shouldInfer(sleepQuerySucceeded = true, hasAmbiguousRecords = false, hasRecentRecordedSession = true))
        assertEquals(true, SleepInferencePolicy.shouldInfer(sleepQuerySucceeded = true, hasAmbiguousRecords = false, hasRecentRecordedSession = false))
    }

    private fun samples(start: Instant, durationMinutes: Int): List<SleepInferencePolicy.Sample> =
        (0..durationMinutes / 15).map { index ->
            SleepInferencePolicy.Sample(start.plusSeconds(index * 15 * 60L), 60.0)
        }
}
