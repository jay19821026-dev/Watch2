package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

object AiHydrationSummary {
    suspend fun read(context: Context): String {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            if (HealthPermission.getReadPermission(HydrationRecord::class) !in client.permissionController.getGrantedPermissions()) {
                return "補水紀錄尚未授權；請在資料與權限開放。未記錄不代表未喝水。"
            }
            val now = Instant.now()
            val start = now.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val result = client.aggregate(AggregateRequest(setOf(HydrationRecord.VOLUME_TOTAL), TimeRangeFilter.between(start, now)))
            val value = result[HydrationRecord.VOLUME_TOTAL]?.inMilliliters
            val origins = result.dataOrigins.map { it.packageName }.filter(String::isNotBlank).distinct()
            val records = client.readAllRecords(HydrationRecord::class, TimeRangeFilter.between(start, now))
                .filter { it.startTime >= start && it.endTime <= now && it.volume.inMilliliters.isFinite() && it.volume.inMilliliters > 0 }
            val recordTotal = records.takeIf { it.isNotEmpty() }?.sumOf { it.volume.inMilliliters }
            val accepted = HealthConnectAggregatePolicy.acceptsSingleOriginRecordAggregate(
                value, origins, recordTotal, records.map { it.metadata.dataOrigin.packageName }
            )
            when {
                value == null -> "今天尚無飲水紀錄；可補登喝水量。"
                !value.isFinite() || value < 0 -> "這次飲水紀錄有誤，請檢查後重試。"
                origins.size != 1 -> "飲水紀錄可能重複，請檢查來源後重試。"
                !accepted -> "飲水紀錄有重複或缺漏，請檢查後重試。"
                else -> "今天已記錄飲水 ${"%.0f".format(value)} mL。這只包含已登錄的飲水量。"
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            "飲水紀錄暫時讀不到；其他健康結果仍可查看。"
        }
    }
}
