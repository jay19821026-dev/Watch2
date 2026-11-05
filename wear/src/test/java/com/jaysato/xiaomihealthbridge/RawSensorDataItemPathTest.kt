package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSensorDataItemPathTest {
    @Test
    fun sameKindAndMillisecondStillProduceDistinctSnapshotPaths() {
        val preview = RawSensorDataItemPath.create("ppg", 1_000L, "preview")
        val final = RawSensorDataItemPath.create("ppg", 1_000L, "final")

        assertNotEquals(preview, final)
        assertTrue(preview.startsWith("/raw-sensor/ppg/1000-"))
        assertTrue(final.startsWith("/raw-sensor/ppg/1000-"))
    }
}
