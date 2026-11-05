package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateSensorValuePolicyTest {
    @Test
    fun emptyInputMeansNoEvents() {
        val assessment = HeartRateSensorValuePolicy.assess(emptyList(), emptyList())

        assertNull(assessment.bpm)
        assertEquals("no_events", assessment.status)
    }

    @Test
    fun receivedButUnusableValuesAreNotReportedAsNoEvents() {
        val assessment = HeartRateSensorValuePolicy.assess(listOf(0.0), listOf(Float.NaN, 300.0f))

        assertNull(assessment.bpm)
        assertEquals("invalid_sensor_values", assessment.status)
    }

    @Test
    fun plausibleLatestValueTakesPrecedenceAndRemainsUncalibratedData() {
        val assessment = HeartRateSensorValuePolicy.assess(listOf(72.0, 0.0), listOf(80.0f))

        assertEquals(72.0, assessment.bpm!!, 0.001)
        assertEquals("has_data", assessment.status)
    }

    @Test
    fun eventAndSampleCountsDistinguishUnparsedEventFromNoEvents() {
        val assessment = HeartRateSensorValuePolicy.assess(emptyList(), emptyList(), eventCount = 1, sampleCount = 1)

        assertNull(assessment.bpm)
        assertEquals("invalid_sensor_values", assessment.status)
    }

    @Test
    fun plausibleValueDoesNotMeanSignalQualityWasValidated() {
        val assessment = HeartRateSensorValuePolicy.assess(listOf(72.0), emptyList(), eventCount = 1, sampleCount = 1)

        assertEquals(72.0, assessment.bpm!!, 0.001)
        assertEquals("has_data", assessment.status)
    }
}
