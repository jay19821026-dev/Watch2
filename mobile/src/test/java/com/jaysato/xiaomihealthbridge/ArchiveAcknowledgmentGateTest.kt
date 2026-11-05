package com.jaysato.xiaomihealthbridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveAcknowledgmentGateTest {
    @Test fun suspendedStorageCannotAcknowledgeEarly() = runBlocking {
        val storageStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val permitCommit = kotlinx.coroutines.CompletableDeferred<Unit>()
        var acknowledged = false
        val worker = launch {
            ArchiveAcknowledgmentGate.storeThenAcknowledge({
                storageStarted.complete(Unit)
                permitCommit.await()
            }, { acknowledged = true })
        }
        storageStarted.await()
        assertFalse(acknowledged)
        permitCommit.complete(Unit)
        worker.join()
        assertTrue(acknowledged)
    }

    @Test fun cancellationWhileStorageWaitsDoesNotAcknowledge() = runBlocking {
        val storageStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        var acknowledged = false
        val worker = launch {
            ArchiveAcknowledgmentGate.storeThenAcknowledge({
                storageStarted.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }, { acknowledged = true })
        }
        storageStarted.await()
        worker.cancel()
        worker.join()
        assertFalse(acknowledged)
    }

    @Test fun acknowledgmentFailureDoesNotUndoStoredEvidence() = runBlocking {
        val evidence = mutableListOf("raw")
        var failed = false
        try {
            ArchiveAcknowledgmentGate.storeThenAcknowledge({ evidence.add("archived") }, { throw IllegalStateException("transport") })
        } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        assertEquals(listOf("raw", "archived"), evidence)
    }

    @Test fun cancellationAfterStorageStillPreventsAcknowledgment() = runBlocking {
        var stored = false
        var acknowledged = false
        val worker = launch {
            ArchiveAcknowledgmentGate.storeThenAcknowledge({
                stored = true
                currentCoroutineContext().cancel()
            }, { acknowledged = true })
        }
        worker.join()
        assertTrue(stored)
        assertFalse(acknowledged)
    }

    @Test fun acknowledgmentFollowsCompletedStorage() = runBlocking {
        val events = mutableListOf<String>()
        ArchiveAcknowledgmentGate.storeThenAcknowledge({ events.add("stored") }, { events.add("ack") })
        assertEquals(listOf("stored", "ack"), events)
    }

    @Test fun storageFailurePreservesUnacknowledgedItem() = runBlocking {
        var acknowledged = false
        var rejected = false
        try {
            ArchiveAcknowledgmentGate.storeThenAcknowledge({ throw IllegalStateException("storage") }, { acknowledged = true })
        } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertFalse(acknowledged)
    }

    @Test fun storageCancellationPreservesUnacknowledgedItem() = runBlocking {
        var acknowledged = false
        var cancelled = false
        try {
            ArchiveAcknowledgmentGate.storeThenAcknowledge({ throw CancellationException("cancelled") }, { acknowledged = true })
        } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertFalse(acknowledged)
    }
}
