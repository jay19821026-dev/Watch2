package com.jaysato.xiaomihealthbridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class PassiveRegistrationWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = suspendCancellableCoroutine { continuation ->
        val completion: (kotlin.Result<*>) -> Unit = { registration ->
            if (continuation.isActive) {
                val error = registration.exceptionOrNull()
                val retry = error != null && error !is SecurityException && error !is IllegalStateException
                continuation.resume(if (error == null) Result.success() else if (retry) Result.retry() else Result.failure())
            }
        }
        if (PassiveMonitoringSettings.isEnabled(applicationContext)) {
            PassiveRegistration.register(applicationContext) { completion(it) }
        } else {
            PassiveRegistration.unregister(applicationContext) { completion(it) }
        }
    }
}
