package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HeartRateWriter(private val context: Context) {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    enum class WriteDisposition {
        QUEUED_LOCALLY,
        WRITTEN_TO_HEALTH_CONNECT
    }

    suspend fun write(beatsPerMinute: Long, epochMillis: Long): Result<WriteDisposition> = mutex.withLock {
        runCatching {
            require(beatsPerMinute in 1..300) { "Invalid heart rate: $beatsPerMinute" }
            val now = System.currentTimeMillis()
            val previousQueue = HeartRateQueuePolicy.compact(loadQueue()).toMutableMap()
            if (HeartRateQueuePolicy.shouldFlushBeforeAppend(previousQueue.size, MAX_QUEUE_SIZE)) {
                flushQueuedSamples(previousQueue, now)
            }
            val queued = HeartRateQueuePolicy.compact(previousQueue + (epochMillis to beatsPerMinute)).toMutableMap()
            check(HeartRateQueuePolicy.fitsCapacity(queued.size, MAX_QUEUE_SIZE)) {
                "Heart-rate queue capacity reached"
            }
            check(saveQueue(queued)) { "Unable to durably save the heart-rate queue" }
            val lastFlush = prefs().getLong(KEY_LAST_FLUSH, 0L)
            if (lastFlush > 0 && now - lastFlush < FLUSH_INTERVAL_MILLIS && queued.size < MAX_BATCH_SIZE) {
                return@runCatching WriteDisposition.QUEUED_LOCALLY
            }
            flushQueuedSamples(queued, now)
            WriteDisposition.WRITTEN_TO_HEALTH_CONNECT
        }
    }

    private suspend fun flushQueuedSamples(queue: MutableMap<Long, Long>, now: Long) {
        if (queue.isEmpty()) return
        val permission = HealthPermission.getWritePermission(HeartRateRecord::class)
        check(permission in client.permissionController.getGrantedPermissions()) {
            "Health Connect heart-rate write permission is not granted"
        }
        val batch = queue.entries.sortedBy { it.key }.take(MAX_BATCH_SIZE)
        val records = HeartRateRecordFactory.create(
            batch.associate { it.key to it.value },
            Device(
                type = Device.TYPE_WATCH,
                manufacturer = "Xiaomi",
                model = "Watch 2 Pro"
            )
        )
        client.insertRecords(records)
        batch.forEach { queue.remove(it.key) }
        check(saveQueue(queue)) { "Unable to durably acknowledge flushed heart-rate samples" }
        check(prefs().edit().putLong(KEY_LAST_FLUSH, now).commit()) {
            "Unable to durably update the heart-rate flush time"
        }
        RestingHeartRateEstimator(context).update(batch.last().key).onFailure { error ->
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ESTIMATOR_ERROR, error.message ?: error.javaClass.simpleName)
                .apply()
        }.onSuccess {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_ESTIMATOR_ERROR)
                .apply()
        }
    }

    fun pendingSampleCount(): Int = loadQueue().size

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadQueue(): Map<Long, Long> = prefs().getString(KEY_QUEUE, null).orEmpty()
        .split(';').mapNotNull { encoded ->
            val parts = encoded.split(':')
            val time = parts.getOrNull(0)?.toLongOrNull()
            val bpm = parts.getOrNull(1)?.toLongOrNull()
            if (time != null && bpm != null) time to bpm else null
        }.toMap()

    private fun saveQueue(queue: Map<Long, Long>): Boolean {
        val compacted = HeartRateQueuePolicy.compact(queue).entries.sortedBy { it.key }
        if (!HeartRateQueuePolicy.fitsCapacity(compacted.size, MAX_QUEUE_SIZE)) return false
        return prefs().edit()
            .putString(KEY_QUEUE, compacted.joinToString(";") { "${it.key}:${it.value}" })
            .commit()
    }

    companion object {
        private val mutex = Mutex()
        private const val PREFS = "heart_rate_batch"
        private const val KEY_QUEUE = "pending"
        private const val KEY_LAST_FLUSH = "last_flush"
        private const val KEY_ESTIMATOR_ERROR = "estimator_error"
        private const val FLUSH_INTERVAL_MILLIS = 15 * 60 * 1000L
        private const val MAX_BATCH_SIZE = 200
        private const val MAX_QUEUE_SIZE = 2000
    }
}
