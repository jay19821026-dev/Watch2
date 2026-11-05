package com.jaysato.xiaomihealthbridge

enum class PowerWorkload { IDLE, PASSIVE_SYNC, MEASUREMENT, EXERCISE }

data class AiPowerDecision(
    val mode: WearPowerMode,
    val pauseBackground: Boolean,
    val allowMeasurement: Boolean,
    val batteryBand: String,
    val syncIntervalMinutes: Int?,
    val reason: String
)

internal object AiPowerEngine {
    fun decide(
        batteryPercent: Int,
        charging: Boolean,
        thermalLimited: Boolean,
        drainPercentPerHour: Double?,
        workload: PowerWorkload
    ): AiPowerDecision {
        if (workload == PowerWorkload.MEASUREMENT || workload == PowerWorkload.EXERCISE) {
            return AiPowerDecision(WearPowerMode.NORMAL, false, true, band(batteryPercent), 1, "使用者正在量測或運動")
        }
        if (thermalLimited && !charging) {
            return AiPowerDecision(WearPowerMode.SAVER, true, false, band(batteryPercent), 30, "系統回報嚴重熱限制，延後背景工作")
        }
        if (!charging && batteryPercent <= 15) {
            return AiPowerDecision(WearPowerMode.CRITICAL, true, false, band(batteryPercent), null, "電量極低，僅保留手動量測")
        }
        if (!charging && batteryPercent <= 30) {
            return AiPowerDecision(WearPowerMode.SAVER, true, true, band(batteryPercent), 30, "低電量，降低背景同步")
        }
        if (!charging && (drainPercentPerHour ?: 0.0) >= 12.0) {
            return AiPowerDecision(WearPowerMode.SAVER, true, true, band(batteryPercent), 15, "放電速度偏高")
        }
        return when {
            charging -> AiPowerDecision(WearPowerMode.NORMAL, false, true, band(batteryPercent), 5, "充電中，維持正常同步")
            batteryPercent <= 60 -> AiPowerDecision(WearPowerMode.SAVER, false, true, band(batteryPercent), 10, "中電量，降低非必要同步")
            else -> AiPowerDecision(WearPowerMode.NORMAL, false, true, band(batteryPercent), 5, "耗電狀態穩定")
        }
    }

    private fun band(percent: Int): String = when (percent.coerceIn(1, 100)) {
        in 1..15 -> "1–15%"
        in 16..30 -> "16–30%"
        in 31..60 -> "31–60%"
        in 61..80 -> "61–80%"
        else -> "81–100%"
    }
}
