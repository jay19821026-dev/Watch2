package com.jaysato.xiaomihealthbridge

internal object SensorWaveformSelectionPolicy {
    fun ppgDisplayWaveform(displayWaveform: List<Float>, analysisWaveform: List<Float>): List<Float> =
        displayWaveform.takeIf { it.isNotEmpty() } ?: analysisWaveform
}
