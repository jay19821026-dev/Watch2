package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class ManufacturerSelfTestTest {
    @Test
    fun pressureAcceptsDatasheetRange() {
        val report = ManufacturerSelfTest.evaluate("pressure", listOf(1013.2f), 1f, "has_data")
        assertNotNull(report)
        assertEquals(3, report!!.passedCount)
    }

    @Test
    fun stationaryAccelerationMatchesGravity() {
        val values = listOf(0f, 0f, 9.80665f, 0.02f, -0.01f, 9.80f)
        val report = ManufacturerSelfTest.evaluate("accelerometer", values, 50f, "has_data")
        assertNotNull(report)
        assertEquals(3, report!!.passedCount)
    }

    @Test
    fun temperatureRawDataNeverClaimsChipCalibrationOrCelsiusPrecision() {
        val report = ManufacturerSelfTest.evaluate("body_temperature_raw", listOf(80f), 1f, "has_data")
        assertNotNull(report)
        assertTrue(report!!.checks.any { it.name == "單位／轉換" && !it.passed })
        assertTrue(report.checks.any { it.name == "整機校準" && !it.passed })
        assertTrue(report.note.contains("不輸出體溫"))
    }

    @Test
    fun ecgSelfTestUsesReportedRateInsteadOfAssumingChipRate() {
        val waveform = List(500) { sin(it * 0.1).toFloat() }
        val report = ManufacturerSelfTest.evaluate("ecg", waveform, 60f, "has_data")

        assertNotNull(report)
        assertTrue(report!!.checks.any { it.name == "HAL 取樣時基" && it.passed })
        assertTrue(report.note.contains("候選 AFE"))
        assertTrue(report.note.contains("公開 Watch 2 Pro 規格未列 ECG"))
    }

    @Test
    fun magnetometerRejectsImpossibleFieldMagnitude() {
        val report = ManufacturerSelfTest.evaluate("magnetometer", listOf(800f, 0f, 0f), 20f, "has_data")
        assertNotNull(report)
        assertTrue(report!!.checks.any { it.name == "地磁量級" && !it.passed })
    }
}
