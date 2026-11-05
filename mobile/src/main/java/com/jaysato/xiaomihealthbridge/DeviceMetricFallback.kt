package com.jaysato.xiaomihealthbridge

import android.content.SharedPreferences
import kotlin.math.pow

object DeviceMetricFallback {
    const val VERIFIED_CELSIUS_UNIT_STATUS = "celsius_verified"

    fun temperatureC(feature: SharedPreferences, now: Long): Double? =
        feature.getFloat("temperature", Float.NaN).takeIf {
            feature.getString("temperature_unit_status", "unverified") == VERIFIED_CELSIUS_UNIT_STATUS &&
            it.isFinite() && HealthAnalysisFreshnessPolicy.isFreshSensor(
                feature.getLong("temperature_timestamp", 0L),
                feature.getString("temperature_timestamp_source", null),
                now,
                24 * 60 * 60 * 1000L
            )
        }?.toDouble()

    fun pressureHpa(raw: SharedPreferences, now: Long): Double? {
        val publishedAt = raw.getLong("pressure_published_at", 0L)
        if (!HealthAnalysisFreshnessPolicy.isFresh(publishedAt, now, 24 * 60 * 60 * 1000L)) return null
        return raw.getString("pressure_latest", null)?.toDoubleOrNull()?.takeIf { it in 800.0..1100.0 }
            ?: pressureSeries(raw).lastOrNull()
    }

    fun pressureFloorChange(raw: SharedPreferences, now: Long): Double? {
        val publishedAt = raw.getLong("pressure_published_at", 0L)
        if (!HealthAnalysisFreshnessPolicy.isFresh(publishedAt, now, 24 * 60 * 60 * 1000L)) return null
        return floorChange(pressureSeries(raw))
    }

    internal fun floorChange(pressureHpa: List<Double>): Double? {
        val valid = pressureHpa.filter { it.isFinite() && it in 800.0..1100.0 }
        if (valid.size < 10) return null
        val window = (valid.size / 5).coerceIn(5, 30)
        val start = median(valid.take(window))
        val end = median(valid.takeLast(window))
        val startAltitude = 44330.0 * (1.0 - (start / 1013.25).pow(0.1903))
        val endAltitude = 44330.0 * (1.0 - (end / 1013.25).pow(0.1903))
        return ((endAltitude - startAltitude) / 3.0).takeIf { it.isFinite() && it in -100.0..100.0 }
    }

    private fun pressureSeries(raw: SharedPreferences): List<Double> =
        raw.getString("pressure_waveform", "").orEmpty().split(',')
            .asSequence().mapNotNull(String::toDoubleOrNull).filter { it in 800.0..1100.0 }.take(2048).toList()

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }
}
