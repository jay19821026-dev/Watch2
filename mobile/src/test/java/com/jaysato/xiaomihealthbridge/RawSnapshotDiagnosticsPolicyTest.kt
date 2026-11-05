package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSnapshotDiagnosticsPolicyTest {
    @Test
    fun diagnosticReceiptIsAppliedAtMostOnce() {
        val receipt = "wear://node/raw-sensor/ppg/1-id-a"
        val ledger = RawSnapshotReceiptLedger.append("", receipt)

        assertFalse(RawSnapshotDiagnosticsPolicy.shouldRecord(ledger, receipt))
        assertTrue(RawSnapshotDiagnosticsPolicy.shouldRecord(ledger, "wear://node/raw-sensor/ppg/2-id-b"))
    }

    @Test
    fun receivedCountResetsForNewSessionAndDoesNotRewindForLateSession() {
        assertEquals(1, RawSnapshotDiagnosticsPolicy.nextReceivedCount(20L, 10L, 8))
        assertEquals(9, RawSnapshotDiagnosticsPolicy.nextReceivedCount(10L, 10L, 8))
        assertEquals(null, RawSnapshotDiagnosticsPolicy.nextReceivedCount(9L, 10L, 8))
        assertEquals(Int.MAX_VALUE, RawSnapshotDiagnosticsPolicy.nextReceivedCount(10L, 10L, Int.MAX_VALUE))
        assertEquals(1, RawSnapshotDiagnosticsPolicy.nextReceivedCount(
            10L, 10L, 8, "suite-new", "suite-old", 101L, 100L
        ))
        assertEquals(null, RawSnapshotDiagnosticsPolicy.nextReceivedCount(
            10L, 10L, 8, "suite-old", "suite-new", 99L, 100L
        ))
    }

    @Test
    fun newSessionReceiptIsCountedEvenWhenItContainsNoSensorEvents() {
        assertEquals(1, RawSnapshotDiagnosticsPolicy.nextReceivedCount(
            incomingSession = 20L,
            storedSession = 10L,
            storedCount = 8,
            incomingSampleTime = 0L,
            storedSampleTime = 100L
        ))
    }

    @Test
    fun failedCommitRequiresDuplicateReceiptToForceDurabilityCheckpoint() {
        RawSnapshotDiagnosticsPolicy.recordCommitResult("ppg", true)
        assertFalse(RawSnapshotDiagnosticsPolicy.needsDurabilityRetry("ppg"))

        RawSnapshotDiagnosticsPolicy.recordCommitResult("ppg", false)
        assertTrue(RawSnapshotDiagnosticsPolicy.needsDurabilityRetry("ppg"))
        RawSnapshotDiagnosticsPolicy.recordCommitResult("ppg", true)
        assertFalse(RawSnapshotDiagnosticsPolicy.needsDurabilityRetry("ppg"))
    }
}
