package com.jaysato.xiaomihealthbridge

import androidx.health.connect.client.records.metadata.Metadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OxygenRecordingMethodPolicyTest {
    @Test fun methodsKeepManualAndUnknownSeparateFromDeviceRecords() {
        val policy = OxygenRecordingMethodPolicy
        assertEquals(OxygenRecordingMethodPolicy.Method.MANUAL, policy.classify(Metadata.RECORDING_METHOD_MANUAL_ENTRY))
        assertEquals(OxygenRecordingMethodPolicy.Method.AUTOMATIC, policy.classify(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED))
        assertEquals(OxygenRecordingMethodPolicy.Method.ACTIVE, policy.classify(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED))
        assertEquals(OxygenRecordingMethodPolicy.Method.UNKNOWN, policy.classify(null))
        assertEquals(OxygenRecordingMethodPolicy.Method.UNKNOWN, policy.classify(Metadata.RECORDING_METHOD_UNKNOWN))
        assertEquals(OxygenRecordingMethodPolicy.Method.UNKNOWN, policy.classify(Int.MAX_VALUE))
        assertFalse(policy.eligibleForDeviceReference(emptyList()))
        assertFalse(policy.eligibleForDeviceReference(listOf(Metadata.RECORDING_METHOD_MANUAL_ENTRY)))
        assertFalse(policy.eligibleForDeviceReference(listOf(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED, null)))
        assertTrue(policy.eligibleForDeviceReference(listOf(
            Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
            Metadata.RECORDING_METHOD_ACTIVELY_RECORDED
        )))
    }
}
