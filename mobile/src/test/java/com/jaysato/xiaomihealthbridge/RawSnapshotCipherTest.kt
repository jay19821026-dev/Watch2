package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class RawSnapshotCipherTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test fun roundTripsWithoutChangingInput() {
        val source = mapOf("raw" to "研究訊號", "time" to Long.MAX_VALUE, "count" to 0)
        assertEquals(source, RawSnapshotCipher.decrypt(RawSnapshotCipher.encrypt(source, key), key))
        assertEquals("研究訊號", source["raw"])
    }

    @Test fun repeatedWritesUseDifferentNonces() {
        val source = mapOf("raw" to 1)
        assertTrue(!RawSnapshotCipher.encrypt(source, key).contentEquals(RawSnapshotCipher.encrypt(source, key)))
    }

    @Test fun wrongKeyCannotReturnEmptyOrPartialData() {
        val encrypted = RawSnapshotCipher.encrypt(mapOf("raw" to 1), key)
        val other = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
        assertTrue(runCatching { RawSnapshotCipher.decrypt(encrypted, other) }.isFailure)
    }

    @Test fun rejectsTamperedHeaderNoncePayloadAndTag() {
        val encrypted = RawSnapshotCipher.encrypt(mapOf("raw" to "evidence"), key)
        listOf(0, 4, 16, encrypted.lastIndex).forEach { index ->
            val changed = encrypted.copyOf()
            changed[index] = (changed[index].toInt() xor 1).toByte()
            assertTrue(runCatching { RawSnapshotCipher.decrypt(changed, key) }.isFailure)
        }
    }

    @Test fun rejectsTruncatedAndExtendedEnvelope() {
        val encrypted = RawSnapshotCipher.encrypt(mapOf("raw" to 1), key)
        assertTrue(runCatching { RawSnapshotCipher.decrypt(encrypted.copyOf(encrypted.size - 1), key) }.isFailure)
        assertTrue(runCatching { RawSnapshotCipher.decrypt(encrypted + byteArrayOf(0), key) }.isFailure)
    }

    @Test fun boundedStreamReadPreservesEnvelope() {
        val encrypted = RawSnapshotCipher.encrypt(emptyMap<String, Any>(), key)
        assertTrue(encrypted.contentEquals(RawSnapshotCipher.readEnvelope(encrypted.inputStream())))
        assertTrue(runCatching { RawSnapshotCipher.readEnvelope(byteArrayOf(0).inputStream()) }.isFailure)
        assertTrue(runCatching {
            RawSnapshotCipher.readEnvelope(ByteArray(8 * 1024 * 1024 + 33).inputStream())
        }.isFailure)
    }
}
