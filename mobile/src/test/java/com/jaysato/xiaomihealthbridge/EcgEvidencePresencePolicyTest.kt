package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgEvidencePresencePolicyTest {
    @Test fun anyPersistedRawEventOrDecodedSampleCountsAsEvidence() {
        assertTrue(EcgEvidencePresencePolicy.hasEvidence(12, 0, 0))
        assertTrue(EcgEvidencePresencePolicy.hasEvidence(0, 4, 0))
        assertTrue(EcgEvidencePresencePolicy.hasEvidence(0, 0, 2600))
    }

    @Test fun noCountsRemainsNoEvidence() {
        assertFalse(EcgEvidencePresencePolicy.hasEvidence(0, 0, 0))
    }
}
