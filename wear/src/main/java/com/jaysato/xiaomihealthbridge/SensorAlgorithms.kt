package com.jaysato.xiaomihealthbridge

import kotlin.math.abs
import kotlin.math.sqrt

object SensorAlgorithms {
    fun accelerationMagnitude(values: FloatArray): Double =
        sqrt(values.take(3).sumOf { it.toDouble() * it.toDouble() })

    fun fallRisk(magnitude: Double, previousMagnitude: Double, now: Long, lastAlert: Long): Boolean =
        magnitude > 25.0 && abs(magnitude - previousMagnitude) > 12.0 && now - lastAlert >= 30_000L

    fun heading(degrees: Double): String = listOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")[(degrees + 22.5).toInt() / 45 % 8]

    fun cadence(stepEvents: Int, durationSeconds: Long): Double =
        if (durationSeconds <= 0) 0.0 else stepEvents * 60.0 / durationSeconds

    fun elevationGain(previous: Double, current: Double, accumulated: Double): Double =
        if (current > previous && current - previous < 4.0) accumulated + current - previous else accumulated

    fun ambientPowerMode(lux: Float, enabled: Boolean): String = when {
        !enabled -> "標準亮度"
        lux < 5f -> "夜間省電"
        lux < 80f -> "室內省電"
        else -> "戶外亮度"
    }
}
