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
                value == null -> "今日尚無飲水紀錄；可補登實際飲水量。不能據此判定脫水。"
                !value.isFinite() || value < 0 -> "飲水紀錄讀值無效，請核對原始紀錄。"
                origins.size != 1 -> "飲水紀錄含多來源或來源不明，需先核對重複紀錄。"
                !accepted -> "飲水彙總與原始紀錄不一致，需先核對；不把部分資料當成今日完整攝取。"
                else -> "今日已記錄 ${"%.0f".format(value)} mL\n來源 ${origins.single()}；紀錄量不等同全部攝取量，請依醫療建議補水。"
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            "補水資料讀取失敗（${error.javaClass.simpleName}）；其他分析可繼續。"
        }
    }
}
