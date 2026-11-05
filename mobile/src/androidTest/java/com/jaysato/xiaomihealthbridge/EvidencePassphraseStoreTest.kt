package com.jaysato.xiaomihealthbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class EvidencePassphraseStoreTest {
    @Test fun reloadAndCorruptionAndMissingKeyPreserveOriginalEnvelope() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = java.util.UUID.randomUUID().toString()
        val alias = "test-only-evidence-$suffix"
        val fileName = "test-only-evidence-$suffix.bin"
        val file = File(context.noBackupFilesDir, fileName)
        val store = WrappedEvidencePassphraseStore(alias, fileName)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            var missingRejected = false
            try { store.load(context, allowCreate = false) }
            catch (_: IllegalStateException) { missingRejected = true }
            assertTrue(missingRejected)
            assertFalse(file.exists())
            val passphrase = store.load(context, allowCreate = true)
            try {
                val reloaded = store.load(context, allowCreate = false)
                try { assertArrayEquals(passphrase, reloaded) } finally { reloaded.fill(0) }
                val original = file.readBytes()
                assertEquals(64, original.size)
                assertFalse(original.copyOfRange(16, 48).contentEquals(passphrase))
                val corrupt = original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                file.writeBytes(corrupt)
                var corruptionRejected = false
                try { store.load(context, allowCreate = true) }
                catch (_: javax.crypto.AEADBadTagException) { corruptionRejected = true }
                assertTrue(corruptionRejected)
                assertArrayEquals(corrupt, file.readBytes())
                file.writeBytes(original)
                keyStore.deleteEntry(alias)
                var lostKeyRejected = false
                try { store.load(context, allowCreate = true) }
                catch (_: IllegalStateException) { lostKeyRejected = true }
                assertTrue(lostKeyRejected)
                assertArrayEquals(original, file.readBytes())
                assertFalse(keyStore.containsAlias(alias))
            } finally { passphrase.fill(0) }
        } finally {
            android.util.AtomicFile(file).delete()
            keyStore.deleteEntry(alias)
        }
    }
}
