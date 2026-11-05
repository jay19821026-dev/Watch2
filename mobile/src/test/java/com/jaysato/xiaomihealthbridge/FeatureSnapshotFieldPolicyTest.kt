package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureSnapshotFieldPolicyTest {
    private val now = 1_000_000L
    private val eventSource = "health_services_datapoint_epoch"

    private fun measurement(
        field: String = "whs_heart_rate",
        value: Double = 86.5,
        timestamp: Long = now - 1,
        source: String? = eventSource,
        previous: Long = 0L,
        previousSensor: Long = 0L
    ) = FeatureSnapshotFieldPolicy.acceptedMeasurement(field, value, timestamp, source, previous, previousSensor, now)

    @Test
    fun healthServicesValueTimeAndSourceSurviveConsumerProjectionTogether() {
        val result = measurement() ?: error("Expected valid measurement")
        assertEquals(86.5f, result.value, 0f)
        assertEquals(now - 1, result.timestamp)
        assertEquals(eventSource, result.timestampSource)
    }

    @Test
    fun eachFeaturePreservesItsOwnTimestampSource() {
        listOf("heading", "altitude", "light", "temperature").forEach { field ->
            val result = measurement(field, 0.0, source = "android_sensor_event_elapsed_realtime")!!
            assertEquals(0f, result.value, 0f)
            assertEquals(now - 1, result.timestamp)
            assertEquals("android_sensor_event_elapsed_realtime", result.timestampSource)
        }
    }

    @Test
    fun missingSourceIsRetainedAsUnknownInsteadOfInventingProvenance() {
        listOf(null, "").forEach { source ->
            val result = measurement(source = source)!!
            assertEquals("watch_sample_timestamp_source_unreported", result.timestampSource)
            assertFalse(HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(result.timestampSource))
        }
    }

    @Test
    fun invalidValuesCannotReplaceExistingMeasurements() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE).forEach { value ->
            assertNull(measurement("altitude", value))
        }
        listOf(0.0, -1.0, 300.01).forEach { value -> assertNull(measurement(value = value)) }
        assertEquals(1f, measurement(value = 1.0)!!.value, 0f)
        assertEquals(300f, measurement(value = 300.0)!!.value, 0f)
    }

    @Test
    fun delayedDuplicateMissingAndFutureTimesCannotReplaceCurrentFields() {
        assertNull(measurement(timestamp = 0))
        assertNull(measurement(timestamp = now, previous = now))
        assertNull(measurement(timestamp = now - 1, previous = now))
        assertNull(measurement(timestamp = now - 1, previousSensor = now))
        assertNull(measurement(timestamp = now + 300_001))
    }
}
