package com.jaysato.xiaomihealthbridge

import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object EngineeringExportRetention {
    const val RAW_EXPORT_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
    const val SUMMARY_MAX_AGE_MILLIS = 15 * 60 * 1000L
    const val MAX_ATOMIC_EXPORT_BYTES = 24L * 1024L * 1024L

    fun isExpired(lastModified: Long, now: Long, maxAgeMillis: Long): Boolean =
        lastModified <= 0L || now < lastModified || now - lastModified >= maxAgeMillis

    fun remainingMillis(lastModified: Long, now: Long, maxAgeMillis: Long): Long? {
        if (isExpired(lastModified, now, maxAgeMillis)) return null
        return maxAgeMillis - (now - lastModified)
    }

    fun writeAtomically(
        target: File,
        content: String,
        maxBytes: Long = MAX_ATOMIC_EXPORT_BYTES,
        writeTemp: (File, ByteArray) -> Unit = { file, value ->
            FileOutputStream(file).use { output ->
                output.write(value)
                output.fd.sync()
            }
        }
    ) {
        require(maxBytes >= 0L) { "Export byte limit must be nonnegative" }
        if (utf8ByteCountWithinLimit(content, maxBytes) == null) {
            throw IOException("Export exceeds maximum byte size")
        }
        val encodedContent = content.toByteArray(StandardCharsets.UTF_8)
        if (encodedContent.size.toLong() > maxBytes) throw IOException("Export exceeds maximum byte size")
        val parent = target.parentFile ?: throw IOException("Export target has no parent directory")
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Unable to create export directory")
        }
        val temporary = File.createTempFile(".${target.name}.", ".tmp", parent)
        try {
            writeTemp(temporary, encodedContent)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (failure: Throwable) {
            if (temporary.exists() && !temporary.delete()) {
                failure.addSuppressed(IOException("Unable to delete partial export temporary file"))
            }
            throw failure
        }
    }

    internal fun utf8ByteCountWithinLimit(content: String, maxBytes: Long): Long? {
        if (maxBytes < 0L || content.length.toLong() > maxBytes) return null
        var byteCount = 0L
        var index = 0
        while (index < content.length) {
            val character = content[index]
            val characterBytes = when {
                character.code <= 0x7F -> 1
                character.code <= 0x7FF -> 2
                Character.isHighSurrogate(character) && index + 1 < content.length &&
                    Character.isLowSurrogate(content[index + 1]) -> {
                    index++
                    4
                }
                Character.isSurrogate(character) -> 1
                else -> 3
            }
            byteCount += characterBytes
            if (byteCount > maxBytes) return null
            index++
        }
        return byteCount
    }

    fun deletePartialFiles(directory: File?, temporaryPrefix: String): Int =
        directory?.listFiles()
            ?.filter { it.isFile && it.name.startsWith(temporaryPrefix) && it.name.endsWith(".tmp") }
            ?.count { it.delete() }
            ?: 0

    fun scheduleDelete(context: android.content.Context, file: File, maxAgeMillis: Long) {
        val handler = Handler(Looper.getMainLooper())
        lateinit var callback: Runnable
        callback = Runnable {
            if (!file.exists()) return@Runnable
            val now = System.currentTimeMillis()
            val remaining = remainingMillis(file.lastModified(), now, maxAgeMillis)
            if (remaining == null) file.delete()
            else handler.postDelayed(callback, remaining.coerceAtLeast(1_000L))
        }
        handler.postDelayed(callback, maxAgeMillis)
        runCatching { DiagnosticExportCleanupWorker.schedule(context.applicationContext, file, maxAgeMillis) }
    }
}
