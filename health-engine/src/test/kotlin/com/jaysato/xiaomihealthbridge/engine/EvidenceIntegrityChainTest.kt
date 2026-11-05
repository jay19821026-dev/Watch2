package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceIntegrityChainTest {
    @Test fun detectsPayloadMutation() {
        val first = EvidenceIntegrityChain.append("", 0, "ecg", "raw-a", "HAL", "v1")
        val second = EvidenceIntegrityChain.append(first.hash, 1, "ecg", "raw-b", "HAL", "v1")
        assertTrue(EvidenceIntegrityChain.verify(listOf(first, second)).valid)
        val mutated = second.copy(payload = "changed")
        val result = EvidenceIntegrityChain.verify(listOf(first, mutated))
        assertFalse(result.valid)
        assertEquals("PAYLOAD_OR_METADATA_CHANGED", result.reason)
    }

    @Test fun detectsReordering() {
        val first = EvidenceIntegrityChain.append("", 0, "ppg", "a", "HAL", "v1")
        val second = EvidenceIntegrityChain.append(first.hash, 1, "ppg", "b", "HAL", "v1")
        assertEquals("SEQUENCE_CHANGED", EvidenceIntegrityChain.verify(listOf(second, first)).reason)
    }
}
