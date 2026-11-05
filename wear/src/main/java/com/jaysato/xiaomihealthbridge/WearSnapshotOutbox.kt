package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.wearable.DataMap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class PendingWearSnapshot(
    val id: String,
    val path: String,
    val urgent: Boolean,
    val payload: ByteArray
)

internal data class WearSnapshotDeliveryOrder(
    val timestampMillis: Long,
    val producerSequence: Long,
    val id: String
)

internal object WearSnapshotOutboxPolicy {
    const val MAX_ENTRIES = 64
    const val MAX_TOTAL_BYTES = 8 * 1024 * 1024
    const val MAX_PAYLOAD_BYTES = 512 * 1024
    const val MAX_AUTOMATIC_RETRIES = 7

    fun shouldRetryAutomatically(runAttemptCount: Int): Boolean =
        runAttemptCount in 0 until MAX_AUTOMATIC_RETRIES

    fun isQuarantineFileName(fileName: String): Boolean =
        fileName.substringAfterLast('.', missingDelimiterValue = "") == "quarantine"

    fun canStore(entryCount: Int, storedBytes: Long, encryptedEntryBytes: Int): Boolean =
        encryptedEntryBytes in 1..(MAX_PAYLOAD_BYTES + ENVELOPE_OVERHEAD_BYTES) &&
            entryCount in 0 until MAX_ENTRIES &&
            storedBytes in 0L..MAX_TOTAL_BYTES.toLong() &&
            encryptedEntryBytes.toLong() <= MAX_TOTAL_BYTES.toLong() - storedBytes

    fun compareDeliveryOrder(first: WearSnapshotDeliveryOrder, second: WearSnapshotDeliveryOrder): Int =
        compareValuesBy(first, second, WearSnapshotDeliveryOrder::timestampMillis,
            WearSnapshotDeliveryOrder::producerSequence, WearSnapshotDeliveryOrder::id)

    private const val ENVELOPE_OVERHEAD_BYTES = 4096
    const val MAX_ENCRYPTED_ENTRY_BYTES = MAX_PAYLOAD_BYTES + ENVELOPE_OVERHEAD_BYTES
}

internal object WearSnapshotOutbox {
    private const val TAG = "WearSnapshotOutbox"
    private const val KEY_ALIAS = "wear_raw_snapshot_outbox_v1"
    const val PREFS = "wear_raw_snapshot_outbox_state"
    private const val KEY_PENDING_COUNT = "pending_count"
    private const val KEY_QUARANTINED_COUNT = "quarantined_count"
    private const val KEY_LAST_STATE = "last_state"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val MAGIC = 0x57534F31
    private const val VERSION = 1
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val UNIQUE_WORK = "wear_raw_snapshot_outbox_v1"
    const val MAX_RECORDS_PER_RUN = 12
    private val lock = Any()

    enum class StoreResult { STORED, TOO_LARGE, FULL, FAILED }

    fun enqueue(context: Context, path: String, dataMap: DataMap, urgent: Boolean): Pair<StoreResult, String?> {
        val payload = try {
            dataMap.toByteArray()
        } catch (error: Exception) {
            recordState(context, "serialize_failed")
            Log.e(TAG, "serialize_failed type=${error.javaClass.simpleName}")
            return StoreResult.FAILED to null
        }
        if (!path.startsWith("/raw-sensor/") || payload.size > WearSnapshotOutboxPolicy.MAX_PAYLOAD_BYTES) {
            recordState(context, "payload_rejected")
            return StoreResult.TOO_LARGE to null
        }
        val id = UUID.randomUUID().toString()
        val envelope = encode(path, urgent, payload) ?: run {
            recordState(context, "encode_failed")
            return StoreResult.FAILED to null
        }
        synchronized(lock) {
            recoverTemporaryFilesLocked(context)
            val files = queueFiles(context)
            val allFiles = allStoredFiles(context)
            val storedBytes = allFiles.sumOf { it.length() }
            val encrypted = try {
                encrypt(id, envelope)
            } catch (error: Exception) {
                recordStateLocked(context, "encryption_failed", files.size, quarantineFiles(context).size)
                Log.e(TAG, "encryption_failed type=${error.javaClass.simpleName}")
                return StoreResult.FAILED to null
            }
            if (!WearSnapshotOutboxPolicy.canStore(allFiles.size, storedBytes, encrypted.size)) {
                recordStateLocked(context, "queue_full", files.size, quarantineFiles(context).size)
                return StoreResult.FULL to null
            }
            val directory = queueDirectory(context)
            if (!directory.exists() && !directory.mkdirs()) {
                recordStateLocked(context, "storage_unavailable", files.size, quarantineFiles(context).size)
                return StoreResult.FAILED to null
            }
            val temporary = File(directory, "$id.tmp")
            val target = File(directory, "$id.enc")
            try {
                FileOutputStream(temporary).use { stream ->
                    stream.write(encrypted)
                    stream.fd.sync()
                }
                if (!temporary.renameTo(target)) throw IllegalStateException("atomic rename failed")
            } catch (error: Exception) {
                temporary.delete()
                recordStateLocked(context, "storage_write_failed", files.size, quarantineFiles(context).size)
                Log.e(TAG, "storage_write_failed type=${error.javaClass.simpleName}")
                return StoreResult.FAILED to null
            }
            recordStateLocked(context, "queued", files.size + 1, quarantineFiles(context).size)
        }
        return StoreResult.STORED to id
    }

    fun pending(context: Context): List<PendingWearSnapshot> = synchronized(lock) {
        recoverTemporaryFilesLocked(context)
        queueFiles(context).mapNotNull { file ->
            try {
                decode(file.nameWithoutExtension, decrypt(file.nameWithoutExtension,
                    BoundedSnapshotFile.read(file, WearSnapshotOutboxPolicy.MAX_ENCRYPTED_ENTRY_BYTES)))
            } catch (error: Exception) {
                quarantine(context, file)
                Log.e(TAG, "record_quarantined type=${error.javaClass.simpleName}")
                null
            }
        }.sortedWith { first, second ->
            WearSnapshotOutboxPolicy.compareDeliveryOrder(first.deliveryOrder(), second.deliveryOrder())
        }
    }

    fun remove(context: Context, id: String): Boolean = synchronized(lock) {
        val file = File(queueDirectory(context), "$id.enc")
        val removed = !file.exists() || file.delete()
        recordStateLocked(
            context,
            if (removed) "delivered_to_data_layer" else "queue_remove_failed",
            queueFiles(context).size,
            quarantineFiles(context).size
        )
        removed
    }

    fun quarantinedCount(context: Context): Int = synchronized(lock) { quarantineFiles(context).size }

    fun clearQuarantine(context: Context): Int = synchronized(lock) {
        val files = quarantineFiles(context)
        val removed = files.count { !it.exists() || it.delete() }
        val remaining = quarantineFiles(context).size
        recordStateLocked(
            context,
            if (remaining == 0) "quarantine_cleared" else "quarantine_clear_partial",
            queueFiles(context).size,
            remaining
        )
        removed
    }

    fun finishWorkerIfEmpty(context: Context): Boolean = synchronized(lock) {
        if (queueFiles(context).isNotEmpty()) return false
        val quarantined = quarantineFiles(context).size
        statePrefs(context).edit()
            .putInt(KEY_PENDING_COUNT, 0)
            .putInt(KEY_QUARANTINED_COUNT, quarantined)
            .putString(KEY_LAST_STATE, if (quarantined == 0) "drained" else "drained_with_quarantine")
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .commit()
    }

    fun defer(context: Context, state: String) {
        recordState(context, state)
    }

    fun recoverPending(context: Context): Boolean = synchronized(lock) {
        recoverTemporaryFilesLocked(context)
        if (queueFiles(context).isEmpty()) return false
        scheduleWorkerLocked(context, ExistingWorkPolicy.KEEP)
    }

    fun scheduleContinuation(context: Context): Boolean = synchronized(lock) {
        queueFiles(context).isNotEmpty() &&
            scheduleWorkerLocked(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun statusSummary(context: Context): String {
        val prefs = statePrefs(context)
        val pending = prefs.getInt(KEY_PENDING_COUNT, 0)
        val quarantined = prefs.getInt(KEY_QUARANTINED_COUNT, 0)
        val state = when (prefs.getString(KEY_LAST_STATE, "").orEmpty()) {
            "queued" -> "已加密暫存，等待傳送"
            "worker_schedule_requested" -> "已排入背景重試"
            "temporary_record_recovered" -> "已復原中斷寫入的封包"
            "delivery_retry" -> "傳送失敗，等待重試"
            "retry_paused" -> "自動重試已暫停；待送資料仍保留，下次開啟 App／服務時恢復"
            "delivered_to_data_layer" -> "最近封包已交給 Data Layer"
            "drained" -> "待送佇列清空；Data Layer 已接受"
            "drained_with_quarantine" -> "待送佇列清空，但隔離封包未送出"
            "queue_full" -> "佇列已滿；本次未能持久暫存，直送結果另行記錄"
            "worker_schedule_failed" -> "重試工作排程失敗"
            "record_quarantined" -> "有封包無法解密，已隔離保留"
            "quarantine_cleared" -> "隔離封包已清除；待送封包未修改"
            "quarantine_clear_partial" -> "部分隔離封包無法刪除；待送封包未修改"
            "" -> "尚無待送記錄"
            else -> "暫存狀態需檢查"
        }
        return "待送 $pending · 隔離 $quarantined · $state"
    }

    private fun encode(path: String, urgent: Boolean, payload: ByteArray): ByteArray? = try {
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { data ->
                data.writeInt(MAGIC)
                data.writeInt(VERSION)
                data.writeBoolean(urgent)
                data.writeUTF(path)
                data.writeInt(payload.size)
                data.write(payload)
            }
            bytes.toByteArray()
        }
    } catch (_: Exception) {
        null
    }

    private fun decode(id: String, bytes: ByteArray): PendingWearSnapshot {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) { "unsupported envelope" }
            val urgent = input.readBoolean()
            val path = input.readUTF()
            require(path.startsWith("/raw-sensor/")) { "invalid data path" }
            val length = input.readInt()
            require(length in 1..WearSnapshotOutboxPolicy.MAX_PAYLOAD_BYTES && length <= input.available()) {
                "invalid payload length"
            }
            val payload = ByteArray(length)
            input.readFully(payload)
            require(input.available() == 0) { "trailing envelope bytes" }
            return PendingWearSnapshot(id, path, urgent, payload)
        }
    }

    private fun PendingWearSnapshot.deliveryOrder(): WearSnapshotDeliveryOrder {
        val pathTimestamp = path.substringAfterLast('/').substringBefore('-').toLongOrNull()
            ?: Long.MAX_VALUE
        val sequence = runCatching {
            DataMap.fromByteArray(payload).getLong("producer_sequence", 0L)
        }.getOrDefault(0L)
        return WearSnapshotDeliveryOrder(pathTimestamp, sequence, id)
    }

    private fun encrypt(id: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(cipher.iv.size)
                output.write(cipher.iv)
                output.write(ciphertext)
            }
            bytes.toByteArray()
        }
    }

    private fun decrypt(id: String, encrypted: ByteArray): ByteArray {
        DataInputStream(ByteArrayInputStream(encrypted)).use { input ->
            val ivSize = input.readInt()
            require(ivSize == GCM_IV_BYTES && input.available() > ivSize) { "invalid ciphertext envelope" }
            val iv = ByteArray(ivSize)
            input.readFully(iv)
            val ciphertext = ByteArray(input.available())
            input.readFully(ciphertext)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
            return cipher.doFinal(ciphertext)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private fun quarantine(context: Context, file: File) {
        val quarantineDirectory = File(queueDirectory(context), "quarantine")
        if (!quarantineDirectory.exists()) quarantineDirectory.mkdirs()
        val target = File(quarantineDirectory, file.nameWithoutExtension + ".quarantine")
        if (file.renameTo(target)) {
            recordStateLocked(context, "record_quarantined", queueFiles(context).size, quarantineFiles(context).size)
        }
    }

    private fun recoverTemporaryFilesLocked(context: Context) {
        val directory = queueDirectory(context)
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "tmp" }
            .forEach { file ->
                val id = file.nameWithoutExtension
                try {
                    decode(id, decrypt(id,
                        BoundedSnapshotFile.read(file, WearSnapshotOutboxPolicy.MAX_ENCRYPTED_ENTRY_BYTES)))
                    val target = File(directory, "$id.enc")
                    if (file.renameTo(target)) {
                        recordStateLocked(
                            context,
                            "temporary_record_recovered",
                            queueFiles(context).size,
                            quarantineFiles(context).size
                        )
                    } else {
                        Log.e(TAG, "temporary_record_recovery_failed reason=rename_failed")
                    }
                } catch (error: Exception) {
                    quarantine(context, file)
                    Log.e(TAG, "temporary_record_quarantined type=${error.javaClass.simpleName}")
                }
            }
    }

    private fun queueDirectory(context: Context) = context.getDir("raw-snapshot-outbox", Context.MODE_PRIVATE)
    private fun queueFiles(context: Context) = queueDirectory(context).listFiles()
        .orEmpty().filter { it.isFile && it.extension == "enc" }.sortedBy { it.name }

    private fun quarantineFiles(context: Context) = File(queueDirectory(context), "quarantine")
        .listFiles().orEmpty().filter { it.isFile && WearSnapshotOutboxPolicy.isQuarantineFileName(it.name) }

    private fun allStoredFiles(context: Context) = queueFiles(context) + quarantineFiles(context) +
        queueDirectory(context).listFiles().orEmpty().filter { it.isFile && it.extension == "tmp" }

    private fun statePrefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun recordState(context: Context, state: String) = synchronized(lock) {
        recordStateLocked(context, state, queueFiles(context).size, quarantineFiles(context).size)
    }

    private fun recordStateLocked(context: Context, state: String, pending: Int, quarantined: Int) {
        statePrefs(context).edit()
            .putString(KEY_LAST_STATE, state)
            .putInt(KEY_PENDING_COUNT, pending)
            .putInt(KEY_QUARANTINED_COUNT, quarantined)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .commit()
    }

    private fun scheduleWorkerLocked(context: Context, policy: ExistingWorkPolicy): Boolean {
        return try {
            val request = OneTimeWorkRequestBuilder<WearSnapshotOutboxWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                policy,
                request
            )
            recordStateLocked(context, "worker_schedule_requested", queueFiles(context).size, quarantineFiles(context).size)
            true
        } catch (error: Exception) {
            recordStateLocked(context, "worker_schedule_failed", queueFiles(context).size, quarantineFiles(context).size)
            Log.e(TAG, "worker_schedule_failed type=${error.javaClass.simpleName}")
            false
        }
    }

}
