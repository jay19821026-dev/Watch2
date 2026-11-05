package com.jaysato.xiaomihealthbridge

internal object RawWaveformDisplayPolicy {
    private const val OPTICAL_FILL_VALUE = 524287f

    fun isGap(value: Float, modality: String): Boolean {
        if (!value.isFinite()) return true
        val isOptical = modality.contains("ppg", ignoreCase = true) ||
            modality.contains("spo2", ignoreCase = true) ||
            modality.contains("optical", ignoreCase = true)
        return isOptical && value == OPTICAL_FILL_VALUE
    }
}
