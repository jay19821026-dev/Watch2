package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.wearable.Wearable
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal object WearDataItemAckRetryPolicy {
    const val MAX_AUTOMATIC_RETRIES = 6
    const val MAX_URI_LENGTH = 2048

    fun shouldRetry(runAttemptCount: Int): Boolean = runAttemptCount in 0 until MAX_AUTOMATIC_RETRIES

    fun isSupportedUri(value: String): Boolean {
        if (value.length !in 1..MAX_URI_LENGTH || !value.startsWith("wear://")) return false
        val remainder = value.substring("wear://".length)
        val pathStart = remainder.indexOf('/')
        if (pathStart < 0) return false
        if (remainder.substring(0, pathStart).isBlank()) return false
        val path = remainder.substring(pathStart)
        return WearDataItemAckTracker.route(path) != "other"
    }

    fun uniqueWorkName(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
            .take(16).joinToString("") { "%02x".format(it) }
        return "wear-data-item-delete-$digest"
    }
}

internal object WearDataItemAckTracker {
    const val PREFS = "wear_data_item_ack_diagnostics"
    const val ATTEMPTS = "attempts"
    const val SUCCEEDED = "succeeded"
    const val FAILED = "failed"
    const val LAST_ROUTE = "last_route"
    const val LAST_RESULT = "last_result"
    const val LAST_ERROR_TYPE = "last_error_type"
    const val LAST_RESULT_AT = "last_result_at"
    val ROUTES = listOf("sensor_snapshot", "control_status", "safety_alert", "heart_rate", "health_metric", "feature_snapshot", "other")
    private const val TAG = "WearDataItemAck"
    private val lock = Any()

    fun delete(context: Context, uri: Uri) {
        val route = route(uri.path.orEmpty())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        recordAttempt(prefs, route)
        try {
            Wearable.getDataClient(context).deleteDataItems(uri).addOnCompleteListener { task ->
                val success = task.isSuccessful
                val errorType = task.exception?.javaClass?.simpleName
                    ?: if (task.isCanceled) "TaskCanceled" else if (success) "" else "TaskFailed"
                recordResult(prefs, route, success, errorType)
                if (!success) {
                    Log.w(TAG, "data_item_delete_failed route=$route type=$errorType")
                    scheduleRetry(context, uri)
                }
            }
        } catch (error: RuntimeException) {
            recordResult(prefs, route, false, error.javaClass.simpleName)
            Log.w(TAG, "data_item_delete_failed route=$route type=${error.javaClass.simpleName}")
            scheduleRetry(context, uri)
        }
    }

    internal fun recordAttempt(context: Context, uri: Uri) {
        recordAttempt(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), route(uri.path.orEmpty()))
    }

    internal fun recordResult(context: Context, uri: Uri, success: Boolean, errorType: String) {
        recordResult(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), route(uri.path.orEmpty()), success, errorType)
    }

    private fun recordAttempt(prefs: android.content.SharedPreferences, route: String) {
        synchronized(lock) {
            prefs.edit()
                .putInt(ATTEMPTS, increment(prefs.getInt(ATTEMPTS, 0)))
                .putInt(routeCounterKey(route, ATTEMPTS), increment(prefs.getInt(routeCounterKey(route, ATTEMPTS), 0)))
                .apply()
        }
    }

    private fun recordResult(
        prefs: android.content.SharedPreferences,
        route: String,
        success: Boolean,
        errorType: String
    ) {
        synchronized(lock) {
            val counter = if (success) SUCCEEDED else FAILED
            prefs.edit()
                .putInt(counter, increment(prefs.getInt(counter, 0)))
                .putInt(routeCounterKey(route, counter), increment(prefs.getInt(routeCounterKey(route, counter), 0)))
                .putString(LAST_ROUTE, route)
                .putString(LAST_RESULT, if (success) "success" else "failure")
                .putString(LAST_ERROR_TYPE, errorType.take(80))
                .putLong(LAST_RESULT_AT, System.currentTimeMillis())
                .apply()
        }
    }

    private fun scheduleRetry(context: Context, uri: Uri) {
        val uriText = uri.toString()
        if (!WearDataItemAckRetryPolicy.isSupportedUri(uriText)) {
            Log.w(TAG, "data_item_delete_retry_skipped reason=unsupported_uri")
            return
        }
        try {
            val request = OneTimeWorkRequestBuilder<WearDataItemAckRetryWorker>()
                .setInputData(Data.Builder().putString(WearDataItemAckRetryWorker.KEY_URI, uriText).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WearDataItemAckRetryPolicy.uniqueWorkName(uriText),
                ExistingWorkPolicy.KEEP,
                request
            )
        } catch (error: RuntimeException) {
            Log.w(TAG, "data_item_delete_retry_schedule_failed type=${error.javaClass.simpleName}")
        }
    }

    internal fun recordRetryExhausted(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val route = route(uri.path.orEmpty())
        synchronized(lock) {
            prefs.edit()
                .putString(LAST_ROUTE, route)
                .putString(LAST_RESULT, "retry_exhausted")
                .putString(LAST_ERROR_TYPE, "RetryLimitReached")
                .putLong(LAST_RESULT_AT, System.currentTimeMillis())
                .apply()
        }
    }

    internal fun route(path: String): String = when {
        path.startsWith("/sensor-suite/") || path.startsWith("/raw-sensor/") -> "sensor_snapshot"
        path.startsWith("/control-status/") -> "control_status"
        path.startsWith(MobileSafetyAlerts.PATH) -> "safety_alert"
        path.startsWith("/feature-snapshot/") -> "feature_snapshot"
        path.startsWith(HeartRateDataListenerService.HEART_RATE_PATH) -> "heart_rate"
        path.startsWith(HeartRateDataListenerService.HEALTH_PATH_PREFIX) -> "health_metric"
        else -> "other"
    }

    internal fun increment(value: Int): Int = if (value == Int.MAX_VALUE) value else value + 1

    internal fun routeCounterKey(routeCategory: String, counter: String): String =
        "route_${routeCategory.takeIf { it in ROUTES } ?: "other"}_$counter"
}
