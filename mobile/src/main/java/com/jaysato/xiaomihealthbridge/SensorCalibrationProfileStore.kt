package com.jaysato.xiaomihealthbridge

import android.content.Context

class SensorCalibrationProfileStore(context: Context) {
    data class Profile(
        val kind: String,
        val targetUnit: String,
        val slope: Double,
        val offset: Double,
        val fitRmse: Double,
        val validationRmse: Double,
        val points: Int,
        val source: String,
        val createdAtMillis: Long,
        val validationReport: String = "",
        val algorithmVersion: String = "linear-loo-v1"
    ) {
        fun apply(raw: Double): Double? = (raw * slope + offset).takeIf(Double::isFinite)
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(kind: String): Profile? {
        val prefix = "$kind."
        if (!prefs.getBoolean(prefix + "present", false)) return null
        return Profile(
            kind = kind,
            targetUnit = prefs.getString(prefix + "unit", null) ?: return null,
            slope = prefs.getString(prefix + "slope", null)?.toDoubleOrNull() ?: return null,
            offset = prefs.getString(prefix + "offset", null)?.toDoubleOrNull() ?: return null,
            fitRmse = prefs.getString(prefix + "fit_rmse", null)?.toDoubleOrNull() ?: return null,
            validationRmse = prefs.getString(prefix + "validation_rmse", null)?.toDoubleOrNull() ?: return null,
            points = prefs.getInt(prefix + "points", 0),
            source = prefs.getString(prefix + "source", null).orEmpty(),
            createdAtMillis = prefs.getLong(prefix + "created_at", 0L),
            validationReport = prefs.getString(prefix + "validation_report", "").orEmpty(),
            algorithmVersion = prefs.getString(prefix + "algorithm_version", "linear-loo-v1").orEmpty()
        ).takeIf {
            it.kind in SUPPORTED_KINDS && it.targetUnit.isNotBlank() && it.slope.isFinite() && it.slope != 0.0 &&
                it.offset.isFinite() && it.fitRmse.isFinite() && it.fitRmse >= 0.0 &&
                it.validationRmse.isFinite() && it.validationRmse >= 0.0 && it.points >= MIN_POINTS &&
                it.source.isNotBlank() && it.createdAtMillis > 0L
        }
    }

    fun save(kind: String, unit: String, result: ExternalReferenceCalibration.Result, source: String): Boolean {
        val normalizedSource = source.trim()
        val validationRmse = result.validationRmse
        if (kind !in SUPPORTED_KINDS || unit.isBlank() || normalizedSource.isBlank() || !result.usable ||
            result.points < MIN_POINTS || validationRmse == null || !validationRmse.isFinite()) return false
        val prefix = "$kind."
        return prefs.edit()
            .putBoolean(prefix + "present", true)
            .putString(prefix + "unit", unit)
            .putString(prefix + "slope", result.slope.toString())
            .putString(prefix + "offset", result.offset.toString())
            .putString(prefix + "fit_rmse", result.fitRmse.toString())
            .putString(prefix + "validation_rmse", validationRmse.toString())
            .putInt(prefix + "points", result.points)
            .putString(prefix + "source", normalizedSource)
            .putLong(prefix + "created_at", System.currentTimeMillis())
            .putString(prefix + "algorithm_version", "linear-loo-v2")
            .putString(prefix + "validation_report", result.validationReport?.let {
                "MAE=${it.mae}; RMSE=${it.rmse}; bias=${it.bias}; difference_limits=${it.lowerAgreementLimit},${it.upperAgreementLimit}; bias_normal_95_ci=${it.lowerBiasConfidenceLimit},${it.upperBiasConfidenceLimit}; n=${it.sampleCount}; validation=leave-one-out; external_validation=unavailable"
            }.orEmpty())
            .commit()
    }

    fun clear(kind: String) {
        val prefix = "$kind."
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    companion object {
        internal const val PREFS = "sensor_external_calibration_profiles"
        const val MIN_POINTS = 5
        val SUPPORTED_KINDS = setOf(
            "pressure", "light", "magnetometer",
            "accelerometer_uncalibrated", "gyroscope_uncalibrated"
        )
    }
}
