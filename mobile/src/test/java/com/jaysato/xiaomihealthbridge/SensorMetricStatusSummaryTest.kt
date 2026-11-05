package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorMetricStatusSummaryTest {
    @Test
    fun groupsFreshnessQualityCalibrationAndAvailabilitySeparately() {
        val counts = SensorMetricStatusSummary.count(
            listOf(
                SensorDisplayState.STALE,
                SensorDisplayState.FUTURE_TIMESTAMP,
                SensorDisplayState.TIMESTAMP_UNKNOWN,
                SensorDisplayState.AVAILABLE,
                SensorDisplayState.QUALITY_REJECTED,
                SensorDisplayState.CALIBRATION_MISSING,
                SensorDisplayState.NOT_MEASURED,
                SensorDisplayState.CONDITION_NOT_MET,
                SensorDisplayState.RAW_ONLY,
                SensorDisplayState.SIGNAL_VALID_RESEARCH,
                SensorDisplayState.DERIVED_ESTIMATE
            )
        )

        assertEquals(1, counts[SensorDisplayState.STALE.exportValue])
        assertEquals(1, counts[SensorDisplayState.FUTURE_TIMESTAMP.exportValue])
        assertEquals(1, counts[SensorDisplayState.TIMESTAMP_UNKNOWN.exportValue])
        assertEquals(1, counts[SensorDisplayState.AVAILABLE.exportValue])
        assertEquals(1, counts[SensorDisplayState.QUALITY_REJECTED.exportValue])
        assertEquals(1, counts[SensorDisplayState.CALIBRATION_MISSING.exportValue])
        assertEquals(1, counts[SensorDisplayState.NOT_MEASURED.exportValue])
        assertEquals(1, counts[SensorDisplayState.CONDITION_NOT_MET.exportValue])
        assertEquals(1, counts[SensorDisplayState.RAW_ONLY.exportValue])
        assertEquals(1, counts[SensorDisplayState.SIGNAL_VALID_RESEARCH.exportValue])
        assertEquals(1, counts[SensorDisplayState.DERIVED_ESTIMATE.exportValue])
    }
}
