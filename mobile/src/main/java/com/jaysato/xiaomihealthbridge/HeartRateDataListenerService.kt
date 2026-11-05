package com.jaysato.xiaomihealthbridge

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HeartRateDataListenerService : WearableListenerService() {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "wear_data_async_processing_failed type=${error.javaClass.simpleName}")
        }
    )

    override fun onDataChanged(events: DataEventBuffer) {
        WearDataEventDispatchPolicy.dispatch(
            events = events,
            shouldProcess = { it.type == DataEvent.TYPE_CHANGED },
            process = { event -> WearDataProcessor.process(applicationContext, event.dataItem, scope) },
            onFailure = { error ->
                Log.e(TAG, "wear_data_event_process_failed; remaining events will continue type=${error.javaClass.simpleName}")
            }
        )
    }

    override fun onMessageReceived(event: com.google.android.gms.wearable.MessageEvent) {
        if (event.path != DEBUG_STATUS_RESPONSE_PATH) return
        try {
            if (event.data.size > MAX_DEBUG_STATUS_RESPONSE_BYTES) {
                Log.w(TAG, "debug_status_response_rejected reason=payload_too_large")
                return
            }
            val report = org.json.JSONObject(event.data.toString(Charsets.UTF_8))
            val requestId = report.optString("request_id")
            val status = report.optString("status")
            val timestamp = report.optLong("watch_timestamp")
            val receivedAt = System.currentTimeMillis()
            val rejectionReason = DebugStatusResponseValidation.rejectionReason(requestId, status, timestamp)
            if (rejectionReason != null) {
                Log.w(TAG, "debug_status_response_rejected reason=$rejectionReason")
                return
            }
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            synchronized(DEBUG_STATUS_LOCK) {
                val oldIds = prefs.getString(KEY_DEBUG_STATUS_RESPONSE_IDS, null)
                val responseIds = DebugStatusResponseLedger.appendRequestId(oldIds, requestId)
                val oldIdSet = oldIds.orEmpty().split(',').filter { it.isNotBlank() }.toSet()
            val safeStatus = DebugStatusResponseValidation.sanitizeStatusForStorage(status)
            val safeReport = org.json.JSONObject()
                .put("request_id", requestId)
                .put("status", safeStatus)
                .put("watch_timestamp", timestamp)
                .put("phone_received_at_ms", receivedAt)
                .put("health_services_capabilities", boundedStrings(report.optJSONArray("health_services_capabilities"), MAX_CAPABILITIES, MAX_DIAGNOSTIC_STRING_LENGTH))
                .put("sensor_inventory_state", report.optString("sensor_inventory_state", "unknown").take(80))
                .put("sensor_inventory", boundedObjects(
                    report.optJSONArray("sensor_inventory"), MAX_SENSOR_INVENTORY,
                    stringFields = mapOf("name" to 100, "vendor" to 80, "type" to 120),
                    booleanFields = setOf("wake_up"),
                    integerFields = setOf("version", "reporting_mode", "min_delay_us", "max_delay_us", "fifo_max", "fifo_reserved"),
                    decimalFields = setOf("max_range", "resolution", "power_ma")
                ))
                .put("health_services_capabilities_complete", report.optBoolean("health_services_capabilities_complete", false))
                .put("health_services_capability_calls", boundedObjects(
                    report.optJSONArray("health_services_capability_calls"), MAX_CAPABILITY_CALLS,
                    stringFields = mapOf("name" to 80, "outcome" to 40),
                    longFields = setOf("started_elapsed_realtime_ns", "completed_elapsed_realtime_ns")
                ))
                .put("permissions", boundedObjects(
                    report.optJSONArray("permissions"), MAX_PERMISSIONS,
                    stringFields = mapOf("permission" to 160),
                    booleanFields = setOf("granted")
                ))
                .put("acquisition_history", boundedObjects(
                    report.optJSONArray("acquisition_history"), MAX_ACQUISITION_HISTORY,
                    stringFields = mapOf("channel" to 60, "registration" to 40, "stop_reason" to 80),
                    integerFields = setOf("event_count"),
                    longFields = setOf("stopped_age_ms")
                ))
                .put("acquisition_history_scope", report.optString("acquisition_history_scope", "unknown").take(80))
                .put("device_model", report.optString("device_model", "unknown").take(100))
                .put("android_release", report.optString("android_release", "unknown").take(32))
                .put("android_api", report.optInt("android_api", -1))
                .put("app_package", report.optString("app_package", "unknown").take(128))
                .put("app_version", report.optString("app_version", "unknown").take(64))
                .put("app_version_code", report.optLong("app_version_code", -1L))
                .put("app_target_sdk", report.optInt("app_target_sdk", -1))
                .put("signer_sha256", boundedStrings(report.optJSONArray("signer_sha256"), MAX_SIGNER_DIGESTS, MAX_SIGNER_LENGTH, Regex("[A-Fa-f0-9]{64}")))
                .put("suite_active", report.optBoolean("suite_active", false))
                .put("health_fgs_active_by_app", report.optBoolean("health_fgs_active_by_app", false))
                .put("wake_lock_held_by_app", report.optBoolean("wake_lock_held_by_app", false))
                .put("health_connect", report.optString("health_connect", "unknown").take(80))
                .put("diagnostic_error", report.optString("diagnostic_error", "").take(80))
                val reportForUi = safeReport
                val editor = prefs.edit()
                    .putString(DebugStatusResponseLedger.responseKey(requestId), reportForUi.toString())
                    .putString(KEY_DEBUG_STATUS_RESPONSE_IDS, responseIds.joinToString(","))
                (oldIdSet - responseIds.toSet()).forEach { expiredId ->
                    if (expiredId.matches(Regex("[A-Za-z0-9-]{16,64}"))) {
                        editor.remove(DebugStatusResponseLedger.responseKey(expiredId))
                    }
                }
                if (DebugStatusResponseLedger.isActiveResponse(
                        prefs.getString(KEY_DEBUG_STATUS_ACTIVE_REQUEST_ID, null),
                        requestId
                    )
                ) {
                    editor.putString(KEY_DEBUG_STATUS_REQUEST_ID, requestId)
                        .putString(KEY_DEBUG_STATUS, safeStatus)
                        .putString(KEY_DEBUG_STATUS_MEASURE_API, reportForUi.optJSONArray("health_services_capabilities")?.toString() ?: "unknown")
                        .putString(KEY_DEBUG_STATUS_REPORT, reportForUi.toString())
                        .putLong(KEY_DEBUG_STATUS_TIME, timestamp)
                        .putLong(KEY_DEBUG_STATUS_RECEIVED_AT, receivedAt)
                }
                editor.apply()
            }
        } catch (error: Exception) {
            Log.e(TAG, "debug_status_response_processing_failed type=${error.javaClass.simpleName}")
        }
    }

    private fun boundedStrings(
        source: org.json.JSONArray?,
        maxItems: Int,
        maxLength: Int,
        pattern: Regex? = null
    ): org.json.JSONArray = org.json.JSONArray().apply {
        if (source == null) return@apply
        for (index in 0 until minOf(source.length(), maxItems)) {
            val value = source.optString(index, "")
            if (value.isNotBlank() && (pattern == null || pattern.matches(value))) put(value.take(maxLength))
        }
    }

    private fun boundedObjects(
        source: org.json.JSONArray?,
        maxItems: Int,
        stringFields: Map<String, Int> = emptyMap(),
        booleanFields: Set<String> = emptySet(),
        integerFields: Set<String> = emptySet(),
        longFields: Set<String> = emptySet(),
        decimalFields: Set<String> = emptySet()
    ): org.json.JSONArray = org.json.JSONArray().apply {
        if (source == null) return@apply
        for (index in 0 until minOf(source.length(), maxItems)) {
            val item = source.optJSONObject(index) ?: continue
            val safeItem = org.json.JSONObject()
            stringFields.forEach { (key, maxLength) ->
                if (item.has(key) && item.opt(key) is String) safeItem.put(key, item.optString(key).take(maxLength))
            }
            booleanFields.forEach { key ->
                if (item.has(key) && item.opt(key) is Boolean) safeItem.put(key, item.optBoolean(key))
            }
            integerFields.forEach { key ->
                if (item.has(key) && item.opt(key) is Number) {
                    val value = item.optLong(key)
                    if (value in 0L..MAX_DIAGNOSTIC_INTEGER) safeItem.put(key, value)
                }
            }
            longFields.forEach { key ->
                if (item.has(key) && item.opt(key) is Number) {
                    val value = item.optLong(key)
                    if (value >= 0L) safeItem.put(key, value)
                }
                else if (item.has(key) && item.isNull(key)) safeItem.put(key, org.json.JSONObject.NULL)
            }
            decimalFields.forEach { key ->
                if (item.has(key) && item.opt(key) is Number) {
                    val value = item.optDouble(key)
                    val maximum = if (key == "power_ma") MAX_SENSOR_POWER_MILLIAMPS else MAX_SENSOR_SPEC_VALUE
                    if (value.isFinite() && value in 0.0..maximum) safeItem.put(key, value)
                }
            }
            put(safeItem)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HealthBridgeMessage"
        private const val MAX_DEBUG_STATUS_RESPONSE_BYTES = 256 * 1024
        private const val MAX_SENSOR_INVENTORY = 128
        private const val MAX_PERMISSIONS = 128
        private const val MAX_ACQUISITION_HISTORY = 32
        private const val MAX_CAPABILITIES = 32
        private const val MAX_CAPABILITY_CALLS = 16
        private const val MAX_SIGNER_DIGESTS = 8
        private const val MAX_DIAGNOSTIC_STRING_LENGTH = 160
        private const val MAX_SIGNER_LENGTH = 64
        private const val MAX_DIAGNOSTIC_INTEGER = 100_000_000L
        private const val MAX_SENSOR_SPEC_VALUE = 1_000_000.0
        private const val MAX_SENSOR_POWER_MILLIAMPS = 10_000.0
        const val HEART_RATE_PATH = "/heart-rate"
        const val HEALTH_PATH_PREFIX = "/health/"
        const val KEY_BPM = "bpm"
        const val KEY_TIMESTAMP = "timestamp"
        const val PREFS = "sync_status"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_RECEIVED = "last_received"
        const val KEY_LAST_HEALTH_CONNECT_WRITE = "last_health_connect_write"
        const val KEY_LAST_ERROR = "last_error"
        const val DEBUG_STATUS_RESPONSE_PATH = "/debug/status-response"
        const val KEY_DEBUG_STATUS_REQUEST_ID = "debug_status_request_id"
        const val KEY_DEBUG_STATUS = "debug_status"
        const val KEY_DEBUG_STATUS_MEASURE_API = "debug_status_measure_api"
        const val KEY_DEBUG_STATUS_REPORT = "debug_status_report"
        const val KEY_DEBUG_STATUS_TIME = "debug_status_time"
        const val KEY_DEBUG_STATUS_RECEIVED_AT = "debug_status_received_at"
        const val KEY_DEBUG_STATUS_ACTIVE_REQUEST_ID = "debug_status_active_request_id"
        const val KEY_DEBUG_STATUS_RESPONSE_IDS = "debug_status_response_ids"
        private val DEBUG_STATUS_LOCK = Any()
    }
}
