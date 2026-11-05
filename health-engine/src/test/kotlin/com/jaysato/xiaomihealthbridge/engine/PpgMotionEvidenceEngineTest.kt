package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PpgMotionEvidenceEngineTest {
    private val ppg = (0 until 10).map { TimedPpgObservation(1_000_000_000L + it * 20_000_000L, it.toDouble()) }
    private val stillAcceleration = (0 until 10).map {
        TimedAccelerationObservation(1_000_000_000L + it * 20_000_000L, 0.0, 0.0, 9.80665)
    }

    @Test
    fun alignedSameSessionEvidenceProducesOnlyMotionActivityIndex() {
        val result = PpgMotionEvidenceEngine.evaluate(ppg, stillAcceleration, true, true, 1_000_000L)

        assertTrue(result.available)
        assertEquals("ALIGNED_MOTION_EVIDENCE_ONLY", result.reasonCode)
        assertEquals(10, result.pairedObservationCount)
        assertEquals(1.0, result.matchedCoverage!!, 0.0)
        assertEquals(0.0, result.dynamicAccelerationRmsMetersPerSecondSquared!!, 1e-9)
        assertEquals(0.0, result.motionActivityIndex!!, 1e-9)
    }

    @Test
    fun missingSessionClockOrMisalignedTimesCannotBecomeZeroMotion() {
        val differentSession = PpgMotionEvidenceEngine.evaluate(ppg, stillAcceleration, false, true, 1_000_000L)
        val unknownClock = PpgMotionEvidenceEngine.evaluate(ppg, stillAcceleration, true, false, 1_000_000L)
        val noOverlap = PpgMotionEvidenceEngine.evaluate(
            ppg,
            stillAcceleration.map { it.copy(timestampNanos = it.timestampNanos + 1_000_000_000L) },
            true,
            true,
            1_000_000L
        )

        assertEquals("SESSION_MISMATCH", differentSession.reasonCode)
        assertEquals("COMMON_MONOTONIC_CLOCK_UNCONFIRMED", unknownClock.reasonCode)
        assertEquals("NO_TIME_OVERLAP", noOverlap.reasonCode)
        listOf(differentSession, unknownClock, noOverlap).forEach {
            assertFalse(it.available)
            assertNull(it.motionActivityIndex)
        }
    }

    @Test
    fun sparseAccelerationEventsAreNotReusedToInflateCoverage() {
        val sparse = (0 until 8).map { index ->
            TimedAccelerationObservation(1_000_000_000L + index * 60_000_000L, 0.0, 0.0, 9.80665)
        }

        val result = PpgMotionEvidenceEngine.evaluate(ppg, sparse, true, true, 25_000_000L)

        assertFalse(result.available)
        assertEquals("INSUFFICIENT_OVERLAPPING_OBSERVATIONS", result.reasonCode)
        assertEquals(0, result.pairedObservationCount)
        assertNull(result.matchedCoverage)
    }

    @Test
    fun differentSamplingRatesUseTimeOverlapAndUniqueLowerRatePairs() {
        val densePpg = (0 until 100).map { index ->
            TimedPpgObservation(1_000_000_000L + index * 20_000_000L, index.toDouble())
        }
        val slowerAcceleration = (0 until 50).map { index ->
            TimedAccelerationObservation(1_000_000_000L + index * 40_000_000L, 0.0, 0.0, 9.80665)
        }

        val result = PpgMotionEvidenceEngine.evaluate(
            densePpg, slowerAcceleration, true, true, 50_000_000L
        )

        assertTrue(result.available)
        assertEquals(50, result.pairedObservationCount)
        assertEquals(1.0, result.matchedCoverage!!, 0.0)
        assertTrue(result.overlapOfPpgWindow!! < 1.0)
        assertTrue(result.overlapOfPpgWindow!! > 0.98)
    }

    @Test
    fun differentSamplingRatesUseActualOverlapWithoutReusingEvents() {
        val densePpg = (0 until 100).map {
            TimedPpgObservation(1_000_000_000L + it * 20_000_000L, it.toDouble())
        }
        val slowerAcceleration = (0 until 50).map {
            TimedAccelerationObservation(1_000_000_000L + it * 40_000_000L, 0.0, 0.0, 9.80665)
        }

        val result = PpgMotionEvidenceEngine.evaluate(
            densePpg, slowerAcceleration, true, true, 50_000_000L
        )

        assertTrue(result.available)
        assertEquals(50, result.pairedObservationCount)
        assertEquals(1.0, result.matchedCoverage!!, 0.0)
    }

    @Test
    fun invalidOrUnorderedEventsAreRejected() {
        val invalid = ppg.toMutableList().also { it[3] = it[3].copy(timestampNanos = 0L) }
        val unordered = stillAcceleration.toMutableList().also { it[4] = it[4].copy(timestampNanos = 1L) }
        assertEquals(
            "PPG_TIME_OR_VALUE_INVALID",
            PpgMotionEvidenceEngine.evaluate(invalid, stillAcceleration, true, true, 1_000_000L).reasonCode
        )
        assertEquals(
            "ACCELERATION_TIME_NOT_MONOTONIC",
            PpgMotionEvidenceEngine.evaluate(ppg, unordered, true, true, 1_000_000L).reasonCode
        )
    }

    @Test
    fun changingAccelerationDirectionIsDetectedEvenAtConstantMagnitude() {
        val lateral = sqrt(9.80665 * 9.80665 - 1.0)
        val changingDirection = (0 until 10).map { index ->
            TimedAccelerationObservation(
                1_000_000_000L + index * 20_000_000L,
                if (index % 2 == 0) 1.0 else -1.0,
                0.0,
                lateral
            )
        }

        val result = PpgMotionEvidenceEngine.evaluate(ppg, changingDirection, true, true, 1_000_000L)

        assertTrue(result.available)
        assertTrue(result.dynamicAccelerationRmsMetersPerSecondSquared!! > 0.9)
        assertTrue(result.motionActivityIndex!! > 0.08)
    }

    @Test
    fun reportsExploratoryPpgChangeAndMotionCovariationWhenAligned() {
        val motionX = (0 until 10).map { it - 4.5 }
        val motionLevels = motionX.zipWithNext().map { (before, after) ->
            (kotlin.math.abs(before) + kotlin.math.abs(after)) / 2.0
        }
        val ppgValues = mutableListOf(0.0)
        motionLevels.forEach { ppgValues += ppgValues.last() + it }
        val timedPpg = ppgValues.mapIndexed { index, value ->
            TimedPpgObservation(1_000_000_000L + index * 20_000_000L, value)
        }
        val acceleration = motionX.mapIndexed { index, x ->
            TimedAccelerationObservation(1_000_000_000L + index * 20_000_000L, x, 0.0, 9.80665)
        }

        val report = PpgMotionEvidenceEngine.evaluate(timedPpg, acceleration, true, true, 1_000_000L)

        assertTrue(report.available)
        assertEquals(1.0, report.ppgMotionChangeCorrelation!!, 1e-12)
    }

    @Test
    fun rejectsCovariationIntervalsWhoseDurationsDoNotMatchWithinPairSkew() {
        assertTrue(PpgMotionEvidenceEngine.intervalsAligned(20_000_000L, 21_000_000L, 1_000_000L))
        assertFalse(PpgMotionEvidenceEngine.intervalsAligned(20_000_000L, 22_000_001L, 1_000_000L))
        assertFalse(PpgMotionEvidenceEngine.intervalsAligned(0L, 20_000_000L, 1_000_000L))
    }
}
