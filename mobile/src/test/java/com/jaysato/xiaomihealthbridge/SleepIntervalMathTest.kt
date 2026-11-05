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
}
