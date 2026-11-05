package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class WearSnapshotOutboxWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        var delivered = 0
        while (delivered < WearSnapshotOutbox.MAX_RECORDS_PER_RUN) {
            val entries = WearSnapshotOutbox.pending(applicationContext)
            if (entries.isEmpty()) {
                if (WearSnapshotOutbox.finishWorkerIfEmpty(applicationContext)) return Result.success()
                return if (WearSnapshotOutbox.scheduleContinuation(applicationContext)) Result.success() else retryOrPause()
            }
            for (entry in entries) {
                if (delivered >= WearSnapshotOutbox.MAX_RECORDS_PER_RUN) {
                    return if (WearSnapshotOutbox.scheduleContinuation(applicationContext)) Result.success() else retryOrPause()
                }
                try {
                    val request = PutDataMapRequest.create(entry.path).apply {
                        dataMap.putAll(DataMap.fromByteArray(entry.payload))
                    }.asPutDataRequest()
                    if (entry.urgent) request.setUrgent()
                    withContext(Dispatchers.IO) {
                        Tasks.await(
                            Wearable.getDataClient(applicationContext).putDataItem(request),
                            TASK_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                        )
                    }
                    if (!WearSnapshotOutbox.remove(applicationContext, entry.id)) {
                        Log.e(TAG, "delivered_item_retained reason=queue_remove_failed")
                        return retryOrPause()
                    }
                    delivered++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    WearSnapshotOutbox.defer(applicationContext, "delivery_retry")
                    Log.w(TAG, "delivery_retry type=${error.javaClass.simpleName}")
                    return retryOrPause()
                }
            }
        }
        if (WearSnapshotOutbox.pending(applicationContext).isEmpty() &&
            WearSnapshotOutbox.finishWorkerIfEmpty(applicationContext)
        ) return Result.success()
        return if (WearSnapshotOutbox.scheduleContinuation(applicationContext)) Result.success() else retryOrPause()
    }

    private fun retryOrPause(): Result {
        if (WearSnapshotOutboxPolicy.shouldRetryAutomatically(runAttemptCount)) return Result.retry()
        WearSnapshotOutbox.defer(applicationContext, "retry_paused")
        Log.w(TAG, "automatic_retry_paused attempts=${runAttemptCount + 1}; pending entries retained")
        return Result.failure()
    }

    private companion object {
        const val TAG = "WearSnapshotOutbox"
        const val TASK_TIMEOUT_SECONDS = 20L
    }
}
