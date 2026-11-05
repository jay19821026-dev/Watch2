package com.jaysato.xiaomihealthbridge

object WearComputePolicy {
    private const val SEVERE_THERMAL_STATUS = 3

    fun ecgPreviewIntervalMillis(
        availableProcessors: Int,
        thermalStatus: Int?,
        batteryPercent: Int?
    ): Long = when {
        thermalStatus == null -> 1_000L
        thermalStatus >= SEVERE_THERMAL_STATUS -> 1_500L
        batteryPercent == null -> 700L
        batteryPercent <= 15 || availableProcessors <= 2 -> 1_000L
        availableProcessors >= 4 && batteryPercent > 30 -> 400L
        else -> 700L
    }
}
