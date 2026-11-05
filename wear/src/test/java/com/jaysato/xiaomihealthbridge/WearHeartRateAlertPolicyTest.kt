package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearHeartRateAlertPolicyTest {
    @Test fun onlyFreshPlausibleHealthServicesPointsEnterAlertPath() {
        val now = 1_000_000L
        assertTrue(WearHeartRateAlertPolicy.shouldObserve(105L, now - 30_000L, now))
        assertFalse(WearHeartRateAlertPolicy.shouldObserve(0L, now, now))
        assertFalse(WearHeartRateAlertPolicy.shouldObserve(300L, now, now))
        assertFalse(WearHeartRateAlertPolicy.shouldObserve(105L, now - 121_000L, now))
        assertFalse(WearHeartRateAlertPolicy.shouldObserve(105L, now + 31_000L, now))
    }

    @Test fun onlyIncreasingSamplesWithinTwoMinutesContinueAlertEpisode() {
        assertTrue(WearHeartRateAlertPolicy.hasContinuousSampleGap(1_000L, 121_000L))
        assertFalse(WearHeartRateAlertPolicy.hasContinuousSampleGap(1_000L, 121_001L))
        assertFalse(WearHeartRateAlertPolicy.hasContinuousSampleGap(1_000L, 1_000L))
        assertFalse(WearHeartRateAlertPolicy.hasContinuousSampleGap(2_000L, 1_000L))
        assertFalse(WearHeartRateAlertPolicy.hasContinuousSampleGap(0L, 1_000L))
    }

    @Test fun staleNormalSampleCannotResetNewerAlertEpisode() {
        val newerEpisode = WearHeartRateAlertPolicy.advance(
            kind = "high",
            timestamp = 900_000L,
            previousObservedTimestamp = 840_000L,
            previousKind = "high",
            previousFirstTimestamp = 0L,
            previousSampleCount = 2,
            previousSampleTimestamp = 840_000L
        )
        val staleNormal = WearHeartRateAlertPolicy.advance(
            kind = "",
            timestamp = 600_000L,
            previousObservedTimestamp = 900_000L,
            previousKind = newerEpisode!!.kind,
            previousFirstTimestamp = newerEpisode.firstTimestamp,
            previousSampleCount = newerEpisode.sampleCount,
            previousSampleTimestamp = newerEpisode.lastSampleTimestamp
        )

        assertNull(staleNormal)
        assertEquals("high", newerEpisode.kind)
        assertEquals(3, newerEpisode.sampleCount)
    }

    @Test fun alertRequiresTenMinutesAndContinuousSamples() {
        val start = 1_000L
        val progress = WearHeartRateAlertPolicy.Progress("high", start, 3, start + 10 * 60_000L)
        val gapReset = WearHeartRateAlertPolicy.advance(
            kind = "high",
            timestamp = start + 13 * 60_000L,
            previousObservedTimestamp = start + 10 * 60_000L,
            previousKind = "high",
            previousFirstTimestamp = start,
            previousSampleCount = 3,
            previousSampleTimestamp = start + 10 * 60_000L
        )

        assertTrue(WearHeartRateAlertPolicy.isSustained(progress, 0L))
        assertFalse(WearHeartRateAlertPolicy.isSustained(progress.copy(sampleCount = 2), 0L))
        assertEquals(start + 13 * 60_000L, gapReset!!.firstTimestamp)
        assertEquals(1, gapReset.sampleCount)
    }

    @Test fun minuteLevelCallbacksCanReachSustainedGateWithoutSyncThrottling() {
        var progress = WearHeartRateAlertPolicy.Progress("", 0L, 0, 0L)
        var lastObserved = 0L
        for (minute in 1..11) {
            val timestamp = minute * 60_000L
            progress = WearHeartRateAlertPolicy.advance(
                "high", timestamp, lastObserved, progress.kind,
                progress.firstTimestamp, progress.sampleCount, progress.lastSampleTimestamp
            )!!
            lastObserved = timestamp
        }
        assertTrue(WearHeartRateAlertPolicy.isSustained(progress, 0L))
    }
}
