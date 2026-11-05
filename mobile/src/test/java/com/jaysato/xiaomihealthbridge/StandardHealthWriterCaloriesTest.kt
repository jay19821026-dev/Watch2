package com.jaysato.xiaomihealthbridge

import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class StandardHealthWriterCaloriesTest {
    @Test
    fun healthServicesCaloriesAreWrittenAsTotalIncludingBasalRecord() {
        val record = createTotalCaloriesRecord(
            Instant.ofEpochMilli(1_000L),
            ZoneOffset.UTC,
            Instant.ofEpochMilli(2_000L),
            Energy.kilocalories(12.0),
            Metadata.autoRecorded(
                device = Device(Device.TYPE_WATCH, "Xiaomi", "Watch 2 Pro"),
                clientRecordId = "workout-calories-test"
            )
        )

        assertEquals(12.0, record.energy.inKilocalories, 0.001)
    }
}
