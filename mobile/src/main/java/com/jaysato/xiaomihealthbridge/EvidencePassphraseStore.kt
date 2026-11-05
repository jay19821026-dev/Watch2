package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class WrappedEvidencePassphraseStore(private val keyAlias: String, private val fileName: String) {
    init {
        require(keyAlias.isNotBlank())
        require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName && fileName !in setOf(".", ".."))
    }
    private val header = byteArrayOf(65, 69, 75, 1)
    private val associatedData = "ailisa-signal-evidence:v1".toByteArray(Charsets.UTF_8)

    @Synchronized
    fun load(context: Context, allowCreate: Boolean): ByteArray {
        val file = File(context.noBackupFilesDir, fileName)
        val atomic = AtomicFile(file)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (file.exists() || File(file.path + ".bak").exists()) {
            val wrapped = readEnvelope(atomic)
            check(wrapped.take(4).toByteArray().contentEquals(header)) { "Unsupported evidence key envelope." }
            val key = store.getKey(keyAlias, null) as? SecretKey
                ?: error("Evidence wrapping key unavailable.")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped.copyOfRange(4, 16)))
            cipher.updateAAD(associatedData)
            return cipher.doFinal(wrapped.copyOfRange(16, 64)).also {
                check(it.size == 32) { "Invalid evidence passphrase." }
            }
        }
        check(allowCreate) { "Evidence passphrase missing; existing evidence is preserved." }
        val key = (store.getKey(keyAlias, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).run {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(associatedData)
            check(cipher.iv.size == 12)
            val envelope = header + cipher.iv + cipher.doFinal(passphrase)
            val output = atomic.startWrite()
            try {
                output.write(envelope)
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
            check(readEnvelope(atomic).contentEquals(envelope)) { "Evidence key persistence could not be verified." }
            return passphrase
        } catch (error: Exception) {
            passphrase.fill(0)
            throw error
        }
    }

    private fun readEnvelope(file: AtomicFile): ByteArray = java.io.DataInputStream(file.openRead()).use { input ->
        val bytes = ByteArray(64)
        input.readFully(bytes)
        check(input.read() == -1) { "Invalid evidence key envelope." }
        bytes
    }
}

internal object EvidencePassphraseStore {
    private val store = WrappedEvidencePassphraseStore("ailisa-evidence-wrap-v1", "ailisa-evidence-key-v1.bin")

    fun load(context: Context, allowCreate: Boolean): ByteArray = store.load(context, allowCreate)
}
