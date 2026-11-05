package com.jaysato.xiaomihealthbridge

internal object TemperatureSensorValuePolicy {
    data class Assessment(val deviceRawValue: Double?, val status: String)

    fun detailValue(storedAverage: Double?, waveform: List<Float>, latest: List<Double>): Double? =
        assess(waveform, latest).deviceRawValue ?: storedAverage?.takeIf(Double::isFinite)

    fun assess(waveform: List<Float>, latest: List<Double>): Assessment {
        val validWaveformValues = waveform.asSequence()
            .map(Float::toDouble)
            .filter { it.isFinite() }
            .toList()
        val deviceRawValue = validWaveformValues.average()
            .takeIf { validWaveformValues.isNotEmpty() }
            ?: latest.firstOrNull(Double::isFinite)
        val rawReceived = waveform.isNotEmpty() || latest.isNotEmpty()
        val status = when {
            deviceRawValue != null -> "has_data"
            rawReceived -> "invalid_sensor_values"
            else -> "no_events"
        }
        return Assessment(deviceRawValue, status)
    }
}
