package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import com.jaysato.xiaomihealthbridge.engine.TimedAccelerationObservation

internal class WearAccelerationCapture(context: Context, private val handler: Handler) : SensorEventListener {
    data class Snapshot(
        val status: String,
        val sameSession: Boolean,
        val observations: List<TimedAccelerationObservation>
    )

    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager?.getSensorList(Sensor.TYPE_ACCELEROMETER)
        ?.firstOrNull { !it.isWakeUpSensor }
        ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val observations = ArrayDeque<TimedAccelerationObservation>()
    private var running = false
    private var registrationFailed = false

    fun start(): Boolean {
        stop()
        observations.clear()
        registrationFailed = false
        val selected = sensor ?: return false
        return try {
            running = manager?.registerListener(this, selected, SensorManager.SENSOR_DELAY_UI, handler) == true
            registrationFailed = !running
            running
        } catch (_: SecurityException) {
            registrationFailed = true
            running = false
            false
        } catch (_: RuntimeException) {
            registrationFailed = true
            running = false
            false
        }
    }

    fun stop(): Snapshot {
        running = false
        runCatching { manager?.unregisterListener(this) }
        return Snapshot(
            status = when {
                observations.isNotEmpty() -> "captured"
                registrationFailed -> "registration_failed"
                sensor == null -> "sensor_not_exposed"
                else -> "no_events"
            },
            sameSession = observations.isNotEmpty(),
            observations = observations.toList()
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        if (event.timestamp <= 0L || !x.isFinite() || !y.isFinite() || !z.isFinite()) return
        observations.addLast(TimedAccelerationObservation(event.timestamp, x, y, z))
        while (observations.size > MAX_OBSERVATIONS) observations.removeFirst()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        const val MAX_OBSERVATIONS = 512
        const val MAX_PAIR_SKEW_NANOS = 50_000_000L
    }
}
