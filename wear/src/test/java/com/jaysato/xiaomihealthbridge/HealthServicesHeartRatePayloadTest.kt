package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthServicesHeartRatePayloadTest {
    @Test
    fun preservesBpmEventTimestampAndExplicitTimestampSource() {
        val eventTimestamp = 1_791_234_567_890L

        val payload = HealthServicesHeartRatePayload.create(87L, eventTimestamp)

        assertEquals(87L, payload?.bpm)
        assertEquals(eventTimestamp, payload?.eventTimestampMillis)
        assertEquals("health_services_datapoint_epoch", payload?.timestampSource)
    }

    @Test
    fun rejectsMissingOrInvalidTimestampWithoutSubstitutingReceiveTime() {
        assertNull(HealthServicesHeartRatePayload.create(87L, 0L))
        assertNull(HealthServicesHeartRatePayload.create(87L, -1L))
    }

    @Test
    fun acceptsInclusiveProducerBpmBoundaries() {
        assertEquals(1L, HealthServicesHeartRatePayload.create(1L, 1L)?.bpm)
        assertEquals(300L, HealthServicesHeartRatePayload.create(300L, 1L)?.bpm)
    }

    @Test
    fun rejectsBpmOutsideProducerRange() {
        assertNull(HealthServicesHeartRatePayload.create(0L, 1L))
        assertNull(HealthServicesHeartRatePayload.create(-1L, 1L))
        assertNull(HealthServicesHeartRatePayload.create(301L, 1L))
    }
}
