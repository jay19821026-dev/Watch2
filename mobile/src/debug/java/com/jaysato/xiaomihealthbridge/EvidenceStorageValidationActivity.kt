package com.jaysato.xiaomihealthbridge

import android.os.Bundle
import android.util.AtomicFile
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID

class EvidenceStorageValidationActivity : ComponentActivity() {
    private var fixtureId: String? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val status = TextView(this).apply { text = "隔離測試資料庫檢查中…"; textSize = 18f; setPadding(24, 24, 24, 24) }
        setContentView(status)
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                val results = JSONArray()
                suspend fun checkCase(name: String, action: suspend () -> Unit) {
                    val result = JSONObject().put("case", name)
                    try {
                        withTimeout(120_000L) { action() }
                        result.put("status", "PASS")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        result.put("status", "FAIL").put("error_type", error.javaClass.simpleName)
                            .put("error_detail", error.message?.take(160) ?: JSONObject.NULL)
                    }
                    results.put(result)
                }
                val mode = intent.getStringExtra("validation_mode") ?: "basic"
                when (mode) {
                    "prepare_restart" -> checkCase("prepare_restart") { prepareRestart() }
                    "verify_restart" -> checkCase("verify_restart") { verifyRestart() }
                    "prepare_interrupt" -> checkCase("prepare_interrupt") { prepareInterruptedCopy() }
                    "verify_interrupt" -> checkCase("verify_interrupt") { verifyInterruptedCopy() }
                    "audit_production" -> checkCase("audit_production") { auditProduction() }
                    "raw_snapshot" -> checkCase("raw_snapshot_encryption_reopen_and_atomic_rollback") { verifyRawSnapshot() }
                    "prepare_raw_interrupt" -> checkCase("prepare_raw_interrupt") { prepareRawInterrupt() }
                    "verify_raw_interrupt" -> checkCase("verify_raw_interrupt") { verifyRawInterrupt() }
                    "raw_migration" -> checkCase("android_raw_snapshot_migration") { verifyRawMigration() }
                    "raw_preferences" -> checkCase("encrypted_preferences_apply_commit_order") { verifyRawPreferences() }
                    "raw_registry" -> checkCase("encrypted_preferences_file_and_singleton") { verifyRawRegistry() }
                    "raw_backpressure" -> checkCase("encrypted_preferences_bounded_queue") { verifyRawBackpressure() }
                    "audit_raw" -> checkCase("production_raw_snapshot_codec_and_encrypted_copy") { auditRawSnapshot() }
                    "prepare_registry_restart" -> checkCase("prepare_registry_restart") { prepareRegistryRestart() }
                    "verify_registry_restart" -> checkCase("verify_registry_restart") { verifyRegistryRestart() }
                    "basic" -> {
                        checkCase("encrypted_copy_reopen_wrong_key_and_deletion") { verifyDatabase() }
                        checkCase("wrapped_key_reopen_corruption_and_loss") { verifyKey() }
                        checkCase("cutover_recovery_at_commit_boundaries") { verifyCutover() }
                    }
                    else -> checkCase("unsupported_mode") { error("Unsupported validation mode.") }
                }
                JSONObject().put("created_at", System.currentTimeMillis()).put("test_data_only", mode !in setOf("audit_production", "audit_raw"))
                    .put("mode", mode).put("fixture_id", fixtureId ?: JSONObject.NULL)
                    .put("cases", results).also { report ->
                        val output = AtomicFile(File(filesDir, "evidence-storage-validation.json"))
                        val stream = output.startWrite()
                        try {
                            stream.write(report.toString().toByteArray(Charsets.UTF_8))
                            output.finishWrite(stream)
                        } catch (error: Exception) {
                            output.failWrite(stream)
                            throw error
                        }
                    }
            }
            status.text = report.toString(2)
        }
    }

    private val fixtureFile get() = File(filesDir, "evidence-storage-process-fixture.json")

    private fun prepareRegistryRestart() {
        check(!rawProcessFixture.exists())
        val name = "selftest_registry_${UUID.randomUUID()}".also { fixtureId = it }
        check(getSharedPreferences(name, MODE_PRIVATE).edit().putInt("count", 1).commit())
        val preferences = EncryptedRawPreferencesRegistry.open(this, name)
        preferences.edit().putInt("count", 2).apply()
        check(preferences.edit().putInt("count", 3).putString("text", "restart-test").commit())
        writeCheckpoint(rawProcessFixture, JSONObject().put("scenario", "registry_restart").put("name", name))
    }

    private fun verifyRegistryRestart() {
        check(rawProcessFixture.length() in 1..256)
        val checkpoint = JSONObject(rawProcessFixture.readText())
        check(checkpoint.getString("scenario") == "registry_restart")
        val name = checkpoint.getString("name")
        check(name.startsWith("selftest_registry_"))
        UUID.fromString(name.removePrefix("selftest_registry_"))
        fixtureId = name
        val preferences = EncryptedRawPreferencesRegistry.open(this, name)
        check(preferences.getInt("count", 0) == 3 && preferences.getString("text", null) == "restart-test")
        check(preferences.edit().clear().commit())
        check(AndroidRawSnapshotMigrationStorage(this, name).readEncrypted().isEmpty())
        listOf(".encrypted", ".committed", ".wrapped-key").forEach {
            AtomicFile(File(noBackupFilesDir, name + it)).delete()
        }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("raw-snapshot-$name-v1") }
        deleteSharedPreferences(name)
        AtomicFile(rawProcessFixture).delete()
    }

    private fun auditRawSnapshot() {
        val source = RawSensorStore.open(this).all
        val encoded = RawSnapshotCodec.encode(source)
        val file = File(noBackupFilesDir, "raw-audit-${UUID.randomUUID()}.bin")
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val key = javax.crypto.spec.SecretKeySpec(bytes, "AES")
            EncryptedRawSnapshotFile(file, key).write(source)
            val decoded = RawSnapshotCodec.encode(EncryptedRawSnapshotFile(file, key).read())
            try { check(encoded.contentEquals(decoded)) } finally { decoded.fill(0) }
            writeCheckpoint(File(filesDir, "raw-snapshot-production-audit.json"), JSONObject()
                .put("keys", source.size).put("encoded_bytes", encoded.size)
                .put("encrypted_copy_verified", true).put("source_modified", false)
                .put("formal_cutover_enabled", false))
        } finally {
            bytes.fill(0)
            encoded.fill(0)
            AtomicFile(file).delete()
            check(listOf("", ".bak", ".new").none { File(file.path + it).exists() })
        }
    }

    private fun verifyRawBackpressure() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val completed = java.util.concurrent.CountDownLatch(5)
        val writes = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val preferences = EncryptedSnapshotPreferences(emptyMap<String, Any>()) { snapshot ->
            val value = snapshot["count"] as Int
            if (value == 1) {
                entered.countDown()
                check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            }
            writes.add(value)
            completed.countDown()
        }
        try {
            preferences.edit().putInt("count", 1).apply()
            check(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            (2..5).forEach { preferences.edit().putInt("count", it).apply() }
            val applyRejected = try {
                preferences.edit().putInt("count", 6).apply()
                false
            } catch (_: java.util.concurrent.RejectedExecutionException) { true }
            check(applyRejected)
            check(preferences.getInt("count", 0) == 5)
            check(preferences.lastPersistenceFailure == "RejectedExecutionException")
            check(!preferences.edit().putInt("count", 6).commit())
            check(preferences.getInt("count", 0) == 5)
            check(preferences.lastPersistenceFailure == "RejectedExecutionException")
            release.countDown()
            check(completed.await(10, java.util.concurrent.TimeUnit.SECONDS))
            check(preferences.lastPersistenceFailure == "RejectedExecutionException")
            check(preferences.edit().putInt("count", 7).commit())
            check(preferences.lastPersistenceFailure == null)
            check(writes == listOf(1, 2, 3, 4, 5, 7))
        } finally { release.countDown() }
    }

    private fun verifyRawRegistry() {
        val name = "selftest_registry_${UUID.randomUUID()}".also { fixtureId = it }
        try {
            check(getSharedPreferences(name, MODE_PRIVATE).edit().putInt("count", 1).commit())
            val first = EncryptedRawPreferencesRegistry.open(this, name)
            val second = EncryptedRawPreferencesRegistry.open(applicationContext, name)
            check(first === second)
            first.edit().putInt("count", 2).apply()
            check(second.getInt("count", 0) == 2)
            check(second.edit().putString("text", "test").commit())
            val storage = AndroidRawSnapshotMigrationStorage(this, name)
            val durable = storage.readEncrypted()
            check(durable["count"] == 2 && durable["text"] == "test")
            check(first.edit().clear().commit())
            check(storage.readEncrypted().isEmpty())
            check(!File(applicationInfo.dataDir, "shared_prefs/$name.xml").exists())
        } finally {
            deleteSharedPreferences(name)
            listOf(".encrypted", ".committed", ".wrapped-key").forEach {
                AtomicFile(File(noBackupFilesDir, name + it)).delete()
            }
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("raw-snapshot-$name-v1") }
        }
    }

    private fun verifyRawPreferences() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val notifications = java.util.concurrent.CountDownLatch(2)
        val clearNotification = java.util.concurrent.CountDownLatch(1)
        val writes = java.util.Collections.synchronizedList(mutableListOf<Map<String, *>>())
        var fail = false
        val preferences = EncryptedSnapshotPreferences(emptyMap<String, Any>()) { snapshot ->
            if (snapshot["count"] == 1) {
                entered.countDown()
                check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            }
            check(!fail)
            writes.add(snapshot)
        }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            if (key == null) clearNotification.countDown()
            if (key == "count") notifications.countDown()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            preferences.edit().putInt("count", 1).apply()
            check(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            check(preferences.getInt("count", 0) == 1 && writes.isEmpty())
            release.countDown()
            check(preferences.edit().putInt("count", 2).commit())
            check(writes.map { it["count"] } == listOf(1, 2))
            check(notifications.await(10, java.util.concurrent.TimeUnit.SECONDS))
            fail = true
            check(!preferences.edit().putInt("count", 3).commit())
            check(preferences.lastPersistenceFailure != null)
            check(preferences.getInt("count", 0) == 3)
            fail = false
            check(writes.last()["count"] == 2)
            check(preferences.edit().commit())
            check(writes.last()["count"] == 3)
            check(preferences.edit().clear().putStringSet("set", mutableSetOf("test")).commit())
            check(clearNotification.await(10, java.util.concurrent.TimeUnit.SECONDS))
            check(!preferences.contains("count"))
            preferences.getStringSet("set", null)!!.add("changed")
            check(preferences.getStringSet("set", null) == setOf("test"))
        } finally {
            release.countDown()
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    private fun verifyRawMigration() {
        val name = "selftest_raw_${UUID.randomUUID()}".also { fixtureId = it }
        try {
            val legacy = getSharedPreferences(name, MODE_PRIVATE)
            check(legacy.edit().putString("text", "isolated-test").putLong("time", Long.MAX_VALUE)
                .putInt("count", 8).putFloat("float", -0.0f).putBoolean("flag", true)
                .putStringSet("set", setOf("test")).commit())
            val original = RawSnapshotCodec.encode(legacy.all)
            val storage = AndroidRawSnapshotMigrationStorage(this, name)
            val result = RawSnapshotMigration.open(storage)
            check(result.legacyCleanupComplete && storage.isCommitted())
            check(original.contentEquals(RawSnapshotCodec.encode(result.values)))
            val reopened = RawSnapshotMigration.open(AndroidRawSnapshotMigrationStorage(this, name))
            check(original.contentEquals(RawSnapshotCodec.encode(reopened.values)))
            check(!File(applicationInfo.dataDir, "shared_prefs/$name.xml").exists())
            original.fill(0)
        } finally {
            deleteSharedPreferences(name)
            listOf(".encrypted", ".committed", ".wrapped-key").forEach {
                AtomicFile(File(noBackupFilesDir, name + it)).delete()
            }
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("raw-snapshot-$name-v1") }
        }
    }

    private val rawProcessFixture get() = File(filesDir, "raw-snapshot-process-fixture.json")

    private fun rawProcessValues() = mapOf("text" to "process-interruption-test", "count" to 8,
        "time" to Long.MAX_VALUE, "float" to -0.0f, "flag" to true)

    private suspend fun prepareRawInterrupt() {
        check(!rawProcessFixture.exists())
        val suffix = UUID.randomUUID().toString().also { fixtureId = it }
        val passphrase = WrappedEvidencePassphraseStore("selftest-raw-$suffix", "selftest-raw-$suffix.key")
            .load(this, allowCreate = true)
        try {
            val key = javax.crypto.spec.SecretKeySpec(passphrase, "AES")
            val file = File(noBackupFilesDir, "selftest-raw-$suffix.bin")
            EncryptedRawSnapshotFile(file, key).write(rawProcessValues())
            writeCheckpoint(rawProcessFixture, JSONObject().put("fixture_id", suffix))
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(byteArrayOf(1, 2, 3))
                output.flush()
                output.fd.sync()
                writeCheckpoint(File(filesDir, "evidence-storage-validation.json"), JSONObject()
                    .put("test_data_only", true).put("mode", "prepare_raw_interrupt")
                    .put("phase", "partial_write_waiting_for_force_stop").put("fixture_id", suffix))
                kotlinx.coroutines.awaitCancellation()
            } finally { atomic.failWrite(output) }
        } finally { passphrase.fill(0) }
    }

    private fun verifyRawInterrupt() {
        check(rawProcessFixture.length() in 1..256)
        val suffix = UUID.fromString(JSONObject(rawProcessFixture.readText()).getString("fixture_id"))
            .toString().also { fixtureId = it }
        val passphrase = WrappedEvidencePassphraseStore("selftest-raw-$suffix", "selftest-raw-$suffix.key")
            .load(this, allowCreate = false)
        try {
            val key = javax.crypto.spec.SecretKeySpec(passphrase, "AES")
            val file = File(noBackupFilesDir, "selftest-raw-$suffix.bin")
            check(EncryptedRawSnapshotFile(file, key).read() == rawProcessValues())
            val next = rawProcessValues() + ("count" to 9)
            EncryptedRawSnapshotFile(file, key).write(next)
            check(EncryptedRawSnapshotFile(file, key).read() == next)
            AtomicFile(file).delete()
            check(listOf("", ".bak", ".new").none { File(file.path + it).exists() })
            AtomicFile(File(noBackupFilesDir, "selftest-raw-$suffix.key")).delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("selftest-raw-$suffix") }
            AtomicFile(rawProcessFixture).delete()
        } finally { passphrase.fill(0) }
    }

    private fun verifyRawSnapshot() {
        val suffix = UUID.randomUUID().toString().also { fixtureId = it }
        val file = File(noBackupFilesDir, "raw-snapshot-fixture-$suffix.bin")
        val key = javax.crypto.spec.SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
        val source = mapOf("text" to "isolated-test", "count" to 8, "time" to Long.MAX_VALUE,
            "float" to -0.0f, "flag" to true, "set" to setOf("test"))
        try {
            EncryptedRawSnapshotFile(file, key).write(source)
            check(EncryptedRawSnapshotFile(file, key).read() == source)
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            output.write(byteArrayOf(1, 2, 3))
            atomic.failWrite(output)
            check(EncryptedRawSnapshotFile(file, key).read() == source)
            val wrongKey = javax.crypto.spec.SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
            check(runCatching { EncryptedRawSnapshotFile(file, wrongKey).read() }.isFailure)
            check(EncryptedRawSnapshotFile(file, key).read() == source)
        } finally {
            AtomicFile(file).delete()
            check(listOf("", ".bak", ".new").none { File(file.path + it).exists() })
        }
    }

    private suspend fun auditProduction() {
        val database = SignalEvidenceDatabase.get(this)
        val source = getDatabasePath("ailisa-signal-evidence.db")
        check(listOf("", "-wal", "-shm", "-journal").none { File(source.path + it).exists() })
        val encrypted = getDatabasePath("ailisa-signal-evidence-encrypted.db")
        val header = encrypted.inputStream().use { input -> ByteArray(16).also { java.io.DataInputStream(input).readFully(it) } }
        check(!header.contentEquals("SQLite format 3\u0000".toByteArray()))
        val archive = getSharedPreferences("ailisa_archive_status", MODE_PRIVATE)
        check(archive.getBoolean("evidence_encrypted", false) && archive.getBoolean("legacy_cleanup_complete", false))
        val summary = JSONObject().put("sessions", database.evidence().sessionCount())
            .put("stored_raw_rows", database.evidence().rawCount()).put("encrypted", true).put("legacy_files_absent", true)
        writeCheckpoint(File(filesDir, "evidence-production-audit.json"), summary)
    }

    private fun restartSession() = SensorSessionEntity("restart-test", "test-only", 1, "{}")
    private fun restartRaw() = listOf(RawSensorSampleEntity("restart-test", 0, "NaN", null, 15, "UNKNOWN", -1, -1))
    private fun restartDerived() = listOf(DerivedSignalEntity("restart-result", "restart-test", "test", "v1", "test-model", "{}", "[]", 2))

    private suspend fun prepareRestart() {
        check(!fixtureFile.exists()) { "Previous test fixture must be verified first." }
        val suffix = UUID.randomUUID().toString().also { fixtureId = it }
        val store = WrappedEvidencePassphraseStore("selftest-persist-$suffix", "selftest-persist-$suffix.bin")
        val passphrase = store.load(this, allowCreate = true)
        try {
            val database = EncryptedEvidenceDatabase.build(this, "selftest-persist-$suffix.db", passphrase)
            try {
                database.evidence().appendRawSession(restartSession(), restartRaw())
                database.evidence().replaceDerived("restart-test", restartDerived())
            } finally { database.close() }
            val atomic = AtomicFile(fixtureFile)
            val stream = atomic.startWrite()
            try {
                stream.write(JSONObject().put("fixture_id", suffix).toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(stream)
            } catch (error: Exception) {
                atomic.failWrite(stream)
                throw error
            }
            check(JSONObject(fixtureFile.readText()).getString("fixture_id") == suffix)
        } catch (error: Exception) {
            cleanupRestart(suffix)
            throw error
        } finally { passphrase.fill(0) }
    }

    private suspend fun verifyRestart() {
        check(fixtureFile.length() in 1..256)
        val suffix = UUID.fromString(JSONObject(fixtureFile.readText()).getString("fixture_id")).toString()
            .also { fixtureId = it }
        try {
            val passphrase = WrappedEvidencePassphraseStore("selftest-persist-$suffix", "selftest-persist-$suffix.bin")
                .load(this, allowCreate = false)
            try {
                val database = EncryptedEvidenceDatabase.build(this, "selftest-persist-$suffix.db", passphrase)
                try {
                    check(database.evidence().readSession("restart-test") == restartSession())
                    check(database.evidence().readRaw("restart-test") == restartRaw())
                    check(database.evidence().readDerived("restart-test") == restartDerived())
                } finally { database.close() }
            } finally { passphrase.fill(0) }
        } finally { cleanupRestart(suffix) }
    }

    private fun cleanupRestart(suffix: String) {
        deleteDatabase("selftest-persist-$suffix.db")
        deleteDatabase("selftest-copy-source-$suffix.db")
        AtomicFile(File(noBackupFilesDir, "selftest-persist-$suffix.bin")).delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("selftest-persist-$suffix") }
        AtomicFile(fixtureFile).delete()
        check(!getDatabasePath("selftest-persist-$suffix.db").exists() &&
            !File(noBackupFilesDir, "selftest-persist-$suffix.bin").exists() && !fixtureFile.exists())
    }

    private fun writeCheckpoint(file: File, value: JSONObject) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
        check(JSONObject(file.readText()).toString() == value.toString())
    }

    private suspend fun prepareInterruptedCopy() {
        check(!fixtureFile.exists())
        val suffix = UUID.randomUUID().toString().also { fixtureId = it }
        val passphrase = WrappedEvidencePassphraseStore("selftest-persist-$suffix", "selftest-persist-$suffix.bin")
            .load(this, allowCreate = true)
        val source = Room.databaseBuilder(this, SignalEvidenceDatabase::class.java, "selftest-copy-source-$suffix.db").build()
        val destination = EncryptedEvidenceDatabase.build(this, "selftest-persist-$suffix.db", passphrase)
        try {
            source.evidence().appendRawSession(restartSession(), restartRaw())
            source.evidence().replaceDerived("restart-test", restartDerived())
            source.evidence().appendRawSession(restartSession().copy(sessionId = "second-test"),
                restartRaw().map { it.copy(sessionId = "second-test") })
            var copied = 0
            EvidenceDatabaseCopy.copyAndVerify(source, destination) {
                copied++
                if (copied == 1) {
                    writeCheckpoint(fixtureFile, JSONObject().put("fixture_id", suffix).put("scenario", "interrupted_copy"))
                    writeCheckpoint(File(filesDir, "evidence-storage-validation.json"), JSONObject()
                        .put("created_at", System.currentTimeMillis()).put("test_data_only", true)
                        .put("mode", "prepare_interrupt").put("fixture_id", suffix).put("phase", "copy_transaction_open"))
                    kotlinx.coroutines.awaitCancellation()
                }
            }
            error("Interruption checkpoint was not reached.")
        } finally {
            source.close()
            destination.close()
            passphrase.fill(0)
            if (!fixtureFile.exists()) cleanupRestart(suffix)
        }
    }

    private suspend fun verifyInterruptedCopy() {
        check(fixtureFile.length() in 1..256)
        val fixture = JSONObject(fixtureFile.readText())
        check(fixture.getString("scenario") == "interrupted_copy")
        val suffix = UUID.fromString(fixture.getString("fixture_id")).toString().also { fixtureId = it }
        try {
            check(getDatabasePath("selftest-copy-source-$suffix.db").exists() &&
                getDatabasePath("selftest-persist-$suffix.db").exists())
            val passphrase = WrappedEvidencePassphraseStore("selftest-persist-$suffix", "selftest-persist-$suffix.bin")
                .load(this, allowCreate = false)
            val source = Room.databaseBuilder(this, SignalEvidenceDatabase::class.java, "selftest-copy-source-$suffix.db").build()
            val destination = EncryptedEvidenceDatabase.build(this, "selftest-persist-$suffix.db", passphrase)
            try {
                check(source.evidence().sessionCount() == 2L && source.evidence().rawCount() == 2L)
                check(source.evidence().readRaw("restart-test") == restartRaw())
                check(source.evidence().readDerived("restart-test") == restartDerived())
                check(destination.evidence().sessionCount() == 0L && destination.evidence().rawCount() == 0L)
                check(destination.evidence().readDerived("restart-test").isEmpty())
                check(EvidenceDatabaseCopy.copyAndVerify(source, destination) == EvidenceDatabaseCopy.Result(2, 2, 1))
                check(destination.evidence().readRaw("restart-test") == restartRaw())
                check(destination.evidence().readDerived("restart-test") == restartDerived())
                check(destination.evidence().readRaw("second-test") == restartRaw().map { it.copy(sessionId = "second-test") })
            } finally {
                source.close()
                destination.close()
                passphrase.fill(0)
            }
        } finally { cleanupRestart(suffix) }
    }

    private suspend fun verifyDatabase() {
        val suffix = UUID.randomUUID().toString()
        val sourceName = "selftest-source-$suffix.db"
        val destinationName = "selftest-encrypted-$suffix.db"
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val source = Room.databaseBuilder(this, SignalEvidenceDatabase::class.java, sourceName).build()
        var destination = EncryptedEvidenceDatabase.build(this, destinationName, passphrase)
        val session = SensorSessionEntity("selftest", "test-only", 1, "{}")
        val raw = listOf(RawSensorSampleEntity("selftest", 0, "selftest-marker-NaN", null, 15, "UNKNOWN", -1, -1))
        val derived = listOf(DerivedSignalEntity("selftest-result", "selftest", "test", "v1", "test-model", "{}", "[]", 2))
        try {
            source.evidence().appendRawSession(session, raw)
            source.evidence().replaceDerived(session.sessionId, derived)
            destination.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_copy BEFORE INSERT ON raw_signal_samples BEGIN SELECT RAISE(ABORT, 'selftest'); END")
            var copyRejected = false
            try { EvidenceDatabaseCopy.copyAndVerify(source, destination) }
            catch (_: android.database.sqlite.SQLiteConstraintException) { copyRejected = true }
            check(copyRejected && destination.evidence().sessionCount() == 0L && destination.evidence().rawCount() == 0L)
            check(source.evidence().readRaw(session.sessionId) == raw)
            destination.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_copy")
            check(EvidenceDatabaseCopy.copyAndVerify(source, destination) == EvidenceDatabaseCopy.Result(1, 1, 1))
            destination.close()
            val bytes = getDatabasePath(destinationName).readBytes()
            check(!bytes.take(16).toByteArray().contentEquals("SQLite format 3\u0000".toByteArray()))
            check(!bytes.toString(Charsets.ISO_8859_1).contains("selftest-marker-NaN"))
            val wrong = EncryptedEvidenceDatabase.build(this, destinationName, ByteArray(32) { 7 })
            var wrongKeyRejected = false
            try { wrong.evidence().readRaw(session.sessionId) }
            catch (_: android.database.sqlite.SQLiteException) { wrongKeyRejected = true }
            finally { wrong.close() }
            check(wrongKeyRejected)
            destination = EncryptedEvidenceDatabase.build(this, destinationName, passphrase)
            check(destination.evidence().readSession(session.sessionId) == session)
            check(destination.evidence().readRaw(session.sessionId) == raw)
            check(destination.evidence().readDerived(session.sessionId) == derived)
            destination.evidence().appendOrVerifyRawSession(session.copy(createdAt = 99), raw)
            check(destination.evidence().sessionCount() == 1L && destination.evidence().rawCount() == 1L)
            check(destination.evidence().readSession(session.sessionId) == session)
            var conflictingReplayRejected = false
            try {
                destination.evidence().appendOrVerifyRawSession(session,
                    listOf(raw.single().copy(rawEncoding = "conflicting-test-only")))
            } catch (_: IllegalStateException) { conflictingReplayRejected = true }
            check(conflictingReplayRejected && destination.evidence().readRaw(session.sessionId) == raw)
            check(destination.evidence().readDerived(session.sessionId) == derived)
            destination.evidence().deleteDerived(session.sessionId)
            destination.close()
            destination = EncryptedEvidenceDatabase.build(this, destinationName, passphrase)
            check(destination.evidence().readRaw(session.sessionId) == raw && destination.evidence().readDerived(session.sessionId).isEmpty())
            destination.evidence().deleteAllEvidence()
            check(destination.evidence().sessionCount() == 0L && destination.evidence().rawCount() == 0L)
            check(source.evidence().readRaw(session.sessionId) == raw && source.evidence().readDerived(session.sessionId) == derived)
        } finally {
            source.close()
            destination.close()
            passphrase.fill(0)
            deleteDatabase(sourceName)
            deleteDatabase(destinationName)
        }
    }

    private class CutoverInterruption : RuntimeException()

    private suspend fun verifyCutover() {
        for (phase in listOf("copy_verified", "marker_committed", "legacy_deleted")) {
            val suffix = UUID.randomUUID().toString()
            val sourceName = "selftest-cutover-source-$suffix.db"
            val destinationName = "selftest-cutover-encrypted-$suffix.db"
            val markerName = "selftest-cutover-$suffix.json"
            val marker = File(noBackupFilesDir, markerName)
            val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
            try {
                val original = Room.databaseBuilder(this, SignalEvidenceDatabase::class.java, sourceName).build()
                try {
                    original.evidence().appendRawSession(restartSession(), restartRaw())
                    original.evidence().replaceDerived("restart-test", restartDerived())
                } finally { original.close() }
                var interrupted = false
                try {
                    EvidenceDatabaseCutover.open(this, sourceName, destinationName, markerName,
                        { passphrase.copyOf() }, { current -> if (current == phase) throw CutoverInterruption() })
                } catch (_: CutoverInterruption) { interrupted = true }
                check(interrupted && getDatabasePath(destinationName).exists())
                check(getDatabasePath(sourceName).exists() == (phase != "legacy_deleted"))
                check(marker.exists() == (phase != "copy_verified"))
                val recovered = EvidenceDatabaseCutover.open(this, sourceName, destinationName, markerName, { allowCreate ->
                    check(!allowCreate)
                    passphrase.copyOf()
                })
                try {
                    check(recovered.legacyCleanupComplete)
                    check(recovered.database.evidence().readSession("restart-test") == restartSession())
                    check(recovered.database.evidence().readRaw("restart-test") == restartRaw())
                    check(recovered.database.evidence().readDerived("restart-test") == restartDerived())
                } finally { recovered.database.close() }
                check(!getDatabasePath(sourceName).exists())
                deleteDatabase(destinationName)
                var missingRejected = false
                try { EvidenceDatabaseCutover.open(this, sourceName, destinationName, markerName, { passphrase.copyOf() }) }
                catch (_: IllegalStateException) { missingRejected = true }
                check(missingRejected && !getDatabasePath(destinationName).exists())
            } finally {
                passphrase.fill(0)
                deleteDatabase(sourceName)
                deleteDatabase(destinationName)
                AtomicFile(marker).delete()
            }
        }
    }

    private fun verifyKey() {
        val suffix = UUID.randomUUID().toString()
        val alias = "selftest-evidence-$suffix"
        val file = File(noBackupFilesDir, "selftest-evidence-$suffix.bin")
        val store = WrappedEvidencePassphraseStore(alias, file.name)
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val passphrase = store.load(this, allowCreate = true)
            try {
                val reloaded = store.load(this, allowCreate = false)
                try { check(passphrase.contentEquals(reloaded)) } finally { reloaded.fill(0) }
                val original = file.readBytes()
                val corrupt = original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                file.writeBytes(corrupt)
                var corruptionRejected = false
                try { store.load(this, allowCreate = true) }
                catch (_: javax.crypto.AEADBadTagException) { corruptionRejected = true }
                check(corruptionRejected && file.readBytes().contentEquals(corrupt))
                file.writeBytes(original)
                keys.deleteEntry(alias)
                var lostKeyRejected = false
                try { store.load(this, allowCreate = true) }
                catch (_: IllegalStateException) { lostKeyRejected = true }
                check(lostKeyRejected && !keys.containsAlias(alias) && file.readBytes().contentEquals(original))
            } finally { passphrase.fill(0) }
        } finally {
            AtomicFile(file).delete()
            keys.deleteEntry(alias)
        }
    }
}
