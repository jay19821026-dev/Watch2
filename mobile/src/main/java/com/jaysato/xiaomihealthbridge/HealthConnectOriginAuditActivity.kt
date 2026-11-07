package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

/** Local provenance audit inspired by the public Health Connect path found in Mi Fitness 3.53.0i. */
class HealthConnectOriginAuditActivity : ComponentActivity() {
    private lateinit var result: TextView
    private var auditJob: Job? = null
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        .withZone(ZoneId.systemDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(Color.rgb(8, 10, 16))
        }
        column.addView(TextView(this).apply {
            text = "Health Connect 資料來源核對"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        column.addView(TextView(this).apply {
            text = "只讀取近七天已授權紀錄的數量、來源 App 與最新時間；不顯示健康數值，也不送到伺服器。來源 App 不等於感測器型號或臨床校準證明。"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(12), 0, dp(16))
        })
        column.addView(Button(this).apply {
            text = "重新核對"
            isAllCaps = false
            setOnClickListener { refresh() }
        })
        result = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.START
            setPadding(0, dp(16), 0, 0)
        }
        column.addView(result)
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        auditJob?.cancel()
        result.text = "正在核對權限與來源…"
        auditJob = lifecycleScope.launch {
            val sdkStatus = HealthConnectClient.getSdkStatus(this@HealthConnectOriginAuditActivity)
            if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                result.text = "Health Connect 尚不可用（狀態 $sdkStatus）。請檢查手機的健康資料服務。"
                return@launch
            }
            val client = HealthConnectClient.getOrCreate(this@HealthConnectOriginAuditActivity)
            val granted = try {
                client.permissionController.getGrantedPermissions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                result.text = "權限狀態查詢失敗：${error.javaClass.simpleName}。未嘗試讀取紀錄。"
                return@launch
            }
            val end = Instant.now()
            val filter = TimeRangeFilter.between(end.minusSeconds(7L * 24 * 60 * 60), end)
            val lines = mutableListOf("查詢時間：${dateFormat.format(end)}", "範圍：最近七天；每類最多檢查 2,000 筆", "")

            suspend fun <T : Record> inspect(label: String, type: KClass<T>, time: (T) -> Instant) {
                if (HealthPermission.getReadPermission(type) !in granted) {
                    lines += "$label：未授權；請在本 App 的 Health Connect 權限頁允許後重試。"
                    return
                }
                try {
                    val records = client.readAllRecords(type, filter, maxRecords = 2_000)
                    if (records.isEmpty()) {
                        lines += "$label：已授權，近七天沒有可讀紀錄。"
                        return
                    }
                    lines += "$label：${records.size} 筆；來源 App ${records.map { it.metadata.dataOrigin.packageName }.distinct().size} 個。"
                    records.groupBy { it.metadata.dataOrigin.packageName }.toSortedMap().forEach { (origin, items) ->
                        val latest = items.maxOf(time)
                        val futureWarning = if (latest.isAfter(end)) "（時間晚於查詢時間，請核對來源時鐘）" else ""
                        lines += "  ${origin.ifBlank { "來源未標示" }}：${items.size} 筆；最新 ${dateFormat.format(latest)}$futureWarning"
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: HealthConnectReadLimitException) {
                    lines += "$label：紀錄超過 2,000 筆上限；本次未提供不完整來源統計。"
                } catch (error: Exception) {
                    lines += "$label：讀取失敗（${error.javaClass.simpleName}）；不視為沒有紀錄。"
                }
            }

            inspect("心率", HeartRateRecord::class) { it.endTime }
            inspect("血氧", OxygenSaturationRecord::class) { it.time }
            inspect("步數", StepsRecord::class) { it.endTime }
            inspect("睡眠", SleepSessionRecord::class) { it.endTime }
            inspect("距離", DistanceRecord::class) { it.endTime }
            inspect("活動熱量", ActiveCaloriesBurnedRecord::class) { it.endTime }
            lines += ""
            lines += "Mi Fitness 3.53.0i 的反組譯只證實有 Health Connect 寫入路徑；只有上方實際來源列能證實本手機有可讀紀錄。"
            result.text = lines.joinToString("\n")
        }
    }
}
