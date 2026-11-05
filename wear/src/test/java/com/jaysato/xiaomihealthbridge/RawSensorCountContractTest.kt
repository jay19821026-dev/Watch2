package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RawSensorCountContractTest {
    @Test
    fun keepsRawEventsAndRetainedPreviewAsSeparateCounts() {
        val fields = RawSensorCountContract.fields(
            samples = 3_976,
            rawValueCount = 3_976,
            previewPointCount = 512,
            eventCount = 497
        )

        assertEquals(3_976, fields["samples"])
        assertEquals(3_976, fields["raw_value_count"])
        assertEquals(512, fields["preview_point_count"])
        assertEquals(497, fields["event_count"])
        assertEquals(2, fields["count_contract_version"])
    }

    @Test
    fun missingRawCountStaysAbsentAndExplicitZeroStaysZero() {
        val unknown = RawSensorCountContract.fields(0, null, 4, 4)
        val zero = RawSensorCountContract.fields(0, 0, 4, 4)

        assertFalse(unknown.containsKey("raw_value_count"))
        assertEquals(0, zero["raw_value_count"])
        assertEquals(4, unknown["event_count"])
        assertEquals(4, unknown["preview_point_count"])
    }

    @Test
    fun derivedSnapshotsDoNotClaimFiniteRawValues() {
        val snapshot = RawVitalsProbe.derived(
            kind = RawVitalsProbe.Kind.PPG,
            values = listOf(72.0),
            sensorName = "aliased-source",
            vendor = "HealthBridge",
            reason = "derived test fixture"
        )
        val fields = RawSensorCountContract.fields(
            snapshot.samples,
            snapshot.finiteValueCount,
            snapshot.waveform.size,
            snapshot.eventCount
        )

        assertEquals("derived", snapshot.reportingMode)
        assertEquals(1, fields["samples"])
        assertFalse(fields.containsKey("raw_value_count"))
        assertEquals(0, fields["event_count"])
    }

    @Test
    fun emptyDerivedSnapshotDoesNotClaimSamplesOrChannels() {
        val snapshot = RawVitalsProbe.derived(
            kind = RawVitalsProbe.Kind.PPG,
            values = emptyList(),
            sensorName = "aliased-source",
            vendor = "HealthBridge",
            reason = "empty derived fixture"
        )

        assertEquals(0, snapshot.samples)
        assertEquals(0, snapshot.channels)
        assertEquals(0, snapshot.waveform.size)
        assertEquals(0, snapshot.eventCount)
    }
}
