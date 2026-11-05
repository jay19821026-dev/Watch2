package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs

class AdvancedSensorController(
    private val context: Context,
    private val onUpdate: (Snapshot) -> Unit,
    private val onTemperatureReady: (Double, Long, String) -> Unit,
    private val onFallRisk: (Double) -> Unit = {},
    private val onAmbientMode: (String) -> Unit = {}
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val pressure = manager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetic = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val temperature = manager?.getSensorList(Sensor.TYPE_ALL).orEmpty().firstOrNull {
        val id = "${it.name} ${it.stringType}".lowercase()
        isBodyTemperatureSensor(id)
    }
    private val ambientLight = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val temperatureSamples = ArrayDeque<Double>()
    private var altitude = Double.NaN
    private var baselineAltitude = Double.NaN
    private var gained = 0.0
    private var heading = Double.NaN
    private var lastSyncedElevation = 0.0
    private var lastFallRiskMillis = 0L
    private var previousAcceleration = SensorManager.GRAVITY_EARTH.toDouble()
    private var lastTemperatureSentMillis = 0L

    val temperatureSensorName: String? get() = temperature?.name

    fun start() {
        listOfNotNull(pressure, accelerometer, magnetic, temperature, ambientLight).forEach { selected ->
            SensorRegistrationGuard.run(
                register = { manager?.registerListener(this, selected, SensorManager.SENSOR_DELAY_NORMAL) == true },
                onPermissionDenied = { error ->
                    Log.w(TAG, "sensor registration denied type=${selected.stringType}", error)
                    false
                },
                onRuntimeFailure = { error ->
                    Log.w(TAG, "sensor registration failed type=${selected.stringType}", error)
                    false
                }
            )
        }
    }

    fun stop() {
        try {
            manager?.unregisterListener(this)
        } catch (error: RuntimeException) {
            Log.w(TAG, "sensor cleanup failed", error)
        }
    }

    fun resetElevation() {
        baselineAltitude = altitude
        gained = 0.0
        lastSyncedElevation = 0.0
    }

    fun elevationGain() = gained

    fun syncElevationIfNeeded(force: Boolean = false) {
        val delta = gained - lastSyncedElevation
        if ((force && delta > 0.5) || delta >= 3.0) {
            val now = System.currentTimeMillis()
            sendMetric("elevation", delta, now - 60_000, now)
            sendMetric("floors", delta / 3.0, now - 60_000, now)
            lastSyncedElevation = gained
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                val next = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]).toDouble()
                if (altitude.isFinite() && next > altitude && next - altitude < 4.0) gained += next - altitude
                altitude = next
                if (!baselineAltitude.isFinite()) baselineAltitude = next
                syncElevationIfNeeded()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                event.values.copyInto(gravity, endIndex = minOf(3, event.values.size))
                val magnitude = SensorAlgorithms.accelerationMagnitude(event.values)
                val now = System.currentTimeMillis()
                if (SensorAlgorithms.fallRisk(magnitude, previousAcceleration, now, lastFallRiskMillis)) {
                    lastFallRiskMillis = now
                    onFallRisk(magnitude)
                }
                previousAcceleration = magnitude
            }
            Sensor.TYPE_LIGHT -> {
                val enabled = context.getSharedPreferences("device_preferences", Context.MODE_PRIVATE)
                    .getBoolean("gesture_night_mode", true)
                onAmbientMode(SensorAlgorithms.ambientPowerMode(event.values.firstOrNull() ?: 0f, enabled))
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                event.values.copyInto(geomagnetic, endIndex = minOf(3, event.values.size))
                val rotation = FloatArray(9)
                val orientation = FloatArray(3)
                if (SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) {
                    SensorManager.getOrientation(rotation, orientation)
                    heading = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_HEADING, heading.toFloat()).apply()
                }
            }
            else -> if (event.sensor == temperature) processTemperature(event.values, event.timestamp)
        }
        onUpdate(Snapshot(altitude, gained, gained / 3.0, heading, temperatureSamples.lastOrNull()))
    }

    private fun processTemperature(values: FloatArray, eventTimestampNanos: Long) {
        val candidate = values.map { it.toDouble() }.firstOrNull { it.isFinite() } ?: return
        val callbackElapsedNanos = SystemClock.elapsedRealtimeNanos()
        val callbackWallMillis = System.currentTimeMillis()
        val eventTime = SensorEventEpochTimeResolver.resolve(
            eventTimestampNanos = eventTimestampNanos,
            capturedElapsedRealtimeNanos = callbackElapsedNanos,
            epochOffsetMillis = callbackWallMillis - callbackElapsedNanos / 1_000_000L,
            callbackWallClockMillis = callbackWallMillis
        )
        if (eventTime.epochMillis <= 0L || eventTime.source != "android_sensor_event_elapsed_realtime") return
        temperatureSamples += candidate
        while (temperatureSamples.size > 12) temperatureSamples.removeFirst()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = temperatureSamples.average()
        prefs.edit()
            .putFloat(KEY_RAW_TEMPERATURE, raw.toFloat())
            .putLong(KEY_RAW_TEMPERATURE_TIME, eventTime.epochMillis)
            .putInt(KEY_RAW_TEMPERATURE_SAMPLE_COUNT, temperatureSamples.size)
            .putString(KEY_RAW_TEMPERATURE_STATUS, if (temperatureSamples.size >= 5) "has_data" else "partial_data")
            .apply()
        val now = eventTime.epochMillis
        if (lastTemperatureSentMillis == 0L || now - lastTemperatureSentMillis >= 5_000L) {
            lastTemperatureSentMillis = now
            onTemperatureReady(raw, now, eventTime.source)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun sendMetric(metric: String, value: Double, start: Long, end: Long) {
        WearMetricSender.send(context, metric, value, start, end)
    }

    data class Snapshot(
        val altitude: Double,
        val elevationGain: Double,
        val floors: Double,
        val heading: Double,
        val rawTemperature: Double?
    )

    companion object {
        private const val TAG = "AdvancedSensorController"
        const val PREFS = "advanced_sensors"
        const val KEY_HEADING = "heading"
        const val KEY_RAW_TEMPERATURE = "raw_temperature"
        const val KEY_RAW_TEMPERATURE_TIME = "raw_temperature_time"
        const val KEY_RAW_TEMPERATURE_SAMPLE_COUNT = "raw_temperature_sample_count"
        const val KEY_RAW_TEMPERATURE_STATUS = "raw_temperature_status"
        const val KEY_TEMPERATURE_OFFSET = "temperature_offset"
        const val KEY_CALIBRATED = "temperature_calibrated"

        private fun isBodyTemperatureSensor(id: String): Boolean {
            val excluded = listOf("board", "ambient", "battery", "cpu", "soc", "alarm")
            if (excluded.any(id::contains)) return false
            return id.contains("bodytemperature") ||
                id.contains("body temperature") ||
                id.contains("skin temperature") ||
                id.contains("thermometer") ||
                id.contains("ct7117 body")
        }
    }
}
