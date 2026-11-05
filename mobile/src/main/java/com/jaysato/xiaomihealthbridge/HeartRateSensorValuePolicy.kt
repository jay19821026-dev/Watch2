package com.jaysato.xiaomihealthbridge

internal object HeartRateSensorValuePolicy {
    data class Assessment(val bpm: Double?, val status: String)

    fun assess(latest: List<Double>, waveform: List<Float>, eventCount: Int = 0, sampleCount: Int = 0): Assessment {
        val bpm = latest.asReversed().firstOrNull { it.isFinite() && it in 30.0..220.0 }
            ?: waveform.asReversed().firstOrNull { it.isFinite() && it in 30.0f..220.0f }?.toDouble()
        val rawReceived = eventCount > 0 || sampleCount > 0 || latest.isNotEmpty() || waveform.isNotEmpty()
        return Assessment(
            bpm = bpm,
            status = when {
                bpm != null -> "has_data"
                rawReceived -> "invalid_sensor_values"
                else -> "no_events"
            }
        )
    }
}
