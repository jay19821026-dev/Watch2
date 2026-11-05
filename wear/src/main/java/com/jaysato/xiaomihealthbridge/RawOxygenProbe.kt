package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class RawOxygenProbe(
    context: Context,
    private val onReading: (String) -> Unit
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensors = manager.getSensorList(Sensor.TYPE_ALL).filter {
        !it.isWakeUpSensor && it.stringType in setOf(SENSOR_TYPE, MI_SENSOR_TYPE)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val registeredSensors = mutableListOf<Sensor>()
    private var lastLogMillis = 0L
    private var acquired = false

    fun start(): Int {
        if (sensors.isEmpty() || !IN_USE.compareAndSet(false, true)) return 0
        acquired = true
        sensors.forEach { sensor ->
            val registered = runCatching {
                manager.registerListener(
                    this,
                    sensor,
                    RawVitalsProbe.samplingDelayUs(sensor, RawVitalsProbe.Kind.SPO2),
                    handler
                )
            }.getOrElse { error ->
                Log.w(TAG, "registration failed name=${sensor.name} type=${sensor.stringType}", error)
                false
            }
            if (registered) {
                registeredSensors += sensor
                Log.i(TAG, "registered name=${sensor.name} vendor=${sensor.vendor} type=${sensor.type} stringType=${sensor.stringType}")
            } else {
                Log.w(TAG, "registration rejected name=${sensor.name} type=${sensor.stringType}")
            }
        }
        if (registeredSensors.isEmpty()) {
            acquired = false
            IN_USE.set(false)
        }
        return registeredSensors.size
    }

    fun stop() {
        val sensorsToUnregister = registeredSensors.toList()
        try {
            SensorRegistrationGuard.cleanup(sensorsToUnregister.map { sensor ->
                { manager.unregisterListener(this, sensor) }
            }) { error ->
                Log.w(TAG, "unregister failed; continuing sensor cleanup", error)
            }
        } finally {
            registeredSensors.clear()
            if (acquired) {
                acquired = false
                IN_USE.set(false)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!acquired) return
        val now = System.currentTimeMillis()
        if (now - lastLogMillis >= LOG_INTERVAL_MILLIS) {
            val status = "SpO₂ raw event received; sample_count=${event.values.size}; accuracy=${event.accuracy}"
            Log.i(TAG, status)
            onReading(status)
            lastLogMillis = now
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.i(TAG, "accuracy name=${sensor?.name} value=$accuracy")
    }

    companion object {
        const val TAG = "RawOxygenProbe"
        const val SENSOR_TYPE = "com.google.wear.sensor.spo2"
        const val MI_SENSOR_TYPE = "com.google.wear.sensor.mi.spo2"
        const val LOG_INTERVAL_MILLIS = 1_000L
        const val MAX_CAPTURE_MILLIS = 60_000L
        private val IN_USE = AtomicBoolean(false)
    }
}
