package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Percentage
import java.time.Instant
import java.time.ZoneId

class ManualEntryWriter(context: Context) {
    private val client = HealthConnectClient.getOrCreate(context)

    suspend fun writeWeight(kilograms: Double): Result<Unit> = runCatching {
        require(kilograms in 1.0..500.0) { "體重必須介於 1–500 kg" }
        requirePermission(HealthPermission.getWritePermission(WeightRecord::class))
        val now = Instant.now()
        client.insertRecords(listOf(WeightRecord(
            time = now,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(now),
            weight = Mass.kilograms(kilograms),
            metadata = Metadata.manualEntry("manual-weight-${now.toEpochMilli()}")
        )))
    }

    suspend fun writeBloodPressure(systolic: Double, diastolic: Double): Result<Unit> = runCatching {
        require(systolic in 40.0..300.0) { "收縮壓必須介於 40–300 mmHg" }
        require(diastolic in 30.0..200.0) { "舒張壓必須介於 30–200 mmHg" }
        require(systolic > diastolic) { "收縮壓必須高於舒張壓" }
        requirePermission(HealthPermission.getWritePermission(BloodPressureRecord::class))
        val now = Instant.now()
        client.insertRecords(listOf(BloodPressureRecord(
            time = now,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(now),
            metadata = Metadata.manualEntry("manual-blood-pressure-${now.toEpochMilli()}"),
            systolic = Pressure.millimetersOfMercury(systolic),
            diastolic = Pressure.millimetersOfMercury(diastolic),
            bodyPosition = BloodPressureRecord.BODY_POSITION_SITTING_DOWN,
            measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN
        )))
    }

    suspend fun writeRestingHeartRate(beatsPerMinute: Long): Result<Unit> = runCatching {
        require(beatsPerMinute in 20..250) { "靜止心率必須介於 20–250 bpm" }
        requirePermission(HealthPermission.getWritePermission(RestingHeartRateRecord::class))
        val now = Instant.now()
        client.insertRecords(listOf(RestingHeartRateRecord(
            time = now,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(now),
            beatsPerMinute = beatsPerMinute,
            metadata = Metadata.manualEntry("manual-resting-heart-rate-${now.toEpochMilli()}")
        )))
    }

    suspend fun writeHrvRmssd(milliseconds: Double): Result<Unit> = runCatching {
        require(milliseconds in 1.0..200.0) { "HRV RMSSD 必須介於 1–200 ms" }
        requirePermission(HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class))
        val now = Instant.now()
        client.insertRecords(listOf(HeartRateVariabilityRmssdRecord(
            time = now,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(now),
            heartRateVariabilityMillis = milliseconds,
            metadata = Metadata.manualEntry("manual-hrv-rmssd-${now.toEpochMilli()}")
        )))
    }

    suspend fun writeRespiratoryRate(breathsPerMinute: Double): Result<Unit> = runCatching {
        require(breathsPerMinute in 1.0..100.0) { "呼吸速率必須介於 1–100 次／分鐘" }
        requirePermission(HealthPermission.getWritePermission(RespiratoryRateRecord::class))
        val now = Instant.now()
        client.insertRecords(listOf(RespiratoryRateRecord(
            time = now,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(now),
            rate = breathsPerMinute,
            metadata = Metadata.manualEntry("manual-respiratory-rate-${now.toEpochMilli()}")
        )))
    }

    suspend fun writeOxygenSaturation(percentage: Double): Result<Unit> = runCatching {
        require(percentage in 50.0..100.0) { "血氧必須介於 50–100%" }
        requirePermission(HealthPermission.getWritePermission(OxygenSaturationRecord::class))
        val now = Instant.now()
        client.insertRecords(listOf(OxygenSaturationRecord(
            time = now,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(now),
            percentage = Percentage(percentage),
            metadata = Metadata.manualEntry("manual-oxygen-saturation-${now.toEpochMilli()}")
        )))
    }

    suspend fun writeSleepDuration(hours: Double): Result<Unit> = runCatching {
        require(hours in 0.25..24.0) { "睡眠時數必須介於 0.25–24 小時" }
        requirePermission(HealthPermission.getWritePermission(SleepSessionRecord::class))
        val end = Instant.now()
        val start = end.minusMillis((hours * 3_600_000).toLong())
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(end)
        client.insertRecords(listOf(SleepSessionRecord(
            startTime = start,
            startZoneOffset = zoneOffset,
            endTime = end,
            endZoneOffset = zoneOffset,
            title = "手動睡眠紀錄",
            notes = "使用者手動輸入；未提供或推算睡眠階段",
            stages = emptyList(),
            metadata = Metadata.manualEntry("manual-sleep-${end.toEpochMilli()}")
        )))
    }

    private suspend fun requirePermission(permission: String) {
        check(permission in client.permissionController.getGrantedPermissions()) { "Health Connect 寫入權限未授予" }
    }
}
