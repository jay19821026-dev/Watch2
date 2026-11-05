package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorSuiteEventCountExportTest {
    @Test
    fun unknownNormalizedZeroRemainsUnknownAndKeepsReportedFutureValueSeparate() {
        val result = SensorSuiteExportPolicy.eventCountExport(
            normalizedCount = 0,
            reportedCount = 8,
            known = false
        )

        assertNull(result.standardCount)
        assertEquals(8, result.reportedCount)
        assertFalse(result.isKnown)
    }

    @Test
    fun explicitKnownZeroRemainsZeroInExport() {
        val result = SensorSuiteExportPolicy.eventCountExport(
            normalizedCount = 0,
            reportedCount = 0,
            known = true
        )

        assertEquals(0, result.standardCount)
        assertEquals(0, result.reportedCount)
        assertTrue(result.isKnown)
    }
}
