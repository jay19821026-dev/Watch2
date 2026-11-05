package com.jaysato.xiaomihealthbridge

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgSensorProfileFingerprintTest {
    @Test
    fun fingerprintIsStableAndChangesWithReportedSensorProfile() {
        val base = snapshot()

        val fingerprint = EcgSensorProfileFingerprint.create(base)

        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.matches(Regex("[0-9a-f]{64}")))
        assertEquals(fingerprint, EcgSensorProfileFingerprint.create(base.copy()))
        assertNotEquals(fingerprint, EcgSensorProfileFingerprint.create(base, "future-decoder-version"))
        assertNotEquals(
            fingerprint,
            EcgSensorProfileFingerprint.create(base.copy(sensorName = "different-ecg-source"))
        )
        assertNotEquals(
            fingerprint,
            EcgSensorProfileFingerprint.create(base.copy(sampleRateHz = 250.0))
        )
        assertNotEquals(
            fingerprint,
            EcgSensorProfileFingerprint.create(base.copy(halSamplePeriodSeconds = 0.002))
        )
    }

    private fun snapshot(
        sensorName: String = "ecg_sensor",
        sampleRateHz: Double = 0.0,
        halSamplePeriodSeconds: Double? = null
    ) = RawVitalsProbe.Snapshot(
        kind = RawVitalsProbe.Kind.ECG,
        sensorName = sensorName,
        vendor = "sensor vendor",
        reportingMode = "continuous",
        samples = 0,
        eventCount = 1,
        sampleRateHz = sampleRateHz,
        channels = 1,
        accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
        minimum = null,
        maximum = null,
        average = null,
        latest = emptyList(),
        waveform = FloatArray(0),
        worn = null,
        status = "has_data",
        reason = "test",
        halSamplePeriodSeconds = halSamplePeriodSeconds
    )
}
