package com.jaysato.xiaomihealthbridge

import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object RawSnapshotCipher {
    private val header = byteArrayOf(82, 83, 69, 1)
    private val associatedData = "ailisa:raw-snapshot:v1".toByteArray(Charsets.UTF_8)
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val MAX_PLAINTEXT_BYTES = 8 * 1024 * 1024
    private const val MIN_ENVELOPE_BYTES = 4 + IV_BYTES + TAG_BYTES + 8
    private const val MAX_ENVELOPE_BYTES = 4 + IV_BYTES + TAG_BYTES + MAX_PLAINTEXT_BYTES

    fun encrypt(values: Map<String, *>, key: SecretKey): ByteArray {
        val plaintext = RawSnapshotCodec.encode(values)
        try {
            val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            cipher.updateAAD(associatedData)
            return header + iv + cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    fun decrypt(envelope: ByteArray, key: SecretKey): Map<String, Any> {
        require(envelope.size in MIN_ENVELOPE_BYTES..MAX_ENVELOPE_BYTES)
        require(envelope.copyOfRange(0, header.size).contentEquals(header))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128,
            envelope.copyOfRange(header.size, header.size + IV_BYTES)))
        cipher.updateAAD(header)
        cipher.updateAAD(associatedData)
        val plaintext = cipher.doFinal(envelope, header.size + IV_BYTES,
            envelope.size - header.size - IV_BYTES)
        return try {
            RawSnapshotCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    fun readEnvelope(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(count > 0) { "Snapshot stream made no progress." }
            require(output.size() <= MAX_ENVELOPE_BYTES - count)
            output.write(buffer, 0, count)
        }
        return output.toByteArray().also { require(it.size >= MIN_ENVELOPE_BYTES) }
    }
}
