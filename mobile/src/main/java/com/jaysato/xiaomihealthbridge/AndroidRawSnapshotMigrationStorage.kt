package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.util.AtomicFile
import java.io.File
import javax.crypto.spec.SecretKeySpec

internal class AndroidRawSnapshotMigrationStorage(context: Context, private val name: String) : RawSnapshotMigration.Storage {
    private val app = context.applicationContext
    private val snapshot: File
    private val marker: AtomicFile
    private val markerBytes = "raw-snapshot-committed:v1".toByteArray(Charsets.UTF_8)

    init {
        require(name.matches(Regex("[A-Za-z0-9_-]{1,96}")))
        snapshot = File(app.noBackupFilesDir, "$name.encrypted")
        marker = AtomicFile(File(app.noBackupFilesDir, "$name.committed"))
    }

    override fun isCommitted(): Boolean {
        if (!marker.baseFile.exists() && !File(marker.baseFile.path + ".bak").exists()) return false
        marker.openRead().use { input ->
            val bytes = ByteArray(markerBytes.size)
            java.io.DataInputStream(input).readFully(bytes)
            check(input.read() == -1 && bytes.contentEquals(markerBytes)) { "Invalid snapshot migration marker." }
        }
        return true
    }

    override fun encryptedExists(): Boolean = listOf("", ".bak", ".new").any { File(snapshot.path + it).exists() }

    override fun readLegacy(): Map<String, *> = app.getSharedPreferences(name, Context.MODE_PRIVATE).all

    override fun readEncrypted(): Map<String, *> = withFile(allowCreate = false) { it.read() }

    override fun writeEncrypted(values: Map<String, *>) = withFile(allowCreate = !encryptedExists() && !isCommitted()) {
        it.write(values)
    }

    override fun commitMarker() {
        val output = marker.startWrite()
        try {
            output.write(markerBytes)
            output.flush()
            output.fd.sync()
            marker.finishWrite(output)
        } catch (error: Exception) {
            marker.failWrite(output)
            throw error
        }
        check(isCommitted())
    }

    override fun deleteLegacy(): Boolean {
        check(isCommitted())
        readEncrypted()
        if (!app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()) return false
        val deleted = app.deleteSharedPreferences(name)
        val legacy = File(app.applicationInfo.dataDir, "shared_prefs/$name.xml")
        return deleted && listOf("", ".bak").none { File(legacy.path + it).exists() }
    }

    private fun <Result> withFile(allowCreate: Boolean, action: (EncryptedRawSnapshotFile) -> Result): Result {
        val bytes = WrappedEvidencePassphraseStore("raw-snapshot-$name-v1", "$name.wrapped-key")
            .load(app, allowCreate)
        return try {
            action(EncryptedRawSnapshotFile(snapshot, SecretKeySpec(bytes, "AES")))
        } finally { bytes.fill(0) }
    }
}
