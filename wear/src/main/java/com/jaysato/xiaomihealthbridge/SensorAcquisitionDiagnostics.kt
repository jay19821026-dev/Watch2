package com.jaysato.xiaomihealthbridge

import android.content.Context

internal object SensorAcquisitionDiagnostics {
    const val PREFS = "sensor_acquisition_diagnostics"

    fun recordStart(context: Context, kind: String, registered: Boolean) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("${kind}_stopped_at")
            .putLong("${kind}_session_started_at", now)
            .putLong("${kind}_first_sample_at", 0L)
            .putLong("${kind}_last_sample_at", 0L)
            .putInt("${kind}_event_count", 0)
            .putString("${kind}_registration", if (registered) "registered" else "registration_failed")
            .putString("${kind}_sample_timestamp_source", "pending")
            .putString("${kind}_stop_reason", "running")
            .apply()
    }

    fun recordStop(context: Context, kind: String, reason: String, eventCount: Int, firstSampleAt: Long, lastSampleAt: Long, timestampSource: String = "unknown") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("${kind}_stopped_at", System.currentTimeMillis())
            .putInt("${kind}_event_count", eventCount)
            .putLong("${kind}_first_sample_at", firstSampleAt)
            .putLong("${kind}_last_sample_at", lastSampleAt)
            .putString("${kind}_sample_timestamp_source", timestampSource)
            .putString("${kind}_stop_reason", reason.take(96))
            .apply()
    }
}
