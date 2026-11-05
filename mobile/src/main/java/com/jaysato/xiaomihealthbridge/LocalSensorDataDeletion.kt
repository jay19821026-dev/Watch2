package com.jaysato.xiaomihealthbridge

import android.content.Context
import java.io.File

internal object LocalSensorDataDeletion {
    const val RAW_EXPORT_FILE_NAME = "healthbridge-debug.json"
    const val SUMMARY_EXPORT_PREFIX = "engineering-diagnostics-"
    const val WATCH_RAW_EXPORT_PREFIX = "watch-raw-sensors-"

    private val preferenceFiles = listOf(
        WearDataProcessor.RAW_SENSOR_PREFS,
        WearDataProcessor.FEATURE_PREFS,
        "sensor_acquisition_diagnostics",
        HeartRateDataListenerService.PREFS,
        WorkoutRouteStore.PREFS,
        EcgResearchProfileStore.PREFS,
        SensorCalibrationProfileStore.PREFS,
        OpticalBpActivity.CUFF_PREFS,
        RestingHeartRateEstimator.PREFS
    )
    internal val preferenceFilesForDeletion: List<String> get() = preferenceFiles

    data class Result(val clearedPreferences: Int, val deletedFiles: Int, val failures: Int) {
        val complete: Boolean get() = failures == 0
    }

    suspend fun clear(context: Context): Result {
        val appContext = context.applicationContext
        var clearedPreferences = 0
        var failures = 0
        try {
            SignalEvidenceDatabase.get(appContext).evidence().deleteAllEvidence()
            val legacy = appContext.getDatabasePath("ailisa-signal-evidence.db")
            if (listOf("", "-wal", "-shm", "-journal").any { File(legacy.path + it).exists() }) failures++
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failures++
        }
        preferenceFiles.forEach { name ->
            val cleared = try {
                val preferences = if (name == WearDataProcessor.RAW_SENSOR_PREFS) {
                    RawSensorStore.open(appContext)
                } else {
                    appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                }
                preferences.edit().clear().commit()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                false
            }
            if (cleared) {
                clearedPreferences++
            } else {
                failures++
            }
        }

        val externalDebugDirectory = appContext.getExternalFilesDir("debug-export")
        if (externalDebugDirectory == null) failures++
        val directories = listOfNotNull(File(appContext.cacheDir, "exports"), externalDebugDirectory)
        var deletedFiles = 0
        directories.distinctBy(File::getAbsolutePath).forEach { directory ->
            val exists = runCatching { directory.exists() }.getOrElse { true }
            if (!exists) return@forEach
            val isDirectory = runCatching { directory.isDirectory }.getOrDefault(false)
            val files = if (isDirectory) runCatching { directory.listFiles()?.toList() }.getOrNull() else null
            if (LocalSensorDeletionPolicy.directoryListingFailed(exists, isDirectory, files != null)) {
                failures++
                return@forEach
            }
            files.orEmpty().filter(::isSensorExport).forEach { file ->
                if (runCatching { file.isFile && file.delete() }.getOrDefault(false)) deletedFiles++
                else failures++
            }
        }
        return Result(clearedPreferences, deletedFiles, failures)
    }

    private fun isSensorExport(file: File): Boolean {
        return shouldDeleteExportName(file.name)
    }

    internal fun shouldDeleteExportName(name: String): Boolean =
        name == SensorSuiteDump.FILE_NAME ||
            name == "${SensorSuiteDump.FILE_NAME}.tmp" ||
            name == RAW_EXPORT_FILE_NAME ||
            name.startsWith(WATCH_RAW_EXPORT_PREFIX) ||
            name.startsWith(".$WATCH_RAW_EXPORT_PREFIX") ||
            name.startsWith(SUMMARY_EXPORT_PREFIX) ||
            name.startsWith(".${RAW_EXPORT_FILE_NAME}.") ||
            (name.startsWith(".${SensorSuiteDump.FILE_NAME}.") && name.endsWith(".tmp"))
}

internal object LocalSensorDeletionPolicy {
    fun directoryListingFailed(exists: Boolean, isDirectory: Boolean, listingSucceeded: Boolean): Boolean =
        exists && (!isDirectory || !listingSucceeded)
}
