package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSnapshotReceiptRetryStateTest {
    @Test
    fun persistenceFailureBypassesDuplicateAndOrderingRejectionUntilStored() {
        val receiptId = "wear://node/raw-sensor/ppg/1-id-a"
        val ledger = RawSnapshotReceiptLedger.append("", receiptId)

        RawSnapshotReceiptRetryState.markCommitFailed("ppg", receiptId)

        assertTrue(RawSnapshotReceiptRetryState.shouldRetry("ppg", receiptId))
        assertTrue(RawSnapshotReceiptRetryState.isCurrentFailedSnapshot("ppg", receiptId, receiptId))
        assertFalse(RawSnapshotReceiptRetryState.needsDurabilityFlushBeforeResolve("ppg", receiptId, receiptId))
        assertFalse(RawSnapshotReceiptRetryState.shouldApplyOrderingChecks(true))

        RawSnapshotReceiptRetryState.markKindPersisted("ppg")

        assertFalse(RawSnapshotReceiptRetryState.shouldRetry("ppg", receiptId))
        assertTrue(RawSnapshotReceiptLedger.contains(ledger, receiptId))
        assertTrue(RawSnapshotReceiptRetryState.shouldApplyOrderingChecks(false))
    }

    @Test
    fun supersededFailedReceiptDoesNotBypassOrderingOrDedupe() {
        val oldReceiptId = "wear://node/raw-sensor/ppg/10-old"
        val ledger = RawSnapshotReceiptLedger.append("", oldReceiptId)
        RawSnapshotReceiptRetryState.markCommitFailed("ppg", oldReceiptId)

        assertFalse(RawSnapshotReceiptRetryState.isCurrentFailedSnapshot("ppg", oldReceiptId, "wear://node/raw-sensor/ppg/11-new"))
        assertTrue(RawSnapshotReceiptRetryState.needsDurabilityFlushBeforeResolve("ppg", oldReceiptId, "wear://node/raw-sensor/ppg/11-new"))
        assertTrue(RawSnapshotReceiptLedger.contains(ledger, oldReceiptId))
        assertTrue(RawSnapshotReceiptRetryState.shouldApplyOrderingChecks(false))

        RawSnapshotReceiptRetryState.markKindPersisted("ppg")
        assertFalse(RawSnapshotReceiptRetryState.shouldRetry("ppg", oldReceiptId))
    }

    @Test
    fun failedCommitRetainsItemAndRedeliveryAfterRestartIsNotMistakenForDuplicate() {
        val receipt = "wear://node/raw-sensor/ppg/20-a"
        var durableLedger = ""
        var itemDeleted = false

        val failedCommit = true
        if (RawSnapshotCommitPolicy.canAcknowledgeDataItem(failedCommit)) itemDeleted = true
        assertFalse(itemDeleted)
        assertFalse(RawSnapshotReceiptLedger.contains(durableLedger, receipt))

        // Simulate process death: transient retry state is gone; only durable preferences survive.
        RawSnapshotReceiptRetryState.markKindPersisted("ppg")
        val restartedLedger = durableLedger
        assertFalse(RawSnapshotReceiptLedger.contains(restartedLedger, receipt))
        assertTrue(RawSnapshotCommitPolicy.canAcknowledgeDataItem(false))

        durableLedger = RawSnapshotReceiptLedger.append(restartedLedger, receipt)
        val restartedAfterSuccessfulCommit = durableLedger
        assertTrue(RawSnapshotReceiptLedger.contains(restartedAfterSuccessfulCommit, receipt))
        assertTrue(RawSnapshotCommitPolicy.canAcknowledgeDataItem(false))
    }
}
