package com.jaysato.xiaomihealthbridge

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepIntervalMathTest {
    private val midnight = Instant.parse("2026-09-25T00:00:00Z")

    @Test
    fun overlappingAndDuplicateSleepIntervalsAreCountedOnce() {
        val minutes = SleepIntervalMath.unionMinutes(
            listOf(
                midnight to midnight.plusSeconds(3_600),
                midnight.plusSeconds(1_800) to midnight.plusSeconds(5_400),
                midnight to midnight.plusSeconds(3_600)
            ),
            midnight,
            midnight.plusSeconds(10_800)
        )
        assertEquals(90L, minutes)
    }

    @Test
    fun clipsIntervalsAndDoesNotCountOutsideRequestedWindow() {
        val minutes = SleepIntervalMath.unionMinutes(
            listOf(midnight.minusSeconds(1_800) to midnight.plusSeconds(5_400)),
            midnight,
            midnight.plusSeconds(3_600)
        )
        assertEquals(60L, minutes)
    }

    @Test
    fun completeStagesSubtractAwakeFromSleepButRetainSessionDuration() {
        val end = midnight.plusSeconds(7_200)
        val session = SleepSessionValue(
            midnight, end,
            awakeIntervals = listOf(SleepAwakeInterval(midnight.plusSeconds(1_800), midnight.plusSeconds(2_700))),
            hasStageData = true,
            stageIntervals = listOf(
                SleepAwakeInterval(midnight, midnight.plusSeconds(1_800)),
                SleepAwakeInterval(midnight.plusSeconds(1_800), midnight.plusSeconds(2_700)),
                SleepAwakeInterval(midnight.plusSeconds(2_700), end)
            )
        )
        assertEquals(SleepIntervalMath.Summary(120, 105, true), SleepIntervalMath.summarize(listOf(session), midnight, end))
    }

    @Test
    fun partialStagesAndOverlappingSourcesDoNotClaimActualSleep() {
        val end = midnight.plusSeconds(7_200)
        val partial = SleepSessionValue(
            midnight, end,
            hasStageData = true,
            stageIntervals = listOf(SleepAwakeInterval(midnight, midnight.plusSeconds(3_600)))
        )
        assertEquals(SleepIntervalMath.Summary(120, null, false), SleepIntervalMath.summarize(listOf(partial), midnight, end))
        val overlapping = SleepSessionValue(midnight.plusSeconds(3_600), end)
        assertEquals(
            SleepIntervalMath.Summary(120, null, false),
            SleepIntervalMath.summarize(listOf(partial, overlapping), midnight, end)
        )
    }
}
