package com.jaysato.xiaomihealthbridge

data class BatterySample(val levelPercent: Int, val elapsedMillis: Long)

data class BatteryDrainEstimate(
    val percentPerHour: Double?,
    val estimatedHoursRemaining: Double?,
    val shouldConserve: Boolean
)

internal object BatteryDrainEstimator {
    fun estimate(
        samples: List<BatterySample>,
        currentLevelPercent: Int,
        conserveThresholdPercentPerHour: Double = 12.0
    ): BatteryDrainEstimate {
        val first = samples.firstOrNull()
        val last = samples.lastOrNull()
        val elapsedHours = if (first != null && last != null) {
            (last.elapsedMillis - first.elapsedMillis) / 3_600_000.0
        } else 0.0
        val drain = if (elapsedHours > 0.0 && first != null && last != null) {
            ((first.levelPercent - last.levelPercent) / elapsedHours).coerceAtLeast(0.0)
        } else null
        val remaining = if (drain != null && drain > 0.0) currentLevelPercent / drain else null
        return BatteryDrainEstimate(
            percentPerHour = drain,
            estimatedHoursRemaining = remaining,
            shouldConserve = drain != null && drain >= conserveThresholdPercentPerHour
        )
    }
}
