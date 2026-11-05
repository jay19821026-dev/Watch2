package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorEventTimingDiagnosticsTest {
    @Test
    fun summarizesMonotonicSensorEventCadenceWithoutCallingItAdcRate() {
        val summary = SensorEventTimingDiagnostics.fromCsv("1000000,11000000,31000000")

        assertEquals(3, summary?.eventCount)
        assertEquals(0, summary?.outOfOrderCount)
        assertEquals(10.0, summary?.minIntervalMs ?: 0.0, 0.0001)
        assertEquals(15.0, summary?.medianIntervalMs ?: 0.0, 0.0001)
        assertEquals(20.0, summary?.maxIntervalMs ?: 0.0, 0.0001)
    }

    @Test
    fun countsOutOfOrderEventsAndSummarizesOnlyPositiveIntervals() {
        val summary = SensorEventTimingDiagnostics.fromCsv("1000000,11000000,9000000,29000000")

        assertEquals(1, summary?.outOfOrderCount)
        assertEquals(15.0, summary?.medianIntervalMs ?: 0.0, 0.0001)
    }

    @Test
    fun preservesNonPositiveTimestampAnomaliesInSummary() {
        val summary = SensorEventTimingDiagnostics.fromCsv("-1000000,9000000,19000000")

        assertEquals(1, summary?.invalidTimestampCount)
        assertEquals(10.0, summary?.medianIntervalMs ?: 0.0, 0.0001)
    }

    @Test
    fun rejectsMalformedOrOversizedTimestampCsv() {
        assertNull(SensorEventTimingDiagnostics.fromCsv("1,not-a-time,3"))
        assertNull(SensorEventTimingDiagnostics.fromCsv("1," + "2,".repeat(512) + "3"))
    }
}
