package com.jaysato.xiaomihealthbridge

import kotlin.math.roundToInt

object ResearchCompositeModel {
    const val VERSION = "personal-hrv-sleep-rule-v1"
    data class Scores(val stress: Int?, val bodyEnergy: Int?, val recovery: Int?, val readiness: Int?)

    fun calculate(hrvLatest: Double?, hrvBaseline: Double?, sleepWindowHours: Double?, paired: Boolean): Scores {
        if (!paired || hrvLatest == null || hrvBaseline == null || !hrvLatest.isFinite() ||
            !hrvBaseline.isFinite() || hrvLatest <= 0 || hrvBaseline <= 0) return Scores(null, null, null, null)
        val relativeHrv = (hrvLatest / hrvBaseline).takeIf(Double::isFinite) ?: return Scores(null, null, null, null)
        val stress = (50.0 + 50.0 * (1.0 - relativeHrv)).coerceIn(0.0, 100.0).roundToInt()
        val hours = sleepWindowHours?.takeIf { it.isFinite() && it in 0.0..24.0 }
            ?: return Scores(stress, null, null, null)
        val sleepSupport = (hours / 8.0).coerceIn(0.0, 1.0) * 100.0
        val hrvSupport = (relativeHrv * 100.0).coerceIn(0.0, 100.0)
        val recovery = (0.5 * sleepSupport + 0.5 * hrvSupport).roundToInt()
        val energy = (0.6 * sleepSupport + 0.4 * (100 - stress)).roundToInt()
        val readiness = ((recovery + energy) / 2.0).roundToInt()
        return Scores(stress, energy, recovery, readiness)
    }
}
