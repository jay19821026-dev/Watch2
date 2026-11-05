package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class OxygenSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        status("原始 SpO₂ 通道沒有公開百分比校準；不會寫入或同步為血氧百分比")
        return Result.success()
    }

    private fun status(message: String) {
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATUS, message)
            .putLong(KEY_STATUS_TIME, System.currentTimeMillis())
            .apply()
    }

    companion object {
        const val PREFS = "oxygen_sync"
        const val KEY_STATUS = "status"
        const val KEY_STATUS_TIME = "status_time"
        const val LEGACY_UNIQUE_WORK = "periodic_oxygen_sync"
        const val UNIQUE_WORK = "periodic_oxygen_sync_v2"
        const val IMMEDIATE_WORK = "immediate_oxygen_sync"
        fun schedule(context: Context) {
            WearHealthStore.clearLegacyUnverifiedOxygen(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_STATUS_TIME).apply()
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(LEGACY_UNIQUE_WORK)
            manager.cancelUniqueWork(UNIQUE_WORK)
            status(context, "省電模式：SpO₂ 僅在手動要求時量測")
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<OxygenSyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build()
            )
        }

        private fun status(context: Context, message: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_STATUS, message)
                .putLong(KEY_STATUS_TIME, System.currentTimeMillis())
                .apply()
        }
    }
}
