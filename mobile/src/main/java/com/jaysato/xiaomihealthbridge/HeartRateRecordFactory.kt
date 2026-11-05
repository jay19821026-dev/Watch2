package com.jaysato.xiaomihealthbridge

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneId

internal object HeartRateRecordFactory {
    fun create(samples: Map<Long, Long>, device: Device): List<HeartRateRecord> =
        samples.toSortedMap().map { (sampleTimeMillis, bpm) ->
            val instant = Instant.ofEpochMilli(sampleTimeMillis)
            val offset = ZoneId.systemDefault().rules.getOffset(instant)
            HeartRateRecord(
                startTime = instant,
                startZoneOffset = offset,
                endTime = instant.plusMillis(1),
                endZoneOffset = offset,
                samples = listOf(HeartRateRecord.Sample(instant, bpm)),
                metadata = Metadata.autoRecorded(
                    device,
                    HeartRateQueuePolicy.clientRecordId(sampleTimeMillis),
                    HeartRateQueuePolicy.clientRecordVersion(sampleTimeMillis)
                )
            )
        }
}
