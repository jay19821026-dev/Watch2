package com.jaysato.xiaomihealthbridge

import android.content.Context

object WearCalibrationStore {
    private const val PREFS = "wear_sensor_calibration"
    private const val KEY_PRESSURE_OFFSET = "pressure_offset_hpa"
    private const val KEY_LIGHT_SCALE = "light_scale"

    fun apply(context: Context, kind: RawVitalsProbe.Kind, raw: Double): Double = when (kind) {
        RawVitalsProbe.Kind.PRESSURE -> raw + context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_PRESSURE_OFFSET, 0f)
        RawVitalsProbe.Kind.LIGHT -> raw * context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_LIGHT_SCALE, 1f)
        else -> raw
    }

    fun save(context: Context, kind: RawVitalsProbe.Kind, raw: Double, reference: Double): String? {
        if (!raw.isFinite() || !reference.isFinite()) return null
        return when (kind) {
            RawVitalsProbe.Kind.BODY_TEMPERATURE, RawVitalsProbe.Kind.THERMOMETER -> null
            RawVitalsProbe.Kind.PRESSURE -> {
                if (raw !in 300.0..1100.0 || reference !in 300.0..1100.0) return null
                val offset = (reference - raw).coerceIn(-50.0, 50.0)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putFloat(KEY_PRESSURE_OFFSET, offset.toFloat()).apply()
                "氣壓 offset ${"%+.2f".format(offset)} hPa"
            }
            RawVitalsProbe.Kind.LIGHT -> {
                if (raw <= 0.0 || reference < 0.0) return null
                val scale = (reference / raw).coerceIn(0.1, 10.0)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putFloat(KEY_LIGHT_SCALE, scale.toFloat()).apply()
                "環境光 scale ${"%.4f".format(scale)}"
            }
            else -> null
        }
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(AdvancedSensorController.PREFS, Context.MODE_PRIVATE).edit()
            .remove(AdvancedSensorController.KEY_TEMPERATURE_OFFSET)
            .remove(AdvancedSensorController.KEY_CALIBRATED)
            .apply()
    }
}
