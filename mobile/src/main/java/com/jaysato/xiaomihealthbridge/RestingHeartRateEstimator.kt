package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

class RestingHeartRateEstimator(private val context: Context) {
    private val client = HealthConnectClient.getOrCreate(context)

    suspend fun update(epochMillis: Long): Result<Long?> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_STEP_SOURCE_SEEN, false)) return@runCatching null
        val windowEnd = Instant.ofEpochMilli(epochMillis)
        val windowStart = windowEnd.minusSeconds(WINDOW_SECONDS)
        if (prefs.getLong(KEY_LAST_STEP_END, Long.MAX_VALUE) > windowStart.toEpochMilli()) return@runCatching null

        val granted = client.permissionController.getGrantedPermissions()
        check(HealthPermission.getReadPermission(HeartRateRecord::class) in granted)
        check(HealthPermission.getWritePermission(RestingHeartRateRecord::class) in granted)
        val samples = client.readAllRecords(
            HeartRateRecord::class,
            TimeRangeFilter.between(windowStart, windowEnd.plusMillis(1)),
            dataOriginFilter = setOf(DataOrigin(context.packageName))
        )
            .flatMap { it.samples }
            .sortedBy { it.time }
        if (samples.size < MIN_SAMPLES) return@runCatching null
        if (samples.last().time.epochSecond - samples.first().time.epochSecond < MIN_COVERAGE_SECONDS) return@runCatching null
        if (samples.zipWithNext().any { (first, second) ->
                second.time.epochSecond - first.time.epochSecond > MAX_GAP_SECONDS
            }) return@runCatching null

        val values = samples.map { it.beatsPerMinute.toDouble() }
        if (values.any { it > MAX_RESTING_BPM }) return@runCatching null
        if (values.max() - values.min() > MAX_RANGE_BPM) return@runCatching null
        val mean = values.average()
        val standardDeviation = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        if (standardDeviation > MAX_STANDARD_DEVIATION) return@runCatching null
        val estimated = mean.toLong().coerceIn(20, 250)
        val zoneId = ZoneId.systemDefault()
        val day = windowEnd.atZone(zoneId).toLocalDate()
        client.insertRecords(listOf(RestingHeartRateRecord(
            time = windowEnd,
            zoneOffset = zoneId.rules.getOffset(windowEnd),
            beatsPerMinute = estimated,
            metadata = Metadata.autoRecorded(
                device = Device(Device.TYPE_WATCH, "Xiaomi", "Watch 2 Pro"),
                clientRecordId = "xiaomi-watch-2-pro-estimated-resting-heart-rate-$day",
                clientRecordVersion = epochMillis
            )
        )))
        estimated
    }

    companion object {
        const val PREFS = "resting_heart_rate_estimator"
        const val KEY_STEP_SOURCE_SEEN = "step_source_seen"
        const val KEY_LAST_STEP_END = "last_step_end"
        private const val WINDOW_SECONDS = 8 * 60L
        private const val MIN_COVERAGE_SECONDS = 4 * 60L
        private const val MAX_GAP_SECONDS = 3 * 60L
        private const val MIN_SAMPLES = 4
        private const val MAX_RANGE_BPM = 15.0
        private const val MAX_STANDARD_DEVIATION = 7.0
        private const val MAX_RESTING_BPM = 100.0
    }
}
