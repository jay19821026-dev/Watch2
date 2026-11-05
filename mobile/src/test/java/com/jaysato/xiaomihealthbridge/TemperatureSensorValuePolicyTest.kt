package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureSensorValuePolicyTest {
    @Test
    fun finiteSamplesRemainRawWithoutAssumingTemperatureUnits() {
        val result = TemperatureSensorValuePolicy.assess(listOf(33.0f, 35.0f, 0.0f), listOf(0.0))

        assertEquals(22.6666666667, result.deviceRawValue!!, 0.001)
        assertEquals("has_data", result.status)
    }

    @Test
    fun eventWithOnlyInvalidValuesIsNotReportedAsNoEvents() {
        val result = TemperatureSensorValuePolicy.assess(
            listOf(Float.NaN, Float.POSITIVE_INFINITY), listOf(Double.NEGATIVE_INFINITY, Double.NaN)
        )

        assertNull(result.deviceRawValue)
        assertEquals("invalid_sensor_values", result.status)
        assertEquals(
            SensorDisplayState.QUALITY_REJECTED,
            SensorDisplayState.from(2, result.status, 100L, 150L, 1_000L, "thermometer")
        )
    }

    @Test
    fun emptyInputMeansNoEvents() {
        val result = TemperatureSensorValuePolicy.assess(emptyList(), emptyList())

        assertNull(result.deviceRawValue)
        assertEquals("no_events", result.status)
    }

    @Test
    fun latestFiniteRawValueCanBeUsedWhenWaveformHasNoPoints() {
        val result = TemperatureSensorValuePolicy.assess(emptyList(), listOf(36.2, 0.0))

        assertEquals(36.2, result.deviceRawValue!!, 0.001)
        assertEquals("has_data", result.status)
    }

    @Test
    fun detailUsesPersistedRawSamplesWhenAverageWasNotStored() {
        assertEquals(35.5, TemperatureSensorValuePolicy.detailValue(null, listOf(35.0f, 36.0f), emptyList())!!, 0.001)
        assertEquals(36.2, TemperatureSensorValuePolicy.detailValue(null, emptyList(), listOf(36.2))!!, 0.001)
        assertEquals(35.1, TemperatureSensorValuePolicy.detailValue(35.1, emptyList(), emptyList())!!, 0.001)
        assertNull(TemperatureSensorValuePolicy.detailValue(Double.NaN, emptyList(), emptyList()))
    }

    @Test
    fun finiteValuesArePreservedRegardlessOfPlausibleCelsiusRange() {
        val result = TemperatureSensorValuePolicy.assess(
            listOf(Float.NaN, Float.POSITIVE_INFINITY, 50.0f),
            listOf(Double.NEGATIVE_INFINITY)
        )

        assertEquals(50.0, result.deviceRawValue!!, 0.001)
        assertEquals("has_data", result.status)
    }

    @Test
    fun oneFiniteTemperaturePointProducesExplicitResearchSummary() {
        val summary = TemperatureResearchSummary.calculate(listOf(36.64f))

        assertEquals(1, summary?.finiteSampleCount)
        assertEquals(36.64, summary?.lastSequenceValueRaw ?: Double.NaN, 0.001)
        assertNull(summary?.lastMinusMedianRaw)
        assertTrue(summary?.displayText()?.contains("至少需 2 點") == true)
        assertTrue(summary?.displayText()?.contains("不建立基線") == true)
        assertTrue(summary?.displayText()?.contains("索引順序，非時間尺度") == true)
    }
}
