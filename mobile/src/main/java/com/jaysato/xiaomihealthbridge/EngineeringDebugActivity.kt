package com.jaysato.xiaomihealthbridge

import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageInfo
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CancellationException

class EngineeringDebugActivity : ComponentActivity() {
    private lateinit var reportView: TextView
    private var latestReport = JSONObject()
    private var diagnosticGeneration = 0
    private var scanCommandAcceptedAt = 0L
    private val delayedCallbacks = mutableSetOf<Runnable>()
    private data class PendingWatchStatusReconciliation(
        val requestId: String,
        val deadlineAt: Long,
        val timeoutState: String,
        val timeoutMessage: String
    )
    private var pendingWatchResponseReconciliation: PendingWatchStatusReconciliation? = null
    private var retryNodeLookupAfterRestore = false
    private val pendingShellExportDeadlines = sortedSetOf<Long>()
    private val watchStatusPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == HeartRateDataListenerService.KEY_DEBUG_STATUS_REQUEST_ID ||
            key == HeartRateDataListenerService.KEY_DEBUG_STATUS_RECEIVED_AT
        ) {
            runOnUiThread {
                if (::reportView.isInitialized && restoreCorrelatedWatchResponse(latestReport.optString("watch_status_request_id"))) {
                    renderReport()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanupExpiredDiagnosticExports()
        savedInstanceState?.getString(STATE_REPORT)?.let { encoded ->
            latestReport = runCatching { JSONObject(encoded) }.getOrElse { JSONObject() }
            val requestState = latestReport.optString("watch_status_request")
            if (WatchStatusRequestState.shouldRetryNodeLookup(requestState)) {
                retryNodeLookupAfterRestore = true
            } else if (WatchStatusRequestState.isAwaitingResponse(requestState)) {
                val requestId = latestReport.optString("watch_status_request_id")
                if (!restoreCorrelatedWatchResponse(requestId)) {
                    val deadline = WatchStatusRequestState.timeoutDeadline(
                        state = requestState,
                        requestStartedAt = WatchStatusRequestState.restoredElapsedTimestamp(
                            latestReport.optLong("watch_status_request_started_elapsed_at"),
                            latestReport.optLong("watch_status_request_started_at"),
                            SystemClock.elapsedRealtime(),
                            System.currentTimeMillis()
                        ) ?: 0L,
                        messageAcceptedAt = WatchStatusRequestState.restoredElapsedTimestamp(
                            latestReport.optLong("watch_message_accepted_elapsed_at"),
                            latestReport.optLong("watch_message_accepted_at"),
                            SystemClock.elapsedRealtime(),
                            System.currentTimeMillis()
                        ) ?: 0L,
                        sendTimeoutMs = WATCH_SEND_TIMEOUT_MS,
                        responseTimeoutMs = WATCH_STATUS_TIMEOUT_MS
                    )
                    latestReport.put("watch_status_request", "interrupted_by_activity_recreation")
                    latestReport.put("watch_status", "頁面重建中斷了狀態等待；請手動重新檢查，避免重複送出要求")
                    if (requestId.isNotBlank() && deadline != null) {
                        val sending = requestState == "sending_status_request"
                        pendingWatchResponseReconciliation = PendingWatchStatusReconciliation(
                            requestId,
                            deadline,
                            if (sending) "send_task_timeout" else "no_correlated_response",
                            if (sending) "Data Layer 傳送工作逾時；沒有確認訊息是否送達" else "未收到本次請求 ID 對應的手錶回覆"
                        )
                    }
                }
            }
        }
        savedInstanceState?.getLongArray(STATE_SHELL_EXPORT_DEADLINES)?.forEach(pendingShellExportDeadlines::add)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(Color.rgb(8, 10, 16))
        }
        root.addView(text("工程 Debug", 28f, Color.WHITE, true))
        root.addView(text("分層定位：手機／手錶／感測器／同步／儲存／呈現", 13f, 0xFF9EA5B5.toInt()).apply {
            setPadding(0, dp(6), 0, dp(16))
        })
        root.addView(action("查詢手機＋手錶狀態（不啟動感測器）") { runDiagnosis(requestWatch = true) })
        root.addView(action("主動測試：手錶完整全感測器掃描") {
            android.app.AlertDialog.Builder(this)
                .setTitle("主動感測測試")
                .setMessage("將啟動手錶既有完整感測器掃描（整體最長約 7 分鐘，部分階段各約 12 秒），會使用感測器、Health Services 與前景服務，並消耗電力。依現有同步邏輯，已授權且符合條件的心率可能寫入 Health Connect；原始研究波形留在 App 診斷資料中。掃描結果不代表 OEM 校準或醫療有效。要開始嗎？")
                .setNegativeButton("取消", null)
                .setPositiveButton("同意掃描並可能寫入心率") { _, _ -> startWatchProbe() }
                .show()
        })
        root.addView(action("預覽／分享脫敏診斷摘要") { shareReport() })
        root.addView(action("匯出完整感測器資料（本機 ADB）") { confirmRawExport() })
        root.addView(text("狀態標記：已觀察＝API／App 實際回報；未知＝一般 App 無法觀察；不會推測晶片內部狀態。", 12f, 0xFF8D96AA.toInt()).apply {
            setPadding(0, dp(14), 0, dp(14))
        })
        reportView = text("正在檢查手機狀態並向手錶要求最新狀態；不會啟動感測器。", 14f, 0xFFE0E4ED.toInt())
        reportView.setPadding(dp(16), dp(16), dp(16), dp(16))
        reportView.background = HealthVisuals.panel(this)
        root.addView(reportView)
        setContentView(ScrollView(this).apply {
            addView(root)
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        })
        if (savedInstanceState == null) {
            val shellScan = intent.getBooleanExtra(EXTRA_START_SENSOR_SUITE, false)
            val shellExport = intent.getBooleanExtra(EXTRA_EXPORT_RAW_ON_OPEN, false)
            if (shellScan) startWatchProbe()
            else runDiagnosis(requestWatch = true)
            if (shellExport) {
                scheduleShellExport(SHELL_EXPORT_DELAY_MS)
                if (shellScan) scheduleShellExport(SHELL_SCAN_EXPORT_DELAY_MS)
            }
        } else {
            if (retryNodeLookupAfterRestore) runDiagnosis(requestWatch = true) else renderReport()
            pendingWatchResponseReconciliation?.let { pending ->
                val generation = diagnosticGeneration
                postTrackedDelayed((pending.deadlineAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)) {
                    if (!isCurrentRequest(generation) || latestReport.optString("watch_status_request_id") != pending.requestId) return@postTrackedDelayed
                    if (!restoreCorrelatedWatchResponse(pending.requestId) && latestReport.optString("watch_status_request") == "interrupted_by_activity_recreation") {
                        latestReport.put("watch_status_request", pending.timeoutState)
                        latestReport.put("watch_status", pending.timeoutMessage)
                    }
                    renderReport()
                }
            }
            pendingShellExportDeadlines.toList().forEach(::scheduleShellExportAt)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_REPORT, latestReport.toString())
        outState.putLongArray(STATE_SHELL_EXPORT_DEADLINES, pendingShellExportDeadlines.toLongArray())
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences(HeartRateDataListenerService.PREFS, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(watchStatusPrefsListener)
        if (::reportView.isInitialized && restoreCorrelatedWatchResponse(latestReport.optString("watch_status_request_id"))) {
            renderReport()
        }
    }

    override fun onPause() {
        getSharedPreferences(HeartRateDataListenerService.PREFS, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(watchStatusPrefsListener)
        super.onPause()
    }

    override fun onDestroy() {
        diagnosticGeneration++
        if (::reportView.isInitialized) delayedCallbacks.forEach(reportView::removeCallbacks)
        delayedCallbacks.clear()
        super.onDestroy()
    }

    private fun isCurrentRequest(generation: Int): Boolean =
        isEngineeringDebugRequestCurrent(
            generation,
            diagnosticGeneration,
            isFinishing,
            isDestroyed,
            isChangingConfigurations
        )

    private fun cleanupExpiredDiagnosticExports() {
        val now = System.currentTimeMillis()
        fun cleanup(directory: File?, matches: (String) -> Boolean, maxAgeMillis: Long) {
            directory?.listFiles()
                ?.filter { it.isFile && matches(it.name) && EngineeringExportRetention.isExpired(it.lastModified(), now, maxAgeMillis) }
                ?.forEach { it.delete() }
        }
        val rawExportDirectory = getExternalFilesDir("debug-export")
        EngineeringExportRetention.deletePartialFiles(rawExportDirectory, ".$RAW_EXPORT_FILE_NAME.")
        cleanup(rawExportDirectory, { it == RAW_EXPORT_FILE_NAME }, EngineeringExportRetention.RAW_EXPORT_MAX_AGE_MILLIS)
        val cacheExports = File(cacheDir, "exports")
        cleanup(cacheExports, { it.startsWith(SUMMARY_EXPORT_PREFIX) }, EngineeringExportRetention.SUMMARY_MAX_AGE_MILLIS)
        cleanup(cacheExports, { it.startsWith(WATCH_RAW_EXPORT_PREFIX) }, RawDiagnosticExportRetention.MAX_AGE_MILLIS)
    }

    private fun isCurrentWatchRequest(generation: Int, requestId: String, expectedState: String): Boolean =
        isCurrentRequest(generation) &&
            latestReport.optString("watch_status_request_id") == requestId &&
            WatchStatusRequestState.canResolveTimeout(latestReport.optString("watch_status_request"), expectedState)

    private fun postTrackedDelayed(delayMs: Long, action: () -> Unit) {
        lateinit var callback: Runnable
        callback = Runnable {
            delayedCallbacks.remove(callback)
            action()
        }
        delayedCallbacks.add(callback)
        reportView.postDelayed(callback, delayMs)
    }

    private fun restoreCorrelatedWatchResponse(requestId: String): Boolean {
        if (requestId.isBlank()) return false
        val prefs = getSharedPreferences(HeartRateDataListenerService.PREFS, MODE_PRIVATE)
        val encoded = prefs.getString(DebugStatusResponseLedger.responseKey(requestId), null)
            ?: prefs.getString(HeartRateDataListenerService.KEY_DEBUG_STATUS_REPORT, null)
                ?.takeIf {
                    isMatchingWatchStatusResponse(
                        requestId,
                        prefs.getString(HeartRateDataListenerService.KEY_DEBUG_STATUS_REQUEST_ID, "").orEmpty()
                    )
                }
            ?: return false
        val report = runCatching { JSONObject(encoded) }.getOrNull() ?: return false
        if (!isMatchingWatchStatusResponse(requestId, report.optString("request_id"))) return false
        val responseAt = report.optLong("phone_received_at_ms")
            .takeIf { it > 0L }
            ?: prefs.getLong(HeartRateDataListenerService.KEY_DEBUG_STATUS_RECEIVED_AT, 0L)
        if (responseAt <= 0L) return false
        latestReport.put("watch_status_request", "correlated_response_received")
        latestReport.put("watch_status", report.optString("status", "unknown"))
        latestReport.put("watch_measure_api", report.optJSONArray("health_services_capabilities")?.toString() ?: "unknown")
        latestReport.put("watch_status_time", report.optLong("watch_timestamp", 0L))
        latestReport.put("watch_status_received_at", responseAt)
        latestReport.put("watch_report", report)
        pendingWatchResponseReconciliation = null
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val shellScan = intent.getBooleanExtra(EXTRA_START_SENSOR_SUITE, false)
        val shellExport = intent.getBooleanExtra(EXTRA_EXPORT_RAW_ON_OPEN, false)
        if (shellScan) startWatchProbe()
        else if (shellExport || intent.getBooleanExtra(EXTRA_QUERY_WATCH, false)) runDiagnosis(requestWatch = true)
        if (shellExport) {
            scheduleShellExport(if (shellScan) SHELL_EXPORT_DELAY_MS else SHELL_EXPORT_DELAY_MS)
            if (shellScan) scheduleShellExport(SHELL_SCAN_EXPORT_DELAY_MS)
        }
    }

    private fun scheduleShellExport(delayMs: Long) {
        val deadline = SystemClock.elapsedRealtime() + delayMs
        if (pendingShellExportDeadlines.add(deadline)) scheduleShellExportAt(deadline)
    }

    private fun scheduleShellExportAt(deadlineElapsedRealtime: Long) {
        val delayMs = ShellExportDeadline.remainingDelay(deadlineElapsedRealtime, SystemClock.elapsedRealtime())
        postTrackedDelayed(delayMs) {
            if (!pendingShellExportDeadlines.remove(deadlineElapsedRealtime)) return@postTrackedDelayed
            getExternalFilesDir("debug-export")?.let(::writeRawExport)
        }
    }

    private fun runDiagnosis(requestWatch: Boolean) {
        diagnosticGeneration++
        val generation = diagnosticGeneration
        permissionsRequested = false
        latestReport = buildPhoneReport()
        if (scanCommandAcceptedAt > 0L) latestReport.put("scan_command_accepted_at", scanCommandAcceptedAt)
        if (requestWatch) {
            latestReport.put("watch_link", "querying")
            latestReport.put("watch_status_request", "querying_connected_nodes")
            latestReport.put("watch_status", "正在查詢已連線的手錶節點")
        }
        renderReport()
        if (!requestWatch) return
        postTrackedDelayed(WATCH_NODE_LOOKUP_TIMEOUT_MS) {
            if (isCurrentRequest(generation) && latestReport.optString("watch_status_request") == "querying_connected_nodes") {
                latestReport.put("watch_status_request", "node_lookup_timeout")
                latestReport.put("watch_status", "查詢手錶節點逾時；稍後可重新檢查")
                renderReport()
            }
        }
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (!isCurrentRequest(generation) || latestReport.optString("watch_status_request") != "querying_connected_nodes") return@addOnSuccessListener
                latestReport.put("watch_node_count", nodes.size)
                latestReport.put("watch_link", if (nodes.isEmpty()) "disconnected" else "connected")
                if (nodes.isEmpty()) {
                    latestReport.put("watch_status_request", "no_connected_node")
                    latestReport.put("watch_status", "目前沒有可用的 Wearable 手錶節點；尚未傳送狀態請求")
                    renderReport()
                    return@addOnSuccessListener
                }
                if (nodes.size > 1) {
                    latestReport.put("watch_status_request", "ambiguous_multiple_connected_watches")
                    latestReport.put("watch_status", "同時連線多台手錶；為避免回覆交錯，本次未送出狀態查詢")
                    renderReport()
                    return@addOnSuccessListener
                }
                val requestId = UUID.randomUUID().toString()
                val requestTime = System.currentTimeMillis()
                val requestElapsedTime = SystemClock.elapsedRealtime()
                latestReport.put("watch_status_request_started_at", requestTime)
                latestReport.put("watch_status_request_started_elapsed_at", requestElapsedTime)
                latestReport.put("watch_status_request", "sending_status_request")
                latestReport.put("watch_status", "已找到手錶，正在送出狀態查詢")
                latestReport.put("watch_status_request_id", requestId)
                getSharedPreferences(HeartRateDataListenerService.PREFS, MODE_PRIVATE).edit()
                    .putString(HeartRateDataListenerService.KEY_DEBUG_STATUS_ACTIVE_REQUEST_ID, requestId)
                    .apply()
                val node = nodes.single()
                latestReport.put("watch_node_name", node.displayName)
                postTrackedDelayed(WATCH_SEND_TIMEOUT_MS) {
                    if (isCurrentWatchRequest(generation, requestId, "sending_status_request")) {
                        if (!restoreCorrelatedWatchResponse(requestId)) {
                            latestReport.put("watch_status_request", "send_task_timeout")
                            latestReport.put("watch_status", "Data Layer 傳送工作逾時；沒有確認訊息是否送達")
                        }
                        renderReport()
                    }
                }
                Wearable.getMessageClient(this).sendMessage(node.id, PATH_WATCH_STATUS, requestId.toByteArray(Charsets.UTF_8))
                    .addOnSuccessListener {
                        if (isCurrentWatchRequest(generation, requestId, "sending_status_request")) {
                            if (restoreCorrelatedWatchResponse(requestId)) {
                                renderReport()
                                return@addOnSuccessListener
                            }
                            latestReport.put("watch_message_accepted_at", System.currentTimeMillis())
                            latestReport.put("watch_message_accepted_elapsed_at", SystemClock.elapsedRealtime())
                            latestReport.put("watch_status_request", "waiting_for_watch_response")
                            latestReport.put("watch_status", "請求已送達 Wearable Data Layer；等待手錶 App 回報（不代表感測器已啟動）")
                            renderReport()
                            if (WatchStatusRequestState.shouldArmResponseTimeout(latestReport.optString("watch_status_request"))) {
                                postTrackedDelayed(WATCH_STATUS_TIMEOUT_MS) {
                                    val requestState = latestReport.optString("watch_status_request")
                                    if (!isCurrentWatchRequest(generation, requestId, "waiting_for_watch_response") ||
                                        !WatchStatusRequestState.canResolveTimeout(requestState, "waiting_for_watch_response")
                                    ) return@postTrackedDelayed
                                    val received = restoreCorrelatedWatchResponse(requestId)
                                    if (!received) {
                                        latestReport.put("watch_status_request", "no_correlated_response")
                                        latestReport.put("watch_status", "未收到本次請求 ID 對應的手錶回覆；訊息送達狀態不代表檢查成功")
                                        latestReport.put("watch_measure_api", "unknown")
                                        latestReport.put("watch_status_time", 0L)
                                        latestReport.put("watch_status_received_at", 0L)
                                    }
                                    renderReport()
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        if (!isCurrentWatchRequest(generation, requestId, "sending_status_request")) return@addOnFailureListener
                        latestReport.put("watch_status_request", "failed:${it.javaClass.simpleName}")
                        renderReport()
                    }
            }
            .addOnFailureListener {
                if (!isCurrentRequest(generation) || latestReport.optString("watch_status_request") != "querying_connected_nodes") return@addOnFailureListener
                latestReport.put("watch_link", "query_failed:${it.javaClass.simpleName}")
                latestReport.put("watch_status_request", "node_query_failed:${it.javaClass.simpleName}")
                latestReport.put("watch_status", "查詢手錶節點失敗：${it.javaClass.simpleName}")
                renderReport()
            }
    }

    private fun buildPhoneReport(): JSONObject {
        val now = System.currentTimeMillis()
        val packageInfo = appPackageInfo()
        val sensors = getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL)
        val sensorJson = JSONArray()
        sensors.forEach { sensor ->
            sensorJson.put(JSONObject()
                .put("name", sensor.name.take(100))
                .put("vendor", sensor.vendor.take(80))
                .put("type", sensor.stringType)
                .put("version", sensor.version)
                .put("wake_up", sensor.isWakeUpSensor)
                .put("reporting_mode", sensor.reportingMode)
                .put("min_delay_us", sensor.minDelay)
                .put("max_delay_us", sensor.maxDelay)
                .put("fifo_max", sensor.fifoMaxEventCount)
                .put("fifo_reserved", sensor.fifoReservedEventCount)
                .put("max_range", sensor.maximumRange.toDouble())
                .put("resolution", sensor.resolution.toDouble())
                .put("power_ma", sensor.power.toDouble()))
        }
        val appExits = JSONArray()
        if (Build.VERSION.SDK_INT >= 30) {
            val exits = getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(packageName, 0, 5)
            exits.take(5).forEach { info ->
                appExits.put(JSONObject()
                    .put("reason", info.reason)
                    .put("timestamp", info.timestamp)
                    .put("description", info.description.orEmpty().take(120)))
            }
        }
        val sync = getSharedPreferences(HeartRateDataListenerService.PREFS, MODE_PRIVATE)
        val dataItemAck = getSharedPreferences(WearDataItemAckTracker.PREFS, MODE_PRIVATE)
        val dataItemDeleteByRoute = JSONObject().apply {
            WearDataItemAckTracker.ROUTES.forEach { route ->
                put(route, JSONObject()
                    .put("attempts", dataItemAck.getInt(WearDataItemAckTracker.routeCounterKey(route, WearDataItemAckTracker.ATTEMPTS), 0))
                    .put("succeeded", dataItemAck.getInt(WearDataItemAckTracker.routeCounterKey(route, WearDataItemAckTracker.SUCCEEDED), 0))
                    .put("failed", dataItemAck.getInt(WearDataItemAckTracker.routeCounterKey(route, WearDataItemAckTracker.FAILED), 0)))
            }
        }
        val rawStats = JSONObject()
        var rawQuery = "success"
        try {
            val raw = RawSensorStore.open(this@EngineeringDebugActivity)
            RawSnapshotDiagnostics.channels(raw.all).forEach { channel ->
                val ts = channel.timestamp
                rawStats.put(channel.kind, JSONObject()
                    .put("age_ms", if (ts > 0L) now - ts else JSONObject.NULL)
                    .put("has_timestamp", ts > 0L)
                    .put("samples", channel.samples)
                    .put("status", channel.status))
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            rawQuery = "failed:${error.javaClass.simpleName}"
        }
        val power = getSystemService(PowerManager::class.java)
        val battery = getSystemService(BatteryManager::class.java)
        val app = JSONObject()
            .put("package", packageName)
            .put("version_name", packageInfo.versionName.orEmpty())
            .put("version_code", if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong())
            .put("target_sdk", packageInfo.applicationInfo?.targetSdkVersion ?: -1)
            .put("installed_update_time", packageInfo.lastUpdateTime)
            .put("signing_cert_sha256", signingDigest(packageInfo))
        val permissionStates = JSONArray()
        packageInfo.requestedPermissions.orEmpty()
            .filterNot { it.startsWith("android.permission.health.") }
            .forEach { permission ->
                permissionStates.put(JSONObject()
                    .put("permission", permission)
                    .put("declared", true)
                    .put("runtime_granted", checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED))
            }
        return JSONObject()
            .put("generated_at", now)
            .put("device_scope", "phone")
            .put("android_release", Build.VERSION.RELEASE)
            .put("android_api", Build.VERSION.SDK_INT)
            .put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}".take(100))
            .put("app", app)
            .put("manifest_permissions", permissionStates)
            .put("battery_percent", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            .put("charging", battery.isCharging)
            .put("power_save_mode", power.isPowerSaveMode)
            .put("device_idle_mode", power.isDeviceIdleMode)
            .put("sensor_inventory", sensorJson)
            .put("health_connect_sdk_status", HealthConnectClient.getSdkStatus(this))
            .put("health_connect_granted_permission_count", -1)
            .put("phone_last_sync_at", sync.getLong(HeartRateDataListenerService.KEY_LAST_SYNC, 0L))
            .put("phone_last_receive_at", sync.getLong(HeartRateDataListenerService.KEY_LAST_RECEIVED, 0L))
            .put("phone_last_write_at", sync.getLong(HeartRateDataListenerService.KEY_LAST_HEALTH_CONNECT_WRITE, 0L))
            .put("phone_last_error", sync.getString(HeartRateDataListenerService.KEY_LAST_ERROR, "").orEmpty().take(160))
            .put("raw_snapshot_reject_count", sync.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_REJECTION_COUNT, 0))
            .put("raw_snapshot_reject_ordering", sync.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_ORDERING_REJECTION_COUNT, 0))
            .put("raw_snapshot_reject_payload", sync.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_PAYLOAD_REJECTION_COUNT, 0))
            .put("raw_snapshot_reject_duplicate", sync.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_DUPLICATE_REJECTION_COUNT, 0))
            .put("data_item_delete_attempts", dataItemAck.getInt(WearDataItemAckTracker.ATTEMPTS, 0))
            .put("data_item_delete_succeeded", dataItemAck.getInt(WearDataItemAckTracker.SUCCEEDED, 0))
            .put("data_item_delete_failed", dataItemAck.getInt(WearDataItemAckTracker.FAILED, 0))
            .put("data_item_delete_last_route", dataItemAck.getString(WearDataItemAckTracker.LAST_ROUTE, "unknown"))
            .put("data_item_delete_last_result", dataItemAck.getString(WearDataItemAckTracker.LAST_RESULT, "not_run"))
            .put("data_item_delete_last_error_type", dataItemAck.getString(WearDataItemAckTracker.LAST_ERROR_TYPE, ""))
            .put("data_item_delete_last_result_at", dataItemAck.getLong(WearDataItemAckTracker.LAST_RESULT_AT, 0L))
            .put("data_item_delete_by_route", dataItemDeleteByRoute)
            .put("raw_channels", rawStats)
            .put("raw_snapshot_query", rawQuery)
            .put("recent_process_exits", appExits)
            .put("not_observable", JSONArray(listOf("晶片私有暫存器／韌體狀態", "OEM 校準係數", "精確晶片功耗", "其他 App 的 listener／程序狀態")))
    }

    private fun loadGrantedHealthPermissions() {
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            latestReport.put("health_connect_granted_permission_count", JSONObject.NULL)
            latestReport.put("health_connect_permission_query", "provider_unavailable")
            return
        }
        latestReport.put("health_connect_permission_query", "pending")
        val generation = diagnosticGeneration
        lifecycleScope.launch {
            try {
                val permissions = HealthConnectClient.getOrCreate(this@EngineeringDebugActivity)
                    .permissionController.getGrantedPermissions()
                if (!isCurrentRequest(generation)) return@launch
                latestReport.put("health_connect_granted_permission_count", permissions.size)
                latestReport.put("health_connect_granted_permissions", JSONArray(permissions.sorted()))
                latestReport.put("health_connect_permission_query", "success")
                renderReport()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!isCurrentRequest(generation)) return@launch
                latestReport.put("health_connect_granted_permission_count", JSONObject.NULL)
                latestReport.put("health_connect_permission_query", "failed:${error.javaClass.simpleName}")
                renderReport()
            }
        }
    }

    private fun appPackageInfo(): PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(
            (android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES or
                android.content.pm.PackageManager.GET_PERMISSIONS).toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(
            packageName,
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES or
                android.content.pm.PackageManager.GET_PERMISSIONS
        )
    }

    private fun signingDigest(info: PackageInfo): String = runCatching {
        val cert = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        else @Suppress("DEPRECATION") info.signatures?.firstOrNull()?.toByteArray()
        cert?.let { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) } }
            ?: "unknown"
    }.getOrDefault("unknown")

    private fun startWatchProbe() {
        diagnosticGeneration++
        val generation = diagnosticGeneration
        scanCommandAcceptedAt = 0L
        latestReport.remove("scan_command_accepted_at")
        latestReport.remove("scan_command_delivery")
        latestReport.remove("watch_report")
        latestReport.remove("watch_status")
        latestReport.remove("watch_status_time")
        latestReport.remove("watch_status_received_at")
        latestReport.remove("watch_status_request")
        latestReport.remove("watch_measure_api")
        renderReport()
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (!isCurrentRequest(generation)) return@addOnSuccessListener
            if (nodes.isEmpty()) {
                latestReport.put("scan_command_delivery", "no_connected_watch")
                renderReport()
                Toast.makeText(this, "未連線手錶，無法啟動完整掃描", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            if (nodes.size > 1) {
                latestReport.put("scan_command_delivery", "ambiguous_multiple_connected_watches")
                renderReport()
                Toast.makeText(this, "同時連線多台手錶；請先只保留目標手錶連線", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            nodes.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(node.id, PATH_WATCH_SENSOR_SUITE, byteArrayOf())
                    .addOnSuccessListener {
                        if (!isCurrentRequest(generation)) return@addOnSuccessListener
                        scanCommandAcceptedAt = System.currentTimeMillis()
                        latestReport.put("scan_command_accepted_at", scanCommandAcceptedAt)
                        latestReport.put("scan_command_delivery", "accepted_by_data_layer_not_execution_ack")
                        renderReport()
                        Toast.makeText(this, "命令已被傳輸層接受；2 秒後查詢手錶是否回報掃描執行中", Toast.LENGTH_LONG).show()
                        postTrackedDelayed(2_000L) {
                            if (isCurrentRequest(generation)) runDiagnosis(requestWatch = true)
                        }
                    }
                    .addOnFailureListener {
                        if (!isCurrentRequest(generation)) return@addOnFailureListener
                        latestReport.put("scan_command_delivery", "failed:${it.javaClass.simpleName}")
                        renderReport()
                        Toast.makeText(this, "完整掃描命令傳送失敗：${it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    }
            }
        }.addOnFailureListener {
            if (!isCurrentRequest(generation)) return@addOnFailureListener
            latestReport.put("scan_command_delivery", "node_query_failed:${it.javaClass.simpleName}")
            renderReport()
            Toast.makeText(this, "查詢手錶失敗：${it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderReport() {
        loadGrantedHealthPermissionsOnce()
        val watchReport = latestReport.optJSONObject("watch_report")
        val watchStatus = latestReport.optString("watch_status", "尚無本次手錶狀態回報")
        val watchStatusReceivedAt = latestReport.optLong("watch_status_received_at", 0L)
        val generated = latestReport.optLong("generated_at", System.currentTimeMillis())
        val sensors = latestReport.optJSONArray("sensor_inventory") ?: JSONArray()
        val channels = latestReport.optJSONObject("raw_channels") ?: JSONObject()
        val statuses = buildString {
            append("診斷時間：${DateFormat.getDateTimeInstance().format(Date(generated))}\n")
            append("手機：Android ${latestReport.optString("android_release")} / API ${latestReport.optInt("android_api")}\n")
            append("App：${latestReport.optJSONObject("app")?.optString("version_name")} (${latestReport.optJSONObject("app")?.optLong("version_code")})；簽章憑證 SHA-256 ${latestReport.optJSONObject("app")?.optString("signing_cert_sha256", "unknown")}（非 APK 檔案雜湊）\n")
            append("已列舉手機感測器：${sensors.length()}（只代表 API 公開清單）\n")
            append("Health Connect：${latestReport.optInt("health_connect_sdk_status")}；已授權型別數 ${latestReport.optInt("health_connect_granted_permission_count", -1)}\n")
            append("Health Connect 權限查詢：${latestReport.optString("health_connect_permission_query", "pending")}\n")
            append("電量／充電／省電／Doze：${latestReport.optInt("battery_percent")}％ / ${latestReport.optBoolean("charging")} / ${latestReport.optBoolean("power_save_mode")} / ${latestReport.optBoolean("device_idle_mode")}\n")
            append("原始快照讀取：${latestReport.optString("raw_snapshot_query", "未查詢")}\n")
            append("手錶連線：${latestReport.optString("watch_link", "未查詢")}；本次狀態查詢：${latestReport.optString("watch_status_request", "未執行")}\n")
            if (latestReport.optString("watch_status_request", "not_run") == "not_run") {
                append("提示：本次尚未向手錶發出狀態要求；按上方查詢按鈕。\n")
            }
            if (latestReport.optLong("scan_command_accepted_at") > 0L) {
                val active = ObservedBooleanStatus.label(watchReport?.opt("suite_active"))
                append("全掃描命令傳送已被 Data Layer 接受（不等於啟動）；本次查詢時手錶 App 回報掃描執行中=$active\n")
            }
            latestReport.optString("scan_command_delivery").takeIf { it.isNotBlank() }?.let {
                append("本次全掃描命令傳輸狀態：$it\n")
            }
            append("手錶回報（手機收到時間 ${formatTime(watchStatusReceivedAt)}）：$watchStatus\n\n")
            watchReport?.optString("diagnostic_error")?.takeIf { it.isNotBlank() }?.let {
                append("手錶診斷錯誤類別：${it.take(80)}\n")
            }
            append("手錶：${watchReport?.optString("device_model", "未知")} / Android ${watchReport?.optString("android_release", "未知")} API ${watchReport?.optInt("android_api", -1)}\n")
            append("手錶系統 Build：${watchReport?.optString("system_build_display", "未回報")}（整機系統版本識別，不代表感測器韌體版本）\n")
            append("手錶 App：${watchReport?.optString("app_version", "未知")} (${watchReport?.optLong("app_version_code", -1)})；target ${watchReport?.optInt("app_target_sdk", -1)}\n")
            append("手錶公開 SensorManager 清單：${watchReport?.optJSONArray("sensor_inventory")?.length() ?: "未取得"} 顆；權限項目 ${watchReport?.optJSONArray("permissions")?.length() ?: "未取得"}\n")
            append("Health Services 能力彙總（Measure／Passive／Exercise）：${latestReport.optString("watch_measure_api", "尚未取得")}${if (watchReport != null && !watchReport.optBoolean("health_services_capabilities_complete")) "（回覆不完整；未列出不代表不支援）" else ""}\n")
            append("手錶 App 記錄狀態：全掃描=${ObservedBooleanStatus.label(watchReport?.opt("suite_active"))}；健康 FGS=${ObservedBooleanStatus.label(watchReport?.opt("health_fgs_active_by_app"))}；App 所持 WakeLock=${ObservedBooleanStatus.label(watchReport?.opt("wake_lock_held_by_app"))}\n\n")
            append("\n手機感測器公開規格\n${formatSensors(sensors)}")
            append("\n\nHealth Connect 已授權型別\n${latestReport.optJSONArray("health_connect_granted_permissions")?.let(::formatStrings) ?: "尚未查得"}")
            append("\n\n手機宣告的一般系統權限及授權狀態\n${formatPermissions(latestReport.optJSONArray("manifest_permissions"))}")
            append("\n\n手錶感測器公開規格\n${formatSensors(watchReport?.optJSONArray("sensor_inventory"), watchReport?.optString("sensor_inventory_state"))}")
            append("\n\n手錶宣告權限及系統授權狀態\n${formatPermissions(watchReport?.optJSONArray("permissions"))}")
            append("\n\n手錶 Health Services API 能力\n${formatStrings(watchReport?.optJSONArray("health_services_capabilities"))}")
            append("\n\nHealth Services 各 API 查詢狀態／耗時\n${formatCapabilityCalls(watchReport?.optJSONArray("health_services_capability_calls"))}")
            append("\n\n手錶各通道最近採集記錄（未保證屬於本次全掃描）\n${formatAcquisition(watchReport?.optJSONArray("acquisition_history"))}\n")
            append("最近同步：${formatTime(latestReport.optLong("phone_last_sync_at"))}；最近寫入 HC：${formatTime(latestReport.optLong("phone_last_write_at"))}\n")
            append("快照拒收：總 ${latestReport.optInt("raw_snapshot_reject_count")}／順序 ${latestReport.optInt("raw_snapshot_reject_ordering")}／格式 ${latestReport.optInt("raw_snapshot_reject_payload")}／重複 ${latestReport.optInt("raw_snapshot_reject_duplicate")}\n")
            append("Data Layer DataItem 刪除回報：嘗試 ${latestReport.optInt("data_item_delete_attempts")}／成功 ${latestReport.optInt("data_item_delete_succeeded")}／失敗 ${latestReport.optInt("data_item_delete_failed")}；最近 ${latestReport.optString("data_item_delete_last_route")} ${latestReport.optString("data_item_delete_last_result")} ${latestReport.optString("data_item_delete_last_error_type")}\n")
            latestReport.optJSONObject("data_item_delete_by_route")?.let { routes ->
                append("刪除失敗依路線：${routes.keys().asSequence().map { route -> "$route=${routes.optJSONObject(route)?.optInt("failed") ?: 0}" }.joinToString("、")}\n")
            }
            val recent = channels.keys().asSequence().take(24).map { kind ->
                val item = channels.optJSONObject(kind) ?: JSONObject()
                "$kind：${item.optInt("samples", -1)} samples，${item.optString("status")}，${item.opt("age_ms") ?: "無時間戳"} ms 前"
            }.toList()
            append(if (recent.isEmpty()) "\n${RawSnapshotDiagnostics.emptyMessage(latestReport.optString("raw_snapshot_query", "not_run"))}" else "\n\n手機已收快照（最多列 24 通道）\n${recent.joinToString("\n")}")
            append("\n\n最近 App process exit：${latestReport.optJSONArray("recent_process_exits")?.length() ?: 0} 筆")
            append("\n私有晶片狀態與精確功耗：此 App 無法直接讀取；需 OEM/ADB 或實驗室工具。")
        }
        reportView.text = statuses
    }

    private var permissionsRequested = false
    private fun loadGrantedHealthPermissionsOnce() {
        if (permissionsRequested) return
        permissionsRequested = true
        loadGrantedHealthPermissions()
    }

    private fun shareReport() {
        lifecycleScope.launch {
            if (HealthConnectClient.getSdkStatus(this@EngineeringDebugActivity) == HealthConnectClient.SDK_AVAILABLE) {
                try {
                    val granted = HealthConnectClient.getOrCreate(this@EngineeringDebugActivity)
                        .permissionController.getGrantedPermissions()
                    latestReport.put("health_connect_granted_permission_count", granted.size)
                    latestReport.put("health_connect_granted_permissions", JSONArray(granted.sorted()))
                    latestReport.put("health_connect_permission_query", "success")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    latestReport.put("health_connect_granted_permission_count", JSONObject.NULL)
                    latestReport.put("health_connect_permission_query", "failed:${error.javaClass.simpleName}")
                }
            } else {
                latestReport.put("health_connect_granted_permission_count", JSONObject.NULL)
                latestReport.put("health_connect_permission_query", "provider_unavailable")
            }
            val preview = sanitizedExport().toString(2)
            val previewText = text(preview, 12f, 0xFFE0E4ED.toInt()).apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
            android.app.AlertDialog.Builder(this@EngineeringDebugActivity)
                .setTitle("實際分享內容預覽（脫敏摘要）")
                .setView(ScrollView(this@EngineeringDebugActivity).apply {
                    addView(previewText)
                    layoutParams = LinearLayout.LayoutParams(-1, dp(440))
                })
                .setNegativeButton("取消", null)
                .setPositiveButton("分享此內容") { _, _ ->
                    val dir = File(cacheDir, "exports").apply { mkdirs() }
                    cleanupExpiredDiagnosticExports()
                    val file = File(dir, "$SUMMARY_EXPORT_PREFIX${System.currentTimeMillis()}.json")
                    file.writeText(preview)
                    EngineeringExportRetention.scheduleDelete(this@EngineeringDebugActivity, file, EngineeringExportRetention.SUMMARY_MAX_AGE_MILLIS)
                    val uri = FileProvider.getUriForFile(this@EngineeringDebugActivity, "$packageName.files", file)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "分享工程診斷摘要"))
                }.show()
        }
    }

    private fun confirmRawExport() {
        val directory = getExternalFilesDir("debug-export") ?: run {
            Toast.makeText(this, "外部儲存空間不可用", Toast.LENGTH_LONG).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("匯出完整診斷資料")
            .setMessage("將匯出手機目前保存的感測器資料、最近手錶回報、診斷欄位，以及最多 24 小時內的感測器摘要到本機明文 JSON；ECG 另含可對齊時的逐事件候選值與未映射附加欄位（不代表完整 HAL 封包）。不會自動上傳。資料可能含原始波形、時間戳與個人健康資訊。請只在信任的電腦使用已授權 ADB 取回：${directory.absolutePath}/$RAW_EXPORT_FILE_NAME。檔案會在 App 存活時嘗試於 24 小時後刪除，並於下次開啟工程頁時清理過期檔；未加密，建議取回後立即刪除。\n\n要繼續嗎？")
            .setNegativeButton("取消", null)
            .setPositiveButton("匯出到本機") { _, _ -> writeRawExport(directory) }
            .show()
    }

    private fun writeRawExport(directory: File) {
        try {
            val raw = RawSensorStore.open(this@EngineeringDebugActivity).all
            val rawJson = JSONObject()
            var exportChars = 0L
            raw.forEach { (key, value) ->
                val jsonValue: Any? = when (value) {
                    is Set<*> -> JSONArray(value.filterIsInstance<String>().also { strings ->
                        strings.forEach { exportChars += it.length }
                    })
                    is String -> {
                        require(value.length <= MAX_EXPORT_STRING_CHARS) { "感測資料欄位過大：$key" }
                        exportChars += value.length
                        value
                    }
                    is Int, is Long, is Float, is Boolean -> value
                    else -> null
                }
                exportChars += key.length
                require(exportChars <= MAX_EXPORT_TOTAL_CHARS) { "感測資料匯出超過安全上限" }
                if (jsonValue != null) rawJson.put(key, jsonValue)
            }
            val ecgTimestampsRaw = raw["ecg_event_timestamps_ns"] as? String ?: ""
            val ecgCallbacksRaw = raw["ecg_event_callback_elapsed_realtime_ns"] as? String ?: ""
            val ecgCountsRaw = raw["ecg_event_sample_counts"] as? String ?: ""
            val ecgMetadataRaw = (raw["ecg_event_raw_values_rows"] as? String)
                ?: (raw["ecg_event_metadata_rows"] as? String ?: "")
            val ecgSourceEncoding = raw["ecg_source_waveform_encoding"] as? String ?: ""
            val ecgRawValuesStatus = raw["ecg_event_raw_values_status"] as? String
                ?: "legacy_app_split_unverified"
            val ecgWaveformRaw = raw["ecg_raw_waveform"] as? String ?: ""
            val ecgTimestamps = SensorSuiteCsvParser.parseLongs(ecgTimestampsRaw, EcgEventRowExportPolicy.MAX_EVENT_ROWS)
            val ecgCallbacks = SensorSuiteCsvParser.parseLongs(ecgCallbacksRaw, EcgEventRowExportPolicy.MAX_EVENT_ROWS)
            val ecgCounts = SensorSuiteCsvParser.parseInts(ecgCountsRaw, EcgEventRowExportPolicy.MAX_EVENT_ROWS)
            val ecgMetadata = SensorSuiteCsvParser.parseRows(
                ecgMetadataRaw,
                EcgEventRowExportPolicy.MAX_EVENT_ROWS,
                if (ecgSourceEncoding == "hal_event_values_full_unmapped_preview") {
                    SensorSuiteCsvParser.MAX_ECG_EVENT_ROW_CHARS
                } else {
                    48 * 16
                }
            )
            val ecgWaveform = SensorSuiteCsvParser.parse(ecgWaveformRaw, EcgEventRowExportPolicy.MAX_CANDIDATE_POINTS)
            val ecgParseFailed = listOf(
                ecgTimestampsRaw to ecgTimestamps.size,
                ecgCallbacksRaw to ecgCallbacks.size,
                ecgCountsRaw to ecgCounts.size,
                ecgMetadataRaw to ecgMetadata.size,
                ecgWaveformRaw to ecgWaveform.size
            ).any { (source, parsedSize) -> source.isNotEmpty() && parsedSize == 0 }
            val ecgRows = EcgEventRowExportPolicy.build(
                eventTimestampsNs = ecgTimestamps,
                callbackTimesNs = ecgCallbacks,
                sampleCounts = ecgCounts,
                metadataRows = ecgMetadata,
                candidateValues = ecgWaveform,
                sourceEncoding = ecgSourceEncoding,
                rawValuesStatus = ecgRawValuesStatus,
                parseFailed = ecgParseFailed
            )
            val ecgEventRowsJson = JSONArray()
            ecgRows.rows.forEach { row ->
                val candidateValuesJson = JSONArray().also { values ->
                    row.candidateValues.forEach(values::put)
                }
                ecgEventRowsJson.put(JSONObject()
                    .put("event_index", row.eventIndex)
                    .put("sensor_event_timestamp_ns", row.eventTimestampNs)
                    .put("callback_received_elapsed_realtime_ns", row.callbackReceivedElapsedRealtimeNs ?: JSONObject.NULL)
                    .put("legacy_uninterpreted_candidate_prefix_values", candidateValuesJson)
                    .put("sensor_event_values_raw_csv", row.rawEventValuesCsv)
                    .put("legacy_unmapped_remainder_csv", row.legacyRemainderCsv))
            }
            val ecgEventExport = JSONObject()
                .put("status", ecgRows.status)
                .put("row_index_aligned", ecgRows.rowIndexAligned)
                .put("sensor_event_timestamps_valid_monotonic", ecgRows.sensorTimestampsValidMonotonic)
                .put("source_encoding", ecgSourceEncoding)
                .put("raw_values_capture_status", ecgRawValuesStatus)
                .put("interpretation", if (ecgSourceEncoding == "hal_event_values_full_unmapped_preview") {
                    "Per-event SensorEvent.values numeric fields are retained as unmapped CSV; they are not decoded ECG samples, a binary HAL packet, or calibrated physiological values."
                } else {
                    "Legacy app-split preview only: candidate prefix and remainder do not have a verified OEM layout and are not decoded ECG samples, a binary HAL packet, or calibrated physiological values."
                })
                .put("event_rows", ecgEventRowsJson)
            val suiteFile = File(cacheDir, "exports/${SensorSuiteDump.FILE_NAME}")
            SensorSuiteDump.requestPartialCleanup(this)
            val suiteAvailable = suiteFile.isFile &&
                SensorSuiteDumpRetention.isAvailable(suiteFile.lastModified(), System.currentTimeMillis())
            if (suiteFile.isFile && !suiteAvailable) suiteFile.delete()
            val output = JSONObject()
                .put("schema", "healthbridge-local-debug-export-v1")
                .put("exported_at_ms", System.currentTimeMillis())
                .put("phone_diagnostics", latestReport)
                .put("phone_raw_sensor_preferences", rawJson)
                .put("ecg_event_rows", ecgEventExport)
                .put("watch_latest_report", latestReport.optJSONObject("watch_report") ?: JSONObject.NULL)
                .put("sensor_suite_dump", if (suiteAvailable && suiteFile.length() <= MAX_SUITE_DUMP_BYTES) {
                    runCatching { JSONObject(suiteFile.readText()) }.getOrNull() ?: JSONObject.NULL
                } else JSONObject.NULL)
            cleanupExpiredDiagnosticExports()
            val file = File(directory, RAW_EXPORT_FILE_NAME)
            EngineeringExportRetention.writeAtomically(
                file,
                output.toString(),
                maxBytes = MAX_EXPORT_TOTAL_BYTES
            )
            EngineeringExportRetention.scheduleDelete(this, file, EngineeringExportRetention.RAW_EXPORT_MAX_AGE_MILLIS)
            Toast.makeText(this, "已匯出 ${file.length()} bytes；可用 adb pull \"${file.absolutePath}\"", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            Toast.makeText(this, "匯出失敗：${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun sanitizedExport(): JSONObject {
        val now = System.currentTimeMillis()
        val app = latestReport.optJSONObject("app") ?: JSONObject()
        val output = JSONObject()
            .put("schema", "engineering-diagnostic-summary-v1")
            .put("generated_ago_ms", 0)
            .put("phone", JSONObject()
                .put("android_release", latestReport.optString("android_release"))
                .put("android_api", latestReport.optInt("android_api"))
                .put("device_model", latestReport.optString("device_model"))
                .put("app_version", app.optString("version_name"))
                .put("app_version_code", app.optLong("version_code"))
                .put("target_sdk", app.optInt("target_sdk"))
                .put("battery_percent", latestReport.optInt("battery_percent", -1))
                .put("charging", latestReport.optBoolean("charging"))
                .put("power_save_mode", latestReport.optBoolean("power_save_mode"))
                .put("idle_mode", latestReport.optBoolean("device_idle_mode"))
                .put("health_connect_sdk_status", latestReport.optInt("health_connect_sdk_status", -1))
                .put("health_connect_permission_query", latestReport.optString("health_connect_permission_query", "pending"))
                .put("health_connect_permissions", latestReport.optJSONArray("health_connect_granted_permissions") ?: JSONArray())
                .put("sensor_inventory", latestReport.optJSONArray("sensor_inventory") ?: JSONArray()))
            .put("transport", JSONObject()
                .put("watch_link", latestReport.optString("watch_link", "unknown"))
                .put("watch_status_request", latestReport.optString("watch_status_request", "not_run"))
                .put("watch_status_age_ms", latestReport.optLong("watch_status_received_at").takeIf { it > 0 }?.let { now - it } ?: JSONObject.NULL)
                .put("watch_measure_api", latestReport.optString("watch_measure_api", "unknown"))
                .put("last_sync_age_ms", latestReport.optLong("phone_last_sync_at").takeIf { it > 0 }?.let { now - it } ?: JSONObject.NULL)
                .put("last_phone_receive_age_ms", latestReport.optLong("phone_last_receive_at").takeIf { it > 0 }?.let { now - it } ?: JSONObject.NULL)
                .put("last_health_connect_write_age_ms", latestReport.optLong("phone_last_write_at").takeIf { it > 0 }?.let { now - it } ?: JSONObject.NULL)
                .put("snapshot_reject_count", latestReport.optInt("raw_snapshot_reject_count"))
                .put("snapshot_reject_ordering", latestReport.optInt("raw_snapshot_reject_ordering"))
                .put("snapshot_reject_payload", latestReport.optInt("raw_snapshot_reject_payload"))
                .put("snapshot_reject_duplicate", latestReport.optInt("raw_snapshot_reject_duplicate"))
                .put("data_item_delete_attempts", latestReport.optInt("data_item_delete_attempts"))
                .put("data_item_delete_succeeded", latestReport.optInt("data_item_delete_succeeded"))
                .put("data_item_delete_failed", latestReport.optInt("data_item_delete_failed"))
                .put("data_item_delete_last_route", latestReport.optString("data_item_delete_last_route"))
                .put("data_item_delete_last_result", latestReport.optString("data_item_delete_last_result"))
                .put("data_item_delete_last_error_type", latestReport.optString("data_item_delete_last_error_type"))
                .put("data_item_delete_last_result_at", latestReport.optLong("data_item_delete_last_result_at"))
                .put("data_item_delete_by_route", latestReport.optJSONObject("data_item_delete_by_route") ?: JSONObject()))
            .put("raw_snapshot_query", latestReport.optString("raw_snapshot_query", "not_run"))
            .put("channels", latestReport.optJSONObject("raw_channels") ?: JSONObject())
            .put("limits", latestReport.optJSONArray("not_observable") ?: JSONArray())
        latestReport.optJSONObject("watch_report")?.let { watch ->
            output.put("watch", JSONObject()
                .put("device_model", watch.optString("device_model"))
                .put("android_release", watch.optString("android_release"))
                .put("android_api", watch.optInt("android_api"))
                .put("system_build_display", watch.optString("system_build_display"))
                .put("app_version", watch.optString("app_version"))
                .put("app_version_code", watch.optLong("app_version_code"))
                .put("app_target_sdk", watch.optInt("app_target_sdk"))
                .put("control_status", when (watch.optString("status")) {
                    "watch_diagnostic_failed" -> "diagnostic_failed"
                    "" -> "missing"
                    else -> "received"
                })
                .put("diagnostic_error_type", watch.optString("diagnostic_error").takeIf { it.matches(Regex("[A-Za-z0-9_$]{1,80}")) } ?: JSONObject.NULL)
                .put("sensor_inventory_state", watch.optString("sensor_inventory_state", "unknown"))
                .put("status_age_ms", latestReport.optLong("watch_status_received_at").takeIf { it > 0 }?.let { now - it } ?: JSONObject.NULL)
                .put("sensor_inventory", watch.optJSONArray("sensor_inventory") ?: JSONArray())
                .put("permissions", watch.optJSONArray("permissions") ?: JSONArray())
                .put("acquisition_history", watch.optJSONArray("acquisition_history") ?: JSONArray())
                .put("health_services_capabilities", watch.optJSONArray("health_services_capabilities") ?: JSONArray())
                .put("health_services_capability_calls", sanitizedCapabilityCalls(watch.optJSONArray("health_services_capability_calls")))
                .put("health_services_capabilities_complete", watch.optBoolean("health_services_capabilities_complete"))
                .put("acquisition_history_scope", watch.optString("acquisition_history_scope", "unknown"))
                .put("suite_active", (watch.opt("suite_active") as? Boolean) ?: JSONObject.NULL)
                .put("health_fgs_active_by_app", (watch.opt("health_fgs_active_by_app") as? Boolean) ?: JSONObject.NULL)
                .put("wake_lock_held_by_app", (watch.opt("wake_lock_held_by_app") as? Boolean) ?: JSONObject.NULL))
        }
        val exits = JSONArray()
        (latestReport.optJSONArray("recent_process_exits") ?: JSONArray()).let { source ->
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                exits.put(JSONObject()
                    .put("reason", item.optInt("reason"))
                    .put("age_ms", item.optLong("timestamp").takeIf { it > 0 }?.let { now - it } ?: JSONObject.NULL))
            }
        }
        output.put("recent_process_exits", exits)
        output.put("notice", "本檔為工程摘要；已排除原始波形、感測生理讀值、精確時間、簽章指紋及自由文字錯誤內容。裝置型號／感測器規格與權限類型仍可能識別設備或使用情境；預覽即實際分享內容。")
        return output
    }

    private fun formatTime(time: Long): String = if (time > 0L) DateFormat.getDateTimeInstance().format(Date(time)) else "無紀錄"

    private fun formatSensors(array: JSONArray?, state: String? = "received"): String {
        val effectiveState = state ?: if (array == null) "not_queried" else "received"
        if (effectiveState.isBlank() || effectiveState == "not_queried") return "未查詢／未收到手錶清單"
        if (effectiveState.startsWith("failed:")) return "清單查詢失敗（${effectiveState.removePrefix("failed:")}）"
        if (effectiveState == "not_included_due_to_other_error") return "清單未附帶（手錶其他診斷階段失敗）"
        if (array == null) return "未收到手錶清單"
        if (array.length() == 0) return "本次回報的公開感測器清單為空"
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.joinToString("\n") { sensor ->
            "${sensor.optString("type")} · ${sensor.optString("name")} / ${sensor.optString("vendor")} · v${sensor.optInt("version")} · wake=${sensor.optBoolean("wake_up")} · mode=${sensor.optInt("reporting_mode")} · delay=${sensor.optInt("min_delay_us")}..${sensor.optInt("max_delay_us")} µs · FIFO=${sensor.optInt("fifo_reserved")}/${sensor.optInt("fifo_max")} · range=${sensor.optDouble("max_range")} · resolution=${sensor.optDouble("resolution")} · power=${sensor.optDouble("power_ma")} mA (metadata)"
        }
    }

    private fun formatCapabilityCalls(array: JSONArray?): String {
        if (array == null || array.length() == 0) return "未取得查詢呼叫追蹤（可能是舊版手錶 App）"
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.joinToString("\n") { call ->
            val started = call.optLong("started_elapsed_realtime_ns", 0L)
            val completed = call.optLong("completed_elapsed_realtime_ns", 0L)
            val duration = if (started > 0L && completed >= started) "${(completed - started) / 1_000_000} ms" else "未完成／逾時"
            "${call.optString("name", "未知")}：${call.optString("outcome", "unknown")}；$duration"
        }
    }

    private fun sanitizedCapabilityCalls(array: JSONArray?): JSONArray = JSONArray().apply {
        if (array == null) return@apply
        for (index in 0 until array.length()) {
            val call = array.optJSONObject(index) ?: continue
            val started = call.optLong("started_elapsed_realtime_ns", 0L)
            val completed = call.optLong("completed_elapsed_realtime_ns", 0L)
            put(JSONObject()
                .put("name", call.optString("name", "unknown"))
                .put("outcome", call.optString("outcome", "unknown"))
                .put("elapsed_ms", if (started > 0L && completed >= started) (completed - started) / 1_000_000 else JSONObject.NULL))
        }
    }

    private fun formatPermissions(array: JSONArray?): String {
        if (array == null || array.length() == 0) return "未取得權限清單"
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.joinToString("\n") { item ->
            val granted = item.optBoolean("granted", item.optBoolean("runtime_granted"))
            "${if (granted) "✓" else "○"} ${item.optString("permission")}"
        }
    }

    private fun formatStrings(array: JSONArray?): String = if (array == null || array.length() == 0) "未取得" else
        (0 until array.length()).joinToString("\n") { "• ${array.optString(it)}" }

    private fun formatAcquisition(array: JSONArray?): String {
        if (array == null || array.length() == 0) return "尚無本 App 採集記錄"
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.joinToString("\n") { item ->
            "${item.optString("channel")}：${item.optString("registration")}，events=${item.optInt("event_count")}，stop=${item.optString("stop_reason")}，${item.opt("stopped_age_ms") ?: "無停止記錄"} ms 前"
        }
    }
    private fun action(title: String, onClick: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; setTextColor(Color.WHITE); background = HealthVisuals.interactive(this@EngineeringDebugActivity)
        setOnClickListener { onClick() }
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.START
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val RAW_EXPORT_FILE_NAME = LocalSensorDataDeletion.RAW_EXPORT_FILE_NAME
        private const val SUMMARY_EXPORT_PREFIX = LocalSensorDataDeletion.SUMMARY_EXPORT_PREFIX
        private const val WATCH_RAW_EXPORT_PREFIX = LocalSensorDataDeletion.WATCH_RAW_EXPORT_PREFIX
        private const val STATE_REPORT = "engineering_debug_report"
        private const val STATE_SHELL_EXPORT_DEADLINES = "engineering_debug_shell_export_deadlines"
        private const val WATCH_NODE_LOOKUP_TIMEOUT_MS = 15_000L
        private const val WATCH_SEND_TIMEOUT_MS = 15_000L
        private const val WATCH_STATUS_TIMEOUT_MS = 15_000L
        const val EXTRA_QUERY_WATCH = "query_watch_on_open"
        const val EXTRA_EXPORT_RAW_ON_OPEN = "export_raw_on_open"
        const val EXTRA_START_SENSOR_SUITE = "start_sensor_suite_on_open"
        const val SHELL_EXPORT_DELAY_MS = 17_000L
        const val SHELL_SCAN_EXPORT_DELAY_MS = 8 * 60 * 1000L
        const val MAX_EXPORT_STRING_CHARS = 8 * 1024 * 1024
        const val MAX_EXPORT_TOTAL_CHARS = 24L * 1024L * 1024L
        const val MAX_EXPORT_TOTAL_BYTES = 24L * 1024L * 1024L
        const val MAX_SUITE_DUMP_BYTES = 16L * 1024L * 1024L
        const val PATH_WATCH_STATUS = "/control/status"
        const val PATH_WATCH_SENSOR_SUITE = "/control/sensor-suite"
    }
}
