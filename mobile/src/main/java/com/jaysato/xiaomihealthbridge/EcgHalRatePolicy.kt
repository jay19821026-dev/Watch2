package com.jaysato.xiaomihealthbridge

internal object EcgHalRatePolicy {
    fun sampleRateHz(halSamplePeriodSeconds: Double?): Float? {
        val period = halSamplePeriodSeconds?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val rate = 1.0 / period
        return rate.takeIf { it.isFinite() && it in 1.0..2_000.0 }?.toFloat()
    }
}
