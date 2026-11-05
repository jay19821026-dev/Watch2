package com.jaysato.xiaomihealthbridge

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearHeartRateSyncGateTest {
    @Test fun concurrentDuplicateHeartRateSamplesAreClaimedOnce() {
        val gate = WearHeartRateSyncGate()
        val ready = CountDownLatch(12)
        val start = CountDownLatch(1)
        val claimed = AtomicInteger()
        val executor = Executors.newFixedThreadPool(12)
        repeat(12) {
            executor.execute {
                ready.countDown()
                start.await()
                if (gate.claim(1_000L)) claimed.incrementAndGet()
            }
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS))
        start.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        assertEquals(1, claimed.get())
        assertTrue(gate.claim(31_000L))
    }
}
