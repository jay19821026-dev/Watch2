package com.jaysato.xiaomihealthbridge

import kotlin.math.pow

object HearingExposure {
    fun allowedMinutesAt(levelDba: Double): Double? {
        if (!levelDba.isFinite() || levelDba < 80.0) return null
        return 480.0 / 2.0.pow((levelDba - 85.0) / 3.0)
    }

    fun dosePercent(levelDba: Double, minutes: Double): Double? =
        allowedMinutesAt(levelDba)?.takeIf { it > 0.0 }
            ?.let { minutes.coerceAtLeast(0.0) / it * 100.0 }
}
