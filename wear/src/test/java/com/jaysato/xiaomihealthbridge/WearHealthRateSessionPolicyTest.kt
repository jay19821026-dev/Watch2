package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearHealthRateSessionPolicyTest {
    @Test
    fun bindsHealthServiceSnapshotToActiveSuite() {
        assertEquals(
            WearHealthRateSessionMetadata(500L, "suite-1", 500L),
            WearHealthRateSessionPolicy.metadataForSnapshot("suite-1", 500L, 525L)
        )
    }

    @Test
    fun standaloneSnapshotUsesMeasurementTimeWithoutInheritingSuite() {
        val metadata = WearHealthRateSessionPolicy.metadataForSnapshot("", 0L, 525L)

        assertEquals(525L, metadata.sessionStartedAtMs)
        assertNull(metadata.suiteId)
        assertEquals(0L, metadata.suiteStartedAtMs)
    }

    @Test
    fun invalidSuiteMetadataDoesNotLeakAndInvalidSampleTimeIsNotFabricated() {
        val zeroStart = WearHealthRateSessionPolicy.metadataForSnapshot("suite-1", 0L, 525L)
        val invalidSample = WearHealthRateSessionPolicy.metadataForSnapshot(null, 0L, -1L)
        val sampleBeforeSuite = WearHealthRateSessionPolicy.metadataForSnapshot("suite-1", 500L, 499L)

        assertEquals(525L, zeroStart.sessionStartedAtMs)
        assertNull(zeroStart.suiteId)
        assertEquals(0L, zeroStart.suiteStartedAtMs)
        assertEquals(-1L, invalidSample.sessionStartedAtMs)
        assertNull(invalidSample.suiteId)
        assertEquals(0L, invalidSample.suiteStartedAtMs)
        assertEquals(499L, sampleBeforeSuite.sessionStartedAtMs)
        assertNull(sampleBeforeSuite.suiteId)
        assertEquals(0L, sampleBeforeSuite.suiteStartedAtMs)
    }
}
