package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStatusRequestStateTest {
    @Test
    fun retriesNodeLookupOnlyWhenRecreationInterruptedBeforeMessageWasSent() {
        assertTrue(WatchStatusRequestState.shouldRetryNodeLookup("querying_connected_nodes"))
        assertFalse(WatchStatusRequestState.shouldRetryNodeLookup("sending_status_request"))
        assertFalse(WatchStatusRequestState.shouldRetryNodeLookup("waiting_for_watch_response"))
    }

    @Test
    fun timeoutOnlyResolvesRequestsThatAreStillInFlight() {
        assertTrue(WatchStatusRequestState.isAwaitingResponse("sending_status_request"))
        assertTrue(WatchStatusRequestState.isAwaitingResponse("waiting_for_watch_response"))
        assertFalse(WatchStatusRequestState.isAwaitingResponse("failed:ApiException"))
        assertFalse(WatchStatusRequestState.isAwaitingResponse("correlated_response_received"))
        assertFalse(WatchStatusRequestState.isAwaitingResponse("no_connected_node"))
        assertFalse(WatchStatusRequestState.isAwaitingResponse("ambiguous_multiple_connected_watches"))
    }

    @Test
    fun responseTimeoutStartsOnlyAfterDataLayerAcceptsMessage() {
        assertFalse(WatchStatusRequestState.shouldArmResponseTimeout("sending_status_request"))
        assertTrue(WatchStatusRequestState.shouldArmResponseTimeout("waiting_for_watch_response"))
        assertFalse(WatchStatusRequestState.shouldArmResponseTimeout("failed:ApiException"))
    }

    @Test
    fun restoredTimeoutUsesTheDeadlineForItsCurrentStage() {
        assertEquals(
            25_000L,
            WatchStatusRequestState.timeoutDeadline("sending_status_request", 10_000L, 0L, 15_000L, 20_000L)
        )
        assertEquals(
            50_000L,
            WatchStatusRequestState.timeoutDeadline("waiting_for_watch_response", 10_000L, 30_000L, 15_000L, 20_000L)
        )
        assertNull(WatchStatusRequestState.timeoutDeadline("failed", 10_000L, 30_000L, 15_000L, 20_000L))
    }

    @Test
    fun lateCallbacksCannotResolveTimedOutRequests() {
        assertFalse(WatchStatusRequestState.canResolveTimeout("send_task_timeout", "sending_status_request"))
        assertTrue(WatchStatusRequestState.canResolveTimeout("sending_status_request", "sending_status_request"))
        assertFalse(WatchStatusRequestState.canResolveTimeout("waiting_for_watch_response", "sending_status_request"))
    }

    @Test
    fun restoresMonotonicTimestampAndFallsBackForOlderSavedState() {
        assertEquals(2_000L, WatchStatusRequestState.restoredElapsedTimestamp(2_000L, 1_000L, 5_000L, 6_000L))
        assertEquals(4_000L, WatchStatusRequestState.restoredElapsedTimestamp(0L, 1_000L, 5_000L, 2_000L))
        assertNull(WatchStatusRequestState.restoredElapsedTimestamp(0L, 0L, 5_000L, 6_000L))
    }
}
