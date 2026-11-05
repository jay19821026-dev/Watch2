package com.jaysato.xiaomihealthbridge

internal object PpgVascularInput {
    fun parseWaveform(displayWaveform: String?, rawWaveform: String?): List<Float> {
        val selected = displayWaveform?.takeIf(String::isNotBlank) ?: rawWaveform.orEmpty()
        return SensorSuiteCsvParser.parseFloatsWithGapMarkers(selected)
    }
}
