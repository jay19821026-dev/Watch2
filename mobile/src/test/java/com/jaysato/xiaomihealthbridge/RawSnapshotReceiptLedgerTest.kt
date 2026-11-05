package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSnapshotReceiptLedgerTest {
    @Test
    fun replayedReceiptIsRecognizedAndNotAddedTwice() {
        val encoded = RawSnapshotReceiptLedger.append("", "wear://node/raw-sensor/ppg/1-id-a")

        assertTrue(RawSnapshotReceiptLedger.contains(encoded, "wear://node/raw-sensor/ppg/1-id-a"))
        assertEquals(encoded, RawSnapshotReceiptLedger.append(encoded, "wear://node/raw-sensor/ppg/1-id-a"))
        assertFalse(RawSnapshotReceiptLedger.contains(encoded, "wear://node/raw-sensor/ppg/1-id-b"))
    }

    @Test
    fun receiptLedgerRemainsBounded() {
        val encoded = (0..512).fold("") { current, id ->
            RawSnapshotReceiptLedger.append(current, "receipt-$id")
        }

        assertFalse(RawSnapshotReceiptLedger.contains(encoded, "receipt-0"))
        assertTrue(RawSnapshotReceiptLedger.contains(encoded, "receipt-512"))
        assertEquals(512, encoded.lineSequence().count())
    }
}
