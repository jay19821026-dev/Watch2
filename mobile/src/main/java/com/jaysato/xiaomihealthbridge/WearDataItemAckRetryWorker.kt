package com.jaysato.xiaomihealthbridge

import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal class WearDataItemAckRetryWorker(context: android.content.Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val rawUri = inputData.getString(KEY_URI) ?: return Result.failure()
        if (!WearDataItemAckRetryPolicy.isSupportedUri(rawUri)) return Result.failure()
        val uri = Uri.parse(rawUri)
        if (uri.scheme != "wear" || uri.authority.isNullOrBlank() || WearDataItemAckTracker.route(uri.path.orEmpty()) == "other") {
            return Result.failure()
        }
        WearDataItemAckTracker.recordAttempt(applicationContext, uri)
        return try {
            withContext(Dispatchers.IO) {
                Tasks.await(
                    Wearable.getDataClient(applicationContext).deleteDataItems(uri),
                    TASK_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
            }
            WearDataItemAckTracker.recordResult(applicationContext, uri, success = true, errorType = "")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            WearDataItemAckTracker.recordResult(
                applicationContext,
                uri,
                success = false,
                errorType = error.javaClass.simpleName
            )
            Log.w(TAG, "data_item_delete_retry_failed route=${WearDataItemAckTracker.route(uri.path.orEmpty())} type=${error.javaClass.simpleName}")
            if (WearDataItemAckRetryPolicy.shouldRetry(runAttemptCount)) {
                Result.retry()
            } else {
                WearDataItemAckTracker.recordRetryExhausted(applicationContext, uri)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_URI = "data_item_uri"
        private const val TAG = "WearDataItemAckRetry"
        private const val TASK_TIMEOUT_SECONDS = 20L
    }
}
