package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.util.AtomicFile
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

internal object EvidenceDatabaseCutover {
    data class Result(val database: SignalEvidenceDatabase, val legacyCleanupComplete: Boolean)

    suspend fun open(
        context: Context,
        sourceName: String,
        destinationName: String,
        markerName: String,
        loadPassphrase: (allowCreate: Boolean) -> ByteArray,
        onPhase: suspend (String) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        listOf(sourceName, destinationName, markerName).forEach {
            require(it.matches(Regex("[A-Za-z0-9_.-]{1,128}")) && it !in setOf(".", ".."))
        }
        require(sourceName != destinationName)
        val app = context.applicationContext
        val sourceFile = app.getDatabasePath(sourceName)
        val destinationFile = app.getDatabasePath(destinationName)
        val marker = AtomicFile(File(app.noBackupFilesDir, markerName))
        val ready = marker.baseFile.exists() || File(marker.baseFile.path + ".bak").exists()
        if (ready) {
            val record = readMarker(marker)
            check(record.getInt("version") == 1 && record.getString("state") == "encrypted_ready" &&
                record.getString("destination") == destinationName)
            check(destinationFile.exists()) { "Committed evidence database missing." }
        } else {
            check(sourceFile.exists() || databaseFiles(sourceFile).none(File::exists)) { "Legacy evidence file incomplete." }
            check(destinationFile.exists() || databaseFiles(destinationFile).none(File::exists)) { "Encrypted evidence file incomplete." }
        }
        val passphrase = loadPassphrase(!ready && !destinationFile.exists())
        var encrypted: SignalEvidenceDatabase? = null
        try {
            val database = EncryptedEvidenceDatabase.build(app, destinationName, passphrase).also { encrypted = it }
            database.evidence().sessionCount()
            if (!ready) {
                if (sourceFile.exists()) {
                    val original = Room.databaseBuilder(app, SignalEvidenceDatabase::class.java, sourceName).build()
                    try {
                        if (database.evidence().sessionCount() == 0L) EvidenceDatabaseCopy.copyAndVerify(original, database)
                        else EvidenceDatabaseCopy.verify(original, database)
                    } finally { original.close() }
                } else {
                    check(database.evidence().sessionCount() == 0L && database.evidence().rawCount() == 0L) {
                        "Uncommitted evidence has no source for verification."
                    }
                }
                onPhase("copy_verified")
                val record = JSONObject().put("version", 1).put("state", "encrypted_ready").put("destination", destinationName)
                val output = marker.startWrite()
                try {
                    output.write(record.toString().toByteArray(Charsets.UTF_8))
                    marker.finishWrite(output)
                } catch (error: Exception) {
                    marker.failWrite(output)
                    throw error
                }
                check(readMarker(marker).toString() == record.toString()) { "Cutover marker persistence failed." }
                onPhase("marker_committed")
            }
            runCatching { app.deleteDatabase(sourceName) }
            val cleaned = databaseFiles(sourceFile).none(File::exists)
            onPhase(if (cleaned) "legacy_deleted" else "legacy_cleanup_pending")
            Result(database, cleaned)
        } catch (error: Throwable) {
            runCatching { encrypted?.close() }
            throw error
        } finally { passphrase.fill(0) }
    }

    private fun databaseFiles(file: File): List<File> = listOf("", "-wal", "-shm", "-journal").map { File(file.path + it) }

    private fun readMarker(file: AtomicFile): JSONObject = file.openRead().use { input ->
        val bytes = ByteArray(513)
        var count = 0
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read == -1) break
            count += read
        }
        check(count in 1..512) { "Invalid evidence cutover marker." }
        JSONObject(bytes.copyOf(count).toString(Charsets.UTF_8))
    }
}
