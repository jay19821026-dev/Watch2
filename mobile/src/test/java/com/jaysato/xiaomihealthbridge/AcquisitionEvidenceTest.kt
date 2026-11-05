package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquisitionEvidenceTest {
    @Test
    fun emptyFailureCannotBeEnrichedIntoReceivedSignal() {
        assertFalse(RawSensorPayloadPolicy.hasAcquisitionEvidence(0, 0, 0, 0))
    }

    @Test
    fun unmappedEventsAndLegacyValuesRemainEvidence() {
        assertTrue(RawSensorPayloadPolicy.hasAcquisitionEvidence(4, 0, 0, 0))
        assertTrue(RawSensorPayloadPolicy.hasAcquisitionEvidence(0, 1, 0, 0))
        assertTrue(RawSensorPayloadPolicy.hasAcquisitionEvidence(0, 0, 8, 0))
        assertTrue(RawSensorPayloadPolicy.hasAcquisitionEvidence(0, 0, 0, 1))
    }
}
