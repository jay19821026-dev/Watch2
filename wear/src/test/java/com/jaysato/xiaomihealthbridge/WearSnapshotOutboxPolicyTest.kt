package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSnapshotOutboxPolicyTest {
    @Test
    fun automaticRetriesAreBoundedAndRejectInvalidAttemptCounts() {
        assertFalse(WearSnapshotOutboxPolicy.shouldRetryAutomatically(-1))
        repeat(WearSnapshotOutboxPolicy.MAX_AUTOMATIC_RETRIES) { attempt ->
            assertTrue(WearSnapshotOutboxPolicy.shouldRetryAutomatically(attempt))
        }
        assertFalse(WearSnapshotOutboxPolicy.shouldRetryAutomatically(WearSnapshotOutboxPolicy.MAX_AUTOMATIC_RETRIES))
    }

    @Test
    fun quarantineCleanupSelectsOnlyQuarantineFilesNotPendingOrTemporaryEntries() {
        assertTrue(WearSnapshotOutboxPolicy.isQuarantineFileName("entry.quarantine"))
        assertFalse(WearSnapshotOutboxPolicy.isQuarantineFileName("entry.enc"))
        assertFalse(WearSnapshotOutboxPolicy.isQuarantineFileName("entry.tmp"))
        assertFalse(WearSnapshotOutboxPolicy.isQuarantineFileName("quarantine"))
    }

    @Test
    fun acceptsBoundedEntryWhileBothQueueCapsHaveRoom() {
        assertTrue(WearSnapshotOutboxPolicy.canStore(3, 1024L, 4096))
    }

    @Test
    fun rejectsNewEntriesInsteadOfEvictingWhenEitherQueueCapIsReached() {
        assertFalse(WearSnapshotOutboxPolicy.canStore(WearSnapshotOutboxPolicy.MAX_ENTRIES, 0L, 1024))
        assertFalse(WearSnapshotOutboxPolicy.canStore(2, WearSnapshotOutboxPolicy.MAX_TOTAL_BYTES.toLong(), 1024))
        assertFalse(WearSnapshotOutboxPolicy.canStore(-1, 0L, 1024))
        assertFalse(WearSnapshotOutboxPolicy.canStore(1, -1L, 1024))
        assertFalse(WearSnapshotOutboxPolicy.canStore(1, Long.MAX_VALUE, 1024))
    }

    @Test
    fun rejectsOversizedAndEmptyPayloadsAsWholeEntries() {
        assertFalse(WearSnapshotOutboxPolicy.canStore(0, 0L, 0))
        assertFalse(
            WearSnapshotOutboxPolicy.canStore(0, 0L, WearSnapshotOutboxPolicy.MAX_PAYLOAD_BYTES + 4097)
        )
    }

    @Test
    fun ordersPendingSnapshotsByTimeThenProducerSequence() {
        val earlier = WearSnapshotDeliveryOrder(100L, 9L, "z")
        val later = WearSnapshotDeliveryOrder(200L, 1L, "a")
        val sameTimeEarlierSequence = WearSnapshotDeliveryOrder(200L, 2L, "y")
        val sameTimeLaterSequence = WearSnapshotDeliveryOrder(200L, 3L, "b")

        assertTrue(WearSnapshotOutboxPolicy.compareDeliveryOrder(earlier, later) < 0)
        assertTrue(
            WearSnapshotOutboxPolicy.compareDeliveryOrder(sameTimeEarlierSequence, sameTimeLaterSequence) < 0
        )
    }

}
