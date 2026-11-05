package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Volume
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StandardHealthWriter(private val context: Context) {
    private val client = HealthConnectClient.getOrCreate(context)

    suspend fun write(
        metric: String,
        value: Double,
        startMillis: Long,
        endMillis: Long,
        routePoints: Array<String> = emptyArray()
    ): Result<Unit> = mutex.withLock { runCatching {
        require(value > 0.0 && endMillis > startMillis)
        val start = Instant.ofEpochMilli(startMillis)
        val end = Instant.ofEpochMilli(endMillis)
        val zone = ZoneId.systemDefault().rules.getOffset(start)
        val id = "xiaomi-watch-2-pro-$metric-$startMillis-$endMillis"
        val metadata = Metadata.autoRecorded(
            device = Device(Device.TYPE_WATCH, "Xiaomi", "Watch 2 Pro"),
            clientRecordId = id
        )
        val record: Record = when (metric) {
            "steps" -> StepsRecord(start, zone, end, zone, value.toLong(), metadata)
            "distance" -> DistanceRecord(start, zone, end, zone, Length.meters(value), metadata)
            "calories" -> createTotalCaloriesRecord(start, zone, end, Energy.kilocalories(value), metadata)
            "oxygen" -> {
                require(value in 70.0..100.0)
                OxygenSaturationRecord(start, zone, Percentage(value), metadata)
            }
            "elevation" -> ElevationGainedRecord(start, zone, end, zone, Length.meters(value), metadata)
            "floors" -> FloorsClimbedRecord(start, zone, end, zone, value, metadata)
            "hydration" -> HydrationRecord(start, zone, end, zone, Volume.milliliters(value), metadata)
            "exercise" -> {
                val granted = client.permissionController.getGrantedPermissions()
                val route = if (
                    HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE in granted &&
                    routePoints.size >= 2
                ) {
                    ExerciseRoute(routePoints.mapNotNull { encoded ->
                        val parts = encoded.split(',')
                        val time = parts.getOrNull(0)?.toLongOrNull()?.let(Instant::ofEpochMilli) ?: return@mapNotNull null
                        val latitude = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                        val longitude = parts.getOrNull(2)?.toDoubleOrNull() ?: return@mapNotNull null
                        val altitude = parts.getOrNull(3)?.toDoubleOrNull()
                        if (time < start || time >= end || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) null
                        else ExerciseRoute.Location(
                            time = time,
                            latitude = latitude,
                            longitude = longitude,
                            altitude = altitude?.takeIf { it.isFinite() }?.let { Length.meters(it) }
                        )
                    }).takeIf { it.route.size >= 2 }
                } else null
                ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = zone,
                endTime = end,
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(end),
                metadata = metadata,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                title = "Xiaomi Watch 2 Pro 步行",
                exerciseRoute = route
                )
            }
            else -> error("Unsupported metric: $metric")
        }
        val recordType = record::class as KClass<out Record>
        val permission = HealthPermission.getWritePermission(recordType)
        check(permission in client.permissionController.getGrantedPermissions()) { "$metric 寫入權限未授予" }
        client.insertRecords(listOf(record))
        if (metric == "steps") {
            context.getSharedPreferences(RestingHeartRateEstimator.PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(RestingHeartRateEstimator.KEY_STEP_SOURCE_SEEN, true)
                .putLong(RestingHeartRateEstimator.KEY_LAST_STEP_END, endMillis)
                .apply()
        }
    } }

    companion object {
        private val mutex = Mutex()
    }
}

internal fun createTotalCaloriesRecord(
    start: Instant,
    zone: java.time.ZoneOffset,
    end: Instant,
    energy: Energy,
    metadata: Metadata
): TotalCaloriesBurnedRecord = TotalCaloriesBurnedRecord(start, zone, end, zone, energy, metadata)
