package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class RawEvidenceFlagsTest {
    @Test
    fun unknownDecodedCountIsNotMissingRawPayload() {
        assertEquals("FINITE_VALUE_COUNT_UNKNOWN", RawEvidenceFlags.finiteValueFlag(null))
        assertEquals("FINITE_VALUES_MISSING", RawEvidenceFlags.finiteValueFlag(0))
        assertEquals("FINITE_VALUES_RECEIVED", RawEvidenceFlags.finiteValueFlag(10))
    }
}
