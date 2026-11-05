package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearDataEventDispatchPolicyTest {
    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun cancellationIsNotTreatedAsMalformedData() {
        WearDataEventDispatchPolicy.dispatch(
            events = listOf(1, 2),
            shouldProcess = { true },
            process = { throw kotlinx.coroutines.CancellationException("stopped") },
            onFailure = { throw AssertionError("Cancellation must propagate") }
        )
    }

    @Test
    fun oneMalformedEventDoesNotPreventFollowingEventsFromProcessing() {
        val processed = mutableListOf<Int>()
        val errors = mutableListOf<String>()

        val result = WearDataEventDispatchPolicy.dispatch(
            events = listOf(1, 2, 3),
            shouldProcess = { true },
            process = { event ->
                if (event == 2) throw IllegalArgumentException("invalid test item")
                processed += event
            },
            onFailure = { errors += it.javaClass.simpleName }
        )

        assertEquals(listOf(1, 3), processed)
        assertEquals(listOf("IllegalArgumentException"), errors)
        assertEquals(WearDataEventDispatchPolicy.Result(processed = 2, failed = 1, skipped = 0), result)
    }

    @Test
    fun unchangedEventsAreSkippedAndUnexpectedPredicateFailureIsIsolated() {
        val processed = mutableListOf<Int>()
        val failureCount = intArrayOf(0)

        val result = WearDataEventDispatchPolicy.dispatch(
            events = listOf(1, 2, 3),
            shouldProcess = { event ->
                if (event == 2) throw IllegalStateException("invalid event metadata")
                event == 3
            },
            process = processed::add,
            onFailure = { failureCount[0]++ }
        )

        assertEquals(listOf(3), processed)
        assertTrue(failureCount[0] == 1)
        assertEquals(WearDataEventDispatchPolicy.Result(processed = 1, failed = 1, skipped = 1), result)
    }
}
