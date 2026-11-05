package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DebugStatusReplyGateTest {
    @Test
    fun primaryFailureAllowsOneCorrelatedFallbackAttempt() {
        val gate = DebugStatusReplyGate()

        assertEquals(true, gate.claimPrimary())
        assertEquals(true, gate.claimFailure(alreadyClaimed = true))
        assertEquals(false, gate.claimFailure(alreadyClaimed = true))
        assertEquals(false, gate.claimPrimary())
    }

    @Test
    fun unclaimedFailureUsesTheSinglePrimarySlot() {
        val gate = DebugStatusReplyGate()

        assertEquals(true, gate.claimFailure(alreadyClaimed = false))
        assertEquals(false, gate.claimPrimary())
        assertEquals(true, gate.claimFailure(alreadyClaimed = true))
    }

    @Test
    fun fallbackRequiresPrimaryClaim() {
        val gate = DebugStatusReplyGate()

        assertEquals(false, gate.claimFailure(alreadyClaimed = true))
        assertEquals(true, gate.claimPrimary())
        assertEquals(true, gate.claimFailure(alreadyClaimed = true))
    }

    @Test
    fun concurrentFailureCallbacksClaimOnlyOneFallback() {
        val gate = DebugStatusReplyGate()
        val start = CountDownLatch(1)
        val completed = CountDownLatch(64)
        val winners = AtomicInteger()
        val executor = Executors.newFixedThreadPool(8)
        gate.claimPrimary()

        try {
            repeat(64) {
                executor.execute {
                    start.await()
                    if (gate.claimFailure(alreadyClaimed = true)) winners.incrementAndGet()
                    completed.countDown()
                }
            }
            start.countDown()

            assertEquals(true, completed.await(2, TimeUnit.SECONDS))
            assertEquals(1, winners.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
