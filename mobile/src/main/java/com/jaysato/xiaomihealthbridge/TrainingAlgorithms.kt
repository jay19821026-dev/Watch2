package com.jaysato.xiaomihealthbridge

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

object TrainingAlgorithms {
    fun paceMinutesPerKm(distanceMeters: Double, durationMinutes: Double): Double? =
        if (distanceMeters > 0.0 && durationMinutes > 0.0) durationMinutes / (distanceMeters / 1000.0) else null

    fun speedKmh(distanceMeters: Double, durationMinutes: Double): Double? =
        if (distanceMeters > 0.0 && durationMinutes > 0.0) distanceMeters / 1000.0 / (durationMinutes / 60.0) else null

    fun heartRateRecovery(preExercise: Double, oneMinuteAfter: Double): Double? =
        if (preExercise > 0.0 && oneMinuteAfter >= 0.0) (preExercise - oneMinuteAfter).coerceAtLeast(0.0) else null

    fun trainingLoad(durationMinutes: Double, averageHeartRate: Double, maxHeartRate: Double): Int {
        if (durationMinutes <= 0.0 || averageHeartRate <= 0.0 || maxHeartRate <= 0.0) return 0
        val intensity = (averageHeartRate / maxHeartRate).coerceIn(0.0, 1.0)
        return (durationMinutes * intensity.pow(2.0) * 10.0).roundToInt().coerceIn(0, 999)
    }

    fun climbEfficiency(distanceMeters: Double, elevationGainMeters: Double): Double? =
        if (distanceMeters > 0.0 && elevationGainMeters >= 0.0) elevationGainMeters / (distanceMeters / 1000.0) else null

    fun vo2Max(distanceMeters: Double, durationMinutes: Double, averageHeartRate: Double?, maxHeartRate: Double?): Double? {
        if (distanceMeters <= 0.0 || durationMinutes <= 0.0) return null
        val speed = distanceMeters / durationMinutes
        val runningEstimate = 3.5 + speed * 0.2
        val heartCorrection = if (averageHeartRate != null && maxHeartRate != null && maxHeartRate > 0.0)
            (maxHeartRate / averageHeartRate).coerceIn(0.75, 1.35) else 1.0
        return (runningEstimate * heartCorrection).coerceIn(10.0, 85.0)
    }

    fun splitCount(durationMinutes: Double, lapMinutes: Double = 1.0): Int =
        if (durationMinutes <= 0.0 || lapMinutes <= 0.0) 0 else max(1, (durationMinutes / lapMinutes).roundToInt())
}
