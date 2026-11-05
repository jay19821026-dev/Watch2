package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorAcquisitionDiagnosticStateTest {
    @Test
    fun preservesSpecificSensorRegistrationFailureReasons() {
        assertEquals(
            SensorAcquisitionDiagnosticState.PERMISSION_DENIED,
            classify("permission_denied", 100L, 100L, 0, 0)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.REGISTRATION_EXCEPTION,
            classify("registration_exception", 100L, 100L, 0, 0)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.REGISTRATION_FAILED,
            classify("registration_failed", 100L, 100L, 0, 0)
        )
    }

    @Test
    fun classifiesMissingAndMismatchedDiagnosticsWithoutCallingThemTransportFailures() {
        assertEquals(
            SensorAcquisitionDiagnosticState.NOT_RECORDED,
            classify("unknown", 0L, 100L, 0, 0)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.DIFFERENT_SESSION,
            classify("registered", 99L, 100L, 10, 1)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.DIFFERENT_SESSION,
            classify("registered", 99L, 100L, 10, 1, 99L)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.DIFFERENT_SESSION,
            SensorAcquisitionDiagnosticState.classify("registered", 100L, 100L, 10, 1, 100L, "suite-a", "suite-b")
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.ROW_SESSION_UNKNOWN,
            classify("registered", 100L, 0L, 10, 1, 100L)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.PHONE_RECEIPT_UNCONFIRMED,
            classify("registered", 100L, 100L, 10, 0, 100L)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.PHONE_RECEIPT_UNCONFIRMED,
            classify("registered", 100L, 100L, 10, 1, 99L)
        )
    }

    @Test
    fun classifiesHardwareRegistrationEventsAndReceiptStages() {
        assertEquals(
            SensorAcquisitionDiagnosticState.SKIPPED_CHARGING,
            classify("skipped_charging", 100L, 100L, 0, 0, 100L)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.SENSOR_NOT_EXPOSED,
            classify("sensor_not_exposed", 100L, 100L, 0, 0, 100L)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.REGISTRATION_FAILED,
            classify("registration_failed", 100L, 100L, 0, 0, 100L)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.NO_EVENTS,
            classify("registered", 100L, 100L, 0, 0, 100L)
        )
        assertEquals(
            SensorAcquisitionDiagnosticState.SNAPSHOT_RECEIVED,
            classify("registered", 100L, 100L, 10, 1, 100L)
        )
    }

    private fun classify(
        registration: String,
        watchSession: Long,
        rowSession: Long,
        events: Int,
        received: Int,
        phoneSession: Long = 0L
    ) = SensorAcquisitionDiagnosticState.classify(registration, watchSession, rowSession, events, received, phoneSession)
}
