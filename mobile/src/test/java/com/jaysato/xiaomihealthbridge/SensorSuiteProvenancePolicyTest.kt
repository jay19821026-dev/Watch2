package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorSuiteProvenancePolicyTest {
    @Test
    fun onlyExportsWatchContactProvenanceForTheMatchingMeasurementSession() {
        assertTrue(SensorSuiteProvenancePolicy.belongsToSession(100L, 100L))
        assertFalse(SensorSuiteProvenancePolicy.belongsToSession(101L, 100L))
        assertFalse(SensorSuiteProvenancePolicy.belongsToSession(100L, 0L))
        assertFalse(SensorSuiteProvenancePolicy.belongsToSession(0L, 100L))
    }

    @Test
    fun provenanceUsesOneNormalizedSourceAndKeepsDerivedExplicit() {
        assertEquals("hardware_event", SensorSuiteProvenancePolicy.dataProvenance("raw", ""))
        assertEquals("hardware_event", SensorSuiteProvenancePolicy.dataProvenance(null, null))
        assertEquals("derived_estimate", SensorSuiteProvenancePolicy.dataProvenance("derived", "hardware_event"))
        assertEquals("vendor_raw", SensorSuiteProvenancePolicy.dataProvenance("raw", "vendor_raw"))
    }
}
