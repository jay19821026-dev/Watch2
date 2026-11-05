package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HealthServicesCapabilityTraceTest {
    @Test
    fun snapshotSeparatesCompletedAndPendingCapabilityCalls() {
        var elapsed = 10L
        val trace = HealthServicesCapabilityTrace({ elapsed }, expectedNames = setOf("Measure", "Passive"))

        trace.started("Measure")
        elapsed = 25L
        trace.started("Passive")
        elapsed = 40L
        trace.completed("Measure", "available: HR", "completed")

        val calls = trace.snapshot()

        assertEquals(2, calls.size)
        assertEquals(10L, calls[0].startedElapsedNanos)
        assertEquals(40L, calls[0].completedElapsedNanos)
        assertEquals("completed", calls[0].outcome)
        assertNull(calls[1].completedElapsedNanos)
        assertEquals("pending", calls[1].outcome)
    }

    @Test
    fun completionIsIdempotentAndBounded() {
        var elapsed = 10L
        val trace = HealthServicesCapabilityTrace({ elapsed }, expectedNames = setOf("Exercise"))
        trace.started("Exercise")
        elapsed = 20L
        trace.completed("Exercise", "failed:RemoteException", "failed:RemoteException")
        elapsed = 30L
        trace.completed("Exercise", "available: Exercise", "completed")

        val call = trace.snapshot().single()

        assertEquals(20L, call.completedElapsedNanos)
        assertEquals("failed:RemoteException", call.outcome)
        assertEquals("failed:ExecutionException", healthServicesCapabilityOutcome("failed:ExecutionException"))
        assertEquals("completed", healthServicesCapabilityOutcome("available: HR"))
    }

    @Test
    fun capabilityOutcomeIsTruncatedToItsExportBound() {
        val trace = HealthServicesCapabilityTrace({ 10L }, expectedNames = setOf("Exercise"))
        trace.started("Exercise")
        trace.completed("Exercise", "available: EXERCISE", "x".repeat(100))

        assertEquals(80, trace.snapshot().single().outcome.length)
    }

    @Test
    fun responseFreezeMarksOnlyUnfinishedCallsAndIgnoresLateCompletion() {
        var elapsed = 10L
        val trace = HealthServicesCapabilityTrace({ elapsed }, expectedNames = setOf("Measure", "Exercise"))
        trace.started("Measure")
        trace.started("Exercise")
        elapsed = 20L
        trace.completed("Measure", "available: HR", "completed")
        val responseSnapshot = trace.freezeSnapshot()
        elapsed = 30L
        trace.completed("Exercise", "available: EXERCISE", "completed")

        val calls = trace.snapshot()

        assertEquals("completed", calls[0].outcome)
        assertEquals("pending_at_response_timeout", calls[1].outcome)
        assertNull(calls[1].completedElapsedNanos)
        assertEquals(false, trace.started("Late"))
        assertEquals(calls, responseSnapshot.calls)
        assertEquals(listOf("Measure: available: HR"), responseSnapshot.results)
        assertEquals(false, responseSnapshot.complete)
    }

    @Test
    fun completedResponseFreezesCallsWithoutTimeoutLabels() {
        var elapsed = 10L
        val trace = HealthServicesCapabilityTrace({ elapsed }, expectedNames = setOf("Measure"))
        trace.started("Measure")
        elapsed = 20L
        assertEquals(true, trace.completed("Measure", "available: HR", "completed"))

        val responseSnapshot = trace.freezeSnapshot()
        elapsed = 30L
        trace.completed("Measure", "late_update", "late_update")

        assertEquals("completed", responseSnapshot.calls.single().outcome)
        assertEquals(20L, responseSnapshot.calls.single().completedElapsedNanos)
        assertEquals(listOf("Measure: available: HR"), responseSnapshot.results)
        assertEquals(true, responseSnapshot.complete)
    }

    @Test
    fun concurrentCompletionAndFreezeReturnOneConsistentSnapshot() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val trace = HealthServicesCapabilityTrace({ 20L }, expectedNames = setOf("Measure"))
                trace.started("Measure")
                val start = CountDownLatch(1)
                val completion = executor.submit<Boolean> {
                    start.await()
                    trace.completed("Measure", "available: HR", "completed")
                }
                val freeze = executor.submit<HealthServicesCapabilitySnapshot> {
                    start.await()
                    trace.freezeSnapshot()
                }
                start.countDown()

                val accepted = completion.get(1, TimeUnit.SECONDS)
                val response = freeze.get(1, TimeUnit.SECONDS)
                assertEquals(accepted, response.complete)
                if (response.complete) {
                    assertEquals("completed", response.calls.single().outcome)
                    assertEquals(listOf("Measure: available: HR"), response.results)
                } else {
                    assertEquals("pending_at_response_timeout", response.calls.single().outcome)
                    assertEquals(emptyList<String>(), response.results)
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun unknownCapabilityCannotInflateCompleteness() {
        val trace = HealthServicesCapabilityTrace({ 10L })

        assertEquals(false, trace.started("WrongName"))
        assertEquals(true, trace.started("Measure"))
        assertEquals(false, trace.completed("Measure", "available: HR", "completed"))
        assertEquals(true, trace.started("Passive"))
        assertEquals(false, trace.completed("Passive", "available: STEPS", "completed"))
        assertEquals(true, trace.started("Exercise"))
        assertEquals(true, trace.completed("Exercise", "available: RUNNING", "completed"))
        assertEquals(true, trace.freezeSnapshot().complete)
    }

    @Test
    fun arbitraryNamesCannotSubstituteForMissingExpectedCapability() {
        val trace = HealthServicesCapabilityTrace({ 10L })

        assertEquals(false, trace.started("MeasureTypo"))
        assertEquals(true, trace.started("Measure"))
        trace.completed("Measure", "available: HR", "completed")
        assertEquals(true, trace.started("Passive"))
        trace.completed("Passive", "available: STEPS", "completed")

        val snapshot = trace.freezeSnapshot()
        assertEquals(false, snapshot.complete)
        assertEquals(listOf("Measure", "Passive"), snapshot.calls.map { it.name })
    }
}
