package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorAcquisitionDiagnosticExportPolicyTest {
    @Test
    fun sessionAndAvailableSuiteIdentityMustMatch() {
        assertTrue(SensorAcquisitionDiagnosticExportPolicy.matchesRowSession(123L, 123L, "", ""))
        assertTrue(SensorAcquisitionDiagnosticExportPolicy.matchesRowSession(123L, 123L, "suite-a", "suite-a"))
        assertFalse(SensorAcquisitionDiagnosticExportPolicy.matchesRowSession(123L, 124L, "", ""))
        assertFalse(SensorAcquisitionDiagnosticExportPolicy.matchesRowSession(0L, 123L, "", ""))
        assertFalse(SensorAcquisitionDiagnosticExportPolicy.matchesRowSession(123L, 0L, "", ""))
        assertFalse(SensorAcquisitionDiagnosticExportPolicy.matchesRowSession(123L, 123L, "suite-a", "suite-b"))
        assertFalse(SensorAcquisitionDiagnosticExportPolicy.matchesRowSession(123L, 123L, "suite-a", ""))
    }
}
