package com.jaysato.xiaomihealthbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_USER_UNLOCKED,
                Intent.ACTION_BATTERY_LOW,
                Intent.ACTION_BATTERY_OKAY,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED
            )) return
        val powerEvent = action in setOf(
                Intent.ACTION_BATTERY_LOW,
                Intent.ACTION_BATTERY_OKAY,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED
        )
        WearPowerScheduler.apply(context, refreshOxygenSchedule = !powerEvent)
        if (powerEvent) return
        if (WearMeasurementPolicy.shouldRecoverInterruptedSuiteForAction(action)) {
            recoverInterruptedSuite(context, action)
        }
        if (!PassiveMonitoringSettings.isEnabled(context) && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            WorkManager.getInstance(context).cancelUniqueWork(PASSIVE_REGISTRATION_WORK)
            return
        }
        val request = OneTimeWorkRequestBuilder<PassiveRegistrationWorker>()
            .setInitialDelay(15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PASSIVE_REGISTRATION_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun recoverInterruptedSuite(context: Context, action: String) {
        val prefs = context.getSharedPreferences(WearControlService.SENSOR_SUITE_STATUS_PREFS, Context.MODE_PRIVATE)
        val currentState = prefs.getString("run_state", null)
        if (WearMeasurementPolicy.shouldRecoverInterruptedSuite(currentState)) {
            val suiteId = prefs.getString("suite_id", "").orEmpty()
            val sessionStartedAt = prefs.getLong("suite_session_started_at_ms", 0L)
            val timestamp = System.currentTimeMillis()
            val message = WearMeasurementPolicy.interruptionMessageForAction(action)
            if (WearMeasurementPolicy.hasInterruptionSyncIdentity(suiteId, sessionStartedAt)) {
                synchronized(SuiteRecoverySyncLock) {
                    prefs.edit()
                        .putString("message", message)
                        .putLong("timestamp", timestamp)
                        .putBoolean("success", false)
                        .putString("run_state", WearMeasurementPolicy.SuiteRunState.INTERRUPTED.wireValue)
                        .putLong("suite_session_ended_at_ms", timestamp)
                        .putBoolean(KEY_RECOVERY_SYNC_PENDING, true)
                        .putString(KEY_RECOVERY_SYNC_SUITE_ID, suiteId)
                        .putLong(KEY_RECOVERY_SYNC_SESSION_STARTED_AT, sessionStartedAt)
                        .putLong(KEY_RECOVERY_SYNC_TIMESTAMP, timestamp)
                        .putString(KEY_RECOVERY_SYNC_MESSAGE, message)
                        .apply()
                }
            } else {
                synchronized(SuiteRecoverySyncLock) {
                    prefs.edit()
                        .putString("message", "掃描狀態資料不完整，無法同步中斷狀態；已保留已收到的部分結果。")
                        .putLong("timestamp", timestamp)
                        .putBoolean("success", false)
                        .putString("run_state", WearMeasurementPolicy.SuiteRunState.INTERRUPTED.wireValue)
                        .putLong("suite_session_ended_at_ms", timestamp)
                        .putBoolean(KEY_RECOVERY_SYNC_PENDING, false)
                        .remove(KEY_RECOVERY_SYNC_SUITE_ID)
                        .remove(KEY_RECOVERY_SYNC_SESSION_STARTED_AT)
                        .remove(KEY_RECOVERY_SYNC_TIMESTAMP)
                        .remove(KEY_RECOVERY_SYNC_MESSAGE)
                        .apply()
                }
            }
        }
        schedulePendingRecoveryStatus(context, prefs)
    }

    private fun schedulePendingRecoveryStatus(
        context: Context,
        prefs: android.content.SharedPreferences
    ) {
        if (!prefs.getBoolean(KEY_RECOVERY_SYNC_PENDING, false)) return
        val suiteId = prefs.getString(KEY_RECOVERY_SYNC_SUITE_ID, "").orEmpty()
        if (suiteId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<SuiteInterruptionStatusSyncWorker>()
            .setInputData(workDataOf(
                SuiteInterruptionStatusSyncWorker.KEY_SUITE_ID to suiteId,
                SuiteInterruptionStatusSyncWorker.KEY_SESSION_STARTED_AT to prefs.getLong(KEY_RECOVERY_SYNC_SESSION_STARTED_AT, 0L),
                SuiteInterruptionStatusSyncWorker.KEY_TIMESTAMP to prefs.getLong(KEY_RECOVERY_SYNC_TIMESTAMP, 0L),
                SuiteInterruptionStatusSyncWorker.KEY_MESSAGE to prefs.getString(KEY_RECOVERY_SYNC_MESSAGE, "掃描中斷；保留已收到的部分結果。").orEmpty()
            ))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${RECOVERY_STATUS_SYNC_WORK}_$suiteId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val KEY_RECOVERY_SYNC_PENDING = "interrupted_status_sync_pending"
        private const val KEY_RECOVERY_SYNC_SUITE_ID = "interrupted_status_sync_suite_id"
        private const val KEY_RECOVERY_SYNC_SESSION_STARTED_AT = "interrupted_status_sync_session_started_at"
        private const val KEY_RECOVERY_SYNC_TIMESTAMP = "interrupted_status_sync_timestamp"
        private const val KEY_RECOVERY_SYNC_MESSAGE = "interrupted_status_sync_message"
        const val RECOVERY_STATUS_SYNC_WORK = "interrupted_suite_status_sync"
        const val PASSIVE_REGISTRATION_WORK = "passive_health_registration"
        const val PASSIVE_REGISTRATION_PERIODIC = "passive_health_registration_periodic"
    }
}

internal object SuiteRecoverySyncLock {}
