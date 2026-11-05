package com.jaysato.xiaomihealthbridge

import android.util.AtomicFile
import java.io.File
import javax.crypto.SecretKey

internal class EncryptedRawSnapshotFile(file: File, private val key: SecretKey) {
    private val atomic = AtomicFile(file)

    @Synchronized
    fun read(): Map<String, Any> = atomic.openRead().use {
        RawSnapshotCipher.decrypt(RawSnapshotCipher.readEnvelope(it), key)
    }

    @Synchronized
    fun write(values: Map<String, *>) {
        val envelope = RawSnapshotCipher.encrypt(values, key)
        val output = atomic.startWrite()
        try {
            output.write(envelope)
            output.flush()
            output.fd.sync()
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
        val persisted = atomic.openRead().use(RawSnapshotCipher::readEnvelope)
        check(persisted.contentEquals(envelope)) { "Encrypted snapshot persistence verification failed." }
        RawSnapshotCipher.decrypt(persisted, key)
    }
}
