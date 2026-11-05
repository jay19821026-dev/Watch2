package com.jaysato.xiaomihealthbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class EncryptedEvidenceDatabaseTest {
    @Test fun failedCopyRollsBackDestinationWithoutChangingSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = androidx.room.Room.inMemoryDatabaseBuilder(context, SignalEvidenceDatabase::class.java).build()
        val name = "encrypted-copy-rollback-test-${java.util.UUID.randomUUID()}.db"
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val destination = EncryptedEvidenceDatabase.build(context, name, passphrase)
        try {
            listOf("first", "second").forEach { sessionId ->
                source.evidence().appendRawSession(SensorSessionEntity(sessionId, "test-only", 1, "{}"),
                    listOf(RawSensorSampleEntity(sessionId, 0, "NaN", null, null, "UNKNOWN", -1, -1)))
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                destination.openHelper.writableDatabase.execSQL(
                    "CREATE TRIGGER reject_second_sample BEFORE INSERT ON raw_signal_samples WHEN NEW.sessionId = 'second' BEGIN SELECT RAISE(ABORT, 'test copy failure'); END")
            }
            var rejected = false
            try { EvidenceDatabaseCopy.copyAndVerify(source, destination) }
            catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
            assertTrue(rejected)
            assertEquals(0L, destination.evidence().sessionCount())
            assertEquals(0L, destination.evidence().rawCount())
            assertEquals(2L, source.evidence().sessionCount())
            assertEquals(2L, source.evidence().rawCount())
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                destination.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_second_sample")
            }
            assertEquals(EvidenceDatabaseCopy.Result(2, 2, 0), EvidenceDatabaseCopy.copyAndVerify(source, destination))
        } finally {
            source.close()
            destination.close()
            passphrase.fill(0)
            context.deleteDatabase(name)
        }
    }

    @Test fun copyPreservesRawTimingAndProvenanceAndRejectsPopulatedDestination() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = java.util.UUID.randomUUID().toString()
        val sourceName = "plaintext-copy-test-$suffix.db"
        val destinationName = "encrypted-copy-test-$suffix.db"
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val source = androidx.room.Room.databaseBuilder(context, SignalEvidenceDatabase::class.java, sourceName).build()
        val destination = EncryptedEvidenceDatabase.build(context, destinationName, passphrase)
        val session = SensorSessionEntity("copy", "test-only", 1, "{\"clock\":\"UNKNOWN\"}")
        val raw = listOf(RawSensorSampleEntity("copy", 0, "NaN", null, 15, "UNKNOWN", -1, -1))
        val derived = listOf(DerivedSignalEntity("copy-result", "copy", "test", "v1", "test-model", "{\"origin\":\"IMPUTED\"}", "[]", 2))
        try {
            source.evidence().appendRawSession(session, raw)
            source.evidence().replaceDerived("copy", derived)
            assertEquals(EvidenceDatabaseCopy.Result(1, 1, 1), EvidenceDatabaseCopy.copyAndVerify(source, destination))
            var rejected = false
            try { EvidenceDatabaseCopy.copyAndVerify(source, destination) }
            catch (_: IllegalStateException) { rejected = true }
            assertTrue(rejected)
            destination.close()
            val reopened = EncryptedEvidenceDatabase.build(context, destinationName, passphrase)
            try {
                assertEquals(session, reopened.evidence().readSession("copy"))
                assertEquals(raw, reopened.evidence().readRaw("copy"))
                assertEquals(derived, reopened.evidence().readDerived("copy"))
                reopened.evidence().deleteAllEvidence()
            } finally { reopened.close() }
            assertEquals(raw, source.evidence().readRaw("copy"))
            assertEquals(derived, source.evidence().readDerived("copy"))
        } finally {
            source.close()
            destination.close()
            passphrase.fill(0)
            context.deleteDatabase(sourceName)
            context.deleteDatabase(destinationName)
        }
    }

    @Test fun encryptedEvidenceReopensRejectsWrongKeyAndDoesNotExposeRawText() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "encrypted-evidence-test-${java.util.UUID.randomUUID()}.db"
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val raw = listOf(RawSensorSampleEntity("encrypted", 0, "test-raw-evidence-marker", null, null, "UNKNOWN", -1, -1))
        try {
            val first = EncryptedEvidenceDatabase.build(context, name, passphrase)
            try {
                first.evidence().appendRawSession(SensorSessionEntity("encrypted", "test-only", 1, "{}"), raw)
            } finally { first.close() }
            val bytes = context.getDatabasePath(name).readBytes()
            assertFalse(bytes.take(16).toByteArray().contentEquals("SQLite format 3\u0000".toByteArray()))
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("test-raw-evidence-marker"))
            val wrong = EncryptedEvidenceDatabase.build(context, name, ByteArray(32) { 7 })
            var rejected = false
            try { wrong.evidence().readRaw("encrypted") }
            catch (_: android.database.sqlite.SQLiteException) { rejected = true }
            finally { wrong.close() }
            assertTrue(rejected)
            val reopened = EncryptedEvidenceDatabase.build(context, name, passphrase)
            try {
                assertEquals(raw, reopened.evidence().readRaw("encrypted"))
                reopened.evidence().deleteAllEvidence()
                assertEquals(0L, reopened.evidence().sessionCount())
            } finally { reopened.close() }
        } finally {
            passphrase.fill(0)
            context.deleteDatabase(name)
        }
    }
}
