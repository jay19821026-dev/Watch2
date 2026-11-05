package com.jaysato.xiaomihealthbridge

import java.io.DataInputStream
import java.io.File

internal object BoundedSnapshotFile {
    fun read(file: File, maximumBytes: Int): ByteArray {
        require(maximumBytes > 0)
        val length = file.length()
        require(length in 1L..maximumBytes.toLong()) { "Snapshot file size outside bounds" }
        return DataInputStream(file.inputStream()).use { stream ->
            val bytes = ByteArray(length.toInt())
            stream.readFully(bytes)
            require(stream.read() == -1) { "Snapshot file changed during read" }
            bytes
        }
    }
}
