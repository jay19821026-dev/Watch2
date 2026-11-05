package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveRegistrationFlightTest {
    @Test
    fun concurrentRequestsShareOneRegistrationAndCompleteEveryCaller() {
        val flight = PassiveRegistrationFlight()
        val results = mutableListOf<Int>()

        assertTrue(flight.enqueue { results += it.getOrThrow() })
        assertFalse(flight.enqueue { results += it.getOrThrow() })
        assertFalse(flight.enqueue { results += it.getOrThrow() })

        flight.finish(Result.success(4))

        assertEquals(listOf(4, 4, 4), results)
        assertTrue(flight.enqueue { results += it.getOrThrow() })
    }

    @Test
    fun failedRegistrationIsDeliveredToAllWaitingCallers() {
        val flight = PassiveRegistrationFlight()
        val failures = mutableListOf<String>()
        val result = Result.failure<Int>(IllegalStateException("permission missing"))

        assertTrue(flight.enqueue { failures += it.exceptionOrNull()!!.message.orEmpty() })
        assertFalse(flight.enqueue { failures += it.exceptionOrNull()!!.message.orEmpty() })
        flight.finish(result)

        assertEquals(listOf("permission missing", "permission missing"), failures)
        assertTrue(flight.enqueue {})
    }
}
