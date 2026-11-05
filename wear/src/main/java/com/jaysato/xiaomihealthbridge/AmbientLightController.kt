package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class AmbientLightController(
    context: Context,
    private val onMode: (String) -> Unit
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var lastMode = ""

    fun start() {
        sensor?.let { selected ->
            SensorRegistrationGuard.run(
                register = { manager?.registerListener(this, selected, SensorManager.SENSOR_DELAY_NORMAL) == true },
                onPermissionDenied = { error ->
                    Log.w(TAG, "ambient light registration denied", error)
                    false
                },
                onRuntimeFailure = { error ->
                    Log.w(TAG, "ambient light registration failed", error)
                    false
                }
            )
        }
    }

    fun stop() {
        try {
            manager?.unregisterListener(this)
        } catch (error: RuntimeException) {
            Log.w(TAG, "ambient light cleanup failed", error)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val lux = event.values.firstOrNull() ?: return
        val enabled = event.sensor != null
        val mode = SensorAlgorithms.ambientPowerMode(lux, enabled)
        if (mode != lastMode) {
            lastMode = mode
            onMode(mode)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val TAG = "AmbientLightController"
    }
}
