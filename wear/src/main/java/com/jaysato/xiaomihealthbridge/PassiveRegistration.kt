package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig

object PassiveRegistration {
    private val registrationFlight = PassiveRegistrationFlight()

    fun register(context: Context, complete: (Result<Int>) -> Unit) {
        if (!PassiveMonitoringSettings.isEnabled(context)) {
            complete(Result.failure(IllegalStateException("背景同步尚未由使用者開啟")))
            return
        }
        if (!registrationFlight.enqueue(complete)) return
        val clientAndCapabilities = runCatching {
            val client = HealthServices.getClient(context).passiveMonitoringClient
            client to client.getCapabilitiesAsync()
        }.getOrElse {
            saveStatus(context, false, it.message ?: it.javaClass.simpleName)
            registrationFlight.finish(Result.failure(it))
            return
        }
        val client = clientAndCapabilities.first
        val capabilities = clientAndCapabilities.second
        runCatching {
            capabilities.addListener({
                val requestedResult = runCatching {
                    val supported = capabilities.get().supportedDataTypesPassiveMonitoring
                    val hasActivityPermission = context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                    val heartRatePermission = WearMeasurementPermissionPolicy.heartRatePermission(android.os.Build.VERSION.SDK_INT)
                    val backgroundPermission = WearMeasurementPermissionPolicy.backgroundHeartRatePermission(android.os.Build.VERSION.SDK_INT)
                    val hasHeartRatePermissions = context.checkSelfPermission(heartRatePermission) == PackageManager.PERMISSION_GRANTED &&
                        context.checkSelfPermission(backgroundPermission) == PackageManager.PERMISSION_GRANTED
                    buildSet {
                        if (hasActivityPermission) {
                            addAll(setOf(DataType.STEPS, DataType.DISTANCE, DataType.CALORIES).filter { it in supported })
                        }
                        if (hasHeartRatePermissions && WearPowerScheduler.currentState(context).mode != WearPowerMode.CRITICAL &&
                            DataType.HEART_RATE_BPM in supported
                        ) {
                            add(DataType.HEART_RATE_BPM)
                        }
                    }
                }
                requestedResult.onSuccess { requested ->
                    if (requested.isEmpty()) {
                        val error = IllegalStateException("缺少可用資料型別或對應權限；背景同步仍保持開啟，可授權後重試")
                        saveStatus(context, false, error.message.orEmpty())
                        registrationFlight.finish(Result.failure(error))
                    } else {
                        val registrationResult = runCatching {
                            client.setPassiveListenerServiceAsync(
                                PassiveHealthService::class.java,
                                PassiveListenerConfig.builder().setDataTypes(requested).build()
                            )
                        }
                        registrationResult.onSuccess { registration ->
                            runCatching {
                                registration.addListener({
                                    val result = runCatching { registration.get(); requested.size }
                                    result.onSuccess { saveStatus(context, true, "已註冊 $it 種被動資料") }
                                        .onFailure { saveStatus(context, false, it.message ?: it.javaClass.simpleName) }
                                    registrationFlight.finish(result)
                                }, context.mainExecutor)
                            }.onFailure {
                                saveStatus(context, false, it.message ?: it.javaClass.simpleName)
                                registrationFlight.finish(Result.failure(it))
                            }
                        }.onFailure {
                            saveStatus(context, false, it.message ?: it.javaClass.simpleName)
                            registrationFlight.finish(Result.failure(it))
                        }
                    }
                }.onFailure {
                    saveStatus(context, false, it.message ?: it.javaClass.simpleName)
                    registrationFlight.finish(Result.failure(it))
                }
            }, context.mainExecutor)
        }.onFailure {
            saveStatus(context, false, it.message ?: it.javaClass.simpleName)
            registrationFlight.finish(Result.failure(it))
        }
    }

    fun unregister(context: Context, complete: (Result<Unit>) -> Unit = {}) {
        PassiveMonitoringSettings.setEnabled(context, false)
        clearRegistration(context, complete)
    }

    fun suspendForPower(context: Context, complete: (Result<Unit>) -> Unit = {}) {
        clearRegistration(context, complete, "極省模式：被動監測已暫停")
    }

    private fun clearRegistration(
        context: Context,
        complete: (Result<Unit>) -> Unit,
        successMessage: String = "背景同步已關閉"
    ) {
        val future = runCatching {
            HealthServices.getClient(context).passiveMonitoringClient.clearPassiveListenerServiceAsync()
        }.getOrElse {
            saveStatus(context, false, it.message ?: it.javaClass.simpleName)
            complete(Result.failure(it))
            return
        }
        future.addListener({
            val result = runCatching { future.get(); Unit }
                .onSuccess { saveStatus(context, false, successMessage) }
                .onFailure { saveStatus(context, false, it.message ?: it.javaClass.simpleName) }
            complete(result)
        }, context.mainExecutor)
    }

    fun recordPermissionLost(context: Context) {
        saveStatus(context, false, "背景感測權限已失效；請開啟手錶 App 重試")
    }

    private fun saveStatus(context: Context, success: Boolean, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_REGISTERED, success)
            .putString(KEY_STATUS, message)
            .putLong(KEY_STATUS_TIME, System.currentTimeMillis())
            .apply()
    }

    const val PREFS = "passive_registration"
    const val KEY_ENABLED = PassiveMonitoringSettings.KEY_ENABLED
    const val KEY_REGISTERED = "registered"
    const val KEY_STATUS = "status"
    const val KEY_STATUS_TIME = "status_time"
}

internal class PassiveRegistrationFlight {
    private val lock = Any()
    private var inFlight = false
    private val callbacks = mutableListOf<(Result<Int>) -> Unit>()

    fun enqueue(callback: (Result<Int>) -> Unit): Boolean = synchronized(lock) {
        callbacks += callback
        if (inFlight) false else {
            inFlight = true
            true
        }
    }

    fun finish(result: Result<Int>) {
        val pendingCallbacks = synchronized(lock) {
            inFlight = false
            callbacks.toList().also { callbacks.clear() }
        }
        pendingCallbacks.forEach { callback -> runCatching { callback(result) } }
    }
}
