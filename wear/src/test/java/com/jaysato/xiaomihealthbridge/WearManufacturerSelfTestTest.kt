package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class WearManufacturerSelfTestTest {
    @Test
    fun ecgSelfTestDoesNotRequireAnAssumed500HzRate() {
        val waveform = FloatArray(500) { sin(it * 0.1).toFloat() }
        val report = WearManufacturerSelfTest.evaluate(snapshot(sampleRateHz = 60.0, waveform = waveform))!!

        assertTrue(report.lines.any { it.contains("HAL 取樣時基") && it.contains("60.0 Hz") })
        assertTrue(report.boundary.contains("候選 AFE"))
    }

    @Test
    fun ecgSelfTestShowsUnknownClockInsteadOfInventingOne() {
        val report = WearManufacturerSelfTest.evaluate(snapshot(sampleRateHz = 0.0))!!

        assertTrue(report.lines.any { it.contains("未知（索引時間軸）") })
        assertFalse(report.lines.any { it.contains("500.0 Hz") })
    }

    private fun snapshot(sampleRateHz: Double, waveform: FloatArray = FloatArray(500) { sin(it * 0.1).toFloat() }) =
        RawVitalsProbe.Snapshot(
            kind = RawVitalsProbe.Kind.ECG,
            sensorName = "ECG",
            vendor = "HAL",
            reportingMode = "continuous",
            samples = waveform.size,
            eventCount = waveform.size,
            sampleRateHz = sampleRateHz,
            channels = 16,
            accuracy = 0,
            minimum = waveform.minOrNull()?.toDouble(),
            maximum = waveform.maxOrNull()?.toDouble(),
            average = waveform.average(),
            latest = waveform.takeLast(16).map(Float::toDouble),
            waveform = waveform,
            worn = true,
            status = "has_data",
            reason = "test"
        )
}
