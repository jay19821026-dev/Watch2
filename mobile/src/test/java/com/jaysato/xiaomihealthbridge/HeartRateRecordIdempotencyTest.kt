package com.jaysato.xiaomihealthbridge

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HeartRateRecordIdempotencyTest {
    @Test
    fun replayDeduplicatesMinuteRecordAndNewerMeasurementVersionUpdatesIt() = runBlocking {
        val firstTime = 1_700_000_000_010L
        val newerTime = firstTime + 20_000L
        val client = FakeHealthConnectClient(
            "com.jaysato.xiaomihealthbridge.test",
            Clock.fixed(Instant.ofEpochMilli(newerTime + 10_000L), ZoneOffset.UTC),
            FakePermissionController(true)
        )
        val device = Device(Device.TYPE_WATCH, "Xiaomi", "Watch 2 Pro")
        val firstRecord = HeartRateRecordFactory.create(mapOf(firstTime to 61L), device).single()

        client.insertRecords(listOf(firstRecord))
        client.insertRecords(listOf(firstRecord))
        client.insertRecords(HeartRateRecordFactory.create(mapOf(newerTime to 74L), device))

        val stored = client.readRecords(
            ReadRecordsRequest(
                HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(firstTime - 1L),
                    Instant.ofEpochMilli(newerTime + 10L)
                )
            )
        ).records

        assertEquals(1, stored.size)
        assertEquals(74L, stored.single().samples.single().beatsPerMinute)
        assertEquals(newerTime, stored.single().metadata.clientRecordVersion)
    }
}
