package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SerializedSensorPayloadPolicyTest {
    @Test
    fun acceptsPayloadAtLimitAndRejectsPayloadAboveLimit() {
        assertNull(SerializedSensorPayloadPolicy.rejectionReason(SerializedSensorPayloadPolicy.MAX_DATA_MAP_BYTES))
        assertEquals(
            SerializedSensorPayloadPolicy.OVERSIZED_REASON,
            SerializedSensorPayloadPolicy.rejectionReason(SerializedSensorPayloadPolicy.MAX_DATA_MAP_BYTES + 1)
        )
    }

    @Test
    fun preservesProducerOversizeRejectionEvenWhenDiagnosticEnvelopeIsSmall() {
        assertEquals(
            SerializedSensorPayloadPolicy.OVERSIZED_REASON,
            SerializedSensorPayloadPolicy.rejectionReason(128, SerializedSensorPayloadPolicy.OVERSIZED_REASON)
        )
    }

    @Test
    fun rejectsUnknownProducerRejectionMarker() {
        assertEquals(
            "unsupported_payload_rejection_marker",
            SerializedSensorPayloadPolicy.rejectionReason(128, "arbitrary_marker")
        )
    }
}
