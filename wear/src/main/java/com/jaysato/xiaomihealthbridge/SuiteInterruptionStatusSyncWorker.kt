package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SuiteInterruptionStatusSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val suiteId = inputData.getString(KEY_SUITE_ID).orEmpty()
        val sessionStartedAt = inputData.getLong(KEY_SESSION_STARTED_AT, 0L)
        val timestamp = inputData.getLong(KEY_TIMESTAMP, 0L)
        val message = inputData.getString(KEY_MESSAGE).orEmpty()
        if (suiteId.isBlank() || sessionStartedAt <= 0L || timestamp <= 0L || message.isBlank()) {
            return Result.failure()
        }
        val prefs = applicationContext.getSharedPreferences(WearControlService.SENSOR_SUITE_STATUS_PREFS, Context.MODE_PRIVATE)
        if (!WearMeasurementPolicy.shouldClearPendingInterruptionSync(
                prefs.getBoolean(KEY_PENDING, false),
                prefs.getString(KEY_PENDING_SUITE_ID, ""),
                prefs.getLong(KEY_PENDING_TIMESTAMP, 0L),
                suiteId,
                timestamp
            )
        ) return Result.success()

        val request = PutDataMapRequest.create("/control-status/$timestamp").apply {
            dataMap.putString("message", message)
            dataMap.putBoolean("success", false)
            dataMap.putLong("timestamp", timestamp)
            dataMap.putString("suite_id", suiteId)
            dataMap.putLong("suite_session_started_at_ms", sessionStartedAt)
            dataMap.putString("suite_run_state", WearMeasurementPolicy.SuiteRunState.INTERRUPTED.wireValue)
        }.asPutDataRequest().setUrgent()

        return suspendCancellableCoroutine { continuation ->
            Wearable.getDataClient(applicationContext).putDataItem(request)
                .addOnCompleteListener { task ->
                    if (!continuation.isActive) return@addOnCompleteListener
                    if (task.isSuccessful) {
                        clearPendingSyncIfMatching(suiteId, timestamp)
                        continuation.resume(Result.success())
                    } else {
                        Log.w(TAG, "Interrupted suite status sync failed: ${task.exception?.javaClass?.simpleName ?: "unknown"}")
                        continuation.resume(Result.retry())
                    }
                }
        }
    }

    private fun clearPendingSyncIfMatching(suiteId: String, timestamp: Long) {
        val prefs = applicationContext.getSharedPreferences(WearControlService.SENSOR_SUITE_STATUS_PREFS, Context.MODE_PRIVATE)
        synchronized(SuiteRecoverySyncLock) {
            if (WearMeasurementPolicy.shouldClearPendingInterruptionSync(
                    prefs.getBoolean(KEY_PENDING, false),
                    prefs.getString(KEY_PENDING_SUITE_ID, ""),
                    prefs.getLong(KEY_PENDING_TIMESTAMP, 0L),
                    suiteId,
                    timestamp
                )
            ) {
                prefs.edit().putBoolean(KEY_PENDING, false).apply()
            }
        }
    }

    companion object {
        const val KEY_SUITE_ID = "suite_id"
        const val KEY_SESSION_STARTED_AT = "session_started_at"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_MESSAGE = "message"
        private const val KEY_PENDING = "interrupted_status_sync_pending"
        private const val KEY_PENDING_SUITE_ID = "interrupted_status_sync_suite_id"
        private const val KEY_PENDING_TIMESTAMP = "interrupted_status_sync_timestamp"
        private const val TAG = "HealthBridgeStartup"
    }
}
