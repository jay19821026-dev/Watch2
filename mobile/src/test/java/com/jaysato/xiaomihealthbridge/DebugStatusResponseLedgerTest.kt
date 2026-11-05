package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugStatusResponseLedgerTest {
    @Test
    fun retainingLateResponseDoesNotReplaceTheActiveRequest() {
        val activeRequestId = "request-0000000000000002"
        val lateRequestId = "request-0000000000000001"
        val retained = DebugStatusResponseLedger.appendRequestId(activeRequestId, lateRequestId)

        assertEquals(listOf(activeRequestId, lateRequestId), retained)
        assertTrue(DebugStatusResponseLedger.isActiveResponse(activeRequestId, activeRequestId))
        assertFalse(DebugStatusResponseLedger.isActiveResponse(activeRequestId, lateRequestId))
    }

    @Test
    fun responseOrderIsUniqueAndBounded() {
        val ids = (1..10).map { "request-${it.toString().padStart(16, '0')}" }
        var encoded: String? = null
        ids.forEach { encoded = DebugStatusResponseLedger.appendRequestId(encoded, it).joinToString(",") }
        encoded = DebugStatusResponseLedger.appendRequestId(encoded, ids[8]).joinToString(",")

        assertEquals(ids.drop(2).toSet(), encoded.orEmpty().split(',').toSet())
        assertEquals(DebugStatusResponseLedger.MAX_RESPONSES, encoded.orEmpty().split(',').size)
        assertEquals(1, encoded.orEmpty().split(',').count { it == ids[8] })
    }
}
