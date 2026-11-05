package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal object DiagnosticExportCleanupPolicy {
    const val CACHE_SCOPE = "cache_exports"
    const val DEBUG_SCOPE = "debug_exports"

    fun isAllowed(scope: String, name: String): Boolean = when (scope) {
        CACHE_SCOPE -> name.startsWith("engineering-diagnostics-") || name.startsWith("watch-raw-sensors-")
        DEBUG_SCOPE -> name == "healthbridge-debug.json"
        else -> false
    }
}

internal class DiagnosticExportCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val scope = inputData.getString(KEY_SCOPE) ?: return Result.failure()
        val name = inputData.getString(KEY_NAME) ?: return Result.failure()
        val maxAge = inputData.getLong(KEY_MAX_AGE, 0L)
        if (!DiagnosticExportCleanupPolicy.isAllowed(scope, name) || maxAge !in 1L..EngineeringExportRetention.RAW_EXPORT_MAX_AGE_MILLIS) {
            return Result.failure()
        }
        val directory = directoryFor(applicationContext, scope) ?: return Result.failure()
        val target = File(directory, name)
        if (runCatching { target.canonicalFile.parentFile == directory.canonicalFile }.getOrDefault(false).not()) {
            return Result.failure()
        }
        if (!target.exists()) return Result.success()
        if (!EngineeringExportRetention.isExpired(target.lastModified(), System.currentTimeMillis(), maxAge)) {
            return Result.retry()
        }
        return if (target.delete() || !target.exists()) Result.success() else Result.retry()
    }

    companion object {
        private const val KEY_SCOPE = "scope"
        private const val KEY_NAME = "name"
        private const val KEY_MAX_AGE = "max_age_ms"

        fun schedule(context: Context, file: File, maxAgeMillis: Long) {
            val canonicalParent = file.canonicalFile.parentFile ?: return
            val cacheDirectory = File(context.cacheDir, "exports").canonicalFile
            val debugDirectory = context.getExternalFilesDir("debug-export")?.canonicalFile
            val scope = when (canonicalParent) {
                cacheDirectory -> DiagnosticExportCleanupPolicy.CACHE_SCOPE
                debugDirectory -> DiagnosticExportCleanupPolicy.DEBUG_SCOPE
                else -> return
            }
            if (!DiagnosticExportCleanupPolicy.isAllowed(scope, file.name) ||
                maxAgeMillis !in 1L..EngineeringExportRetention.RAW_EXPORT_MAX_AGE_MILLIS
            ) return
            val uniqueName = MessageDigest.getInstance("SHA-256")
                .digest("$scope/${file.name}".toByteArray())
                .take(16)
                .joinToString("") { "%02x".format(it) }
            val remaining = EngineeringExportRetention.remainingMillis(file.lastModified(), System.currentTimeMillis(), maxAgeMillis) ?: 0L
            val request = OneTimeWorkRequestBuilder<DiagnosticExportCleanupWorker>()
                .setInitialDelay(remaining, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(KEY_SCOPE, scope).putString(KEY_NAME, file.name).putLong(KEY_MAX_AGE, maxAgeMillis).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "diagnostic-export-cleanup-$uniqueName",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun directoryFor(context: Context, scope: String): File? = when (scope) {
            DiagnosticExportCleanupPolicy.CACHE_SCOPE -> File(context.cacheDir, "exports")
            DiagnosticExportCleanupPolicy.DEBUG_SCOPE -> context.getExternalFilesDir("debug-export")
            else -> null
        }
    }
}
