package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RawSnapshotTransactionsTest {
    @Test fun updatesMergeWithLatestPersistedValues() {
        var stored: Map<String, *> = mapOf("old" to 1, "keep" to 2)
        val store = RawSnapshotTransactions({ stored }, { stored = it })
        store.update(RawSnapshotTransactions.Update(removals = setOf("old"), values = mapOf("new" to 3)))
        assertEquals(mapOf("keep" to 2, "new" to 3), store.read())
        store.update(RawSnapshotTransactions.Update(clear = true, values = mapOf("only" to 4)))
        assertEquals(mapOf("only" to 4), store.read())
    }

    @Test fun failedWritesDoNotReturnSuccessOrCacheOldState() {
        var stored: Map<String, *> = mapOf("raw" to 1)
        val store = RawSnapshotTransactions({ stored }, { stored = it; error("failure after persistence") })
        assertTrue(runCatching { store.update(RawSnapshotTransactions.Update(values = mapOf("raw" to 2))) }.isFailure)
        assertEquals(2, store.read()["raw"])
    }

    @Test fun silentlyDroppedWriteFailsReadbackVerification() {
        val store = RawSnapshotTransactions({ mapOf("raw" to 1) }, {})
        assertTrue(runCatching { store.update(RawSnapshotTransactions.Update(values = mapOf("raw" to 2))) }.isFailure)
    }

    @Test fun returnedCollectionsCannotMutateStoredEvidence() {
        val values = mutableSetOf("a")
        var stored: Map<String, *> = emptyMap<String, Any>()
        val store = RawSnapshotTransactions({ stored }, { stored = it })
        val result = store.update(RawSnapshotTransactions.Update(values = mapOf("set" to values)))
        values.add("b")
        (result["set"] as MutableSet<String>).add("c")
        assertEquals(setOf("a"), store.read()["set"])
    }

    @Test fun concurrentUpdatesDoNotLoseIndependentKeys() {
        var stored: Map<String, *> = emptyMap<String, Any>()
        val store = RawSnapshotTransactions({ stored }, { stored = it })
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val tasks = (0 until 20).map { index -> pool.submit {
                check(start.await(5, TimeUnit.SECONDS))
                store.update(RawSnapshotTransactions.Update(values = mapOf("key$index" to index)))
            } }
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(20, store.read().size)
        } finally { pool.shutdownNow() }
    }
}
