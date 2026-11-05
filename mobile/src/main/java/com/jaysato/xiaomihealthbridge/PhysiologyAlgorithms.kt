package com.jaysato.xiaomihealthbridge

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PhysiologyAlgorithms {
    enum class Sex { MALE, FEMALE, UNSPECIFIED }
    data class ExerciseResult(
        val activeCalories: Int,
        val estimatedDailyCalories: Int,
        val intensityZone: String,
        val insight: String
    )

    data class RecoveryResult(val additionalHours: Int, val totalHours: Int, val effect: String, val guidance: String)
    data class RhythmResult(val irregular: Boolean, val risk: String, val sdnn: Double?, val rmssd: Double?)

    fun exercise(
        age: Int,
        sex: Sex,
        heightCm: Double,
        weightKg: Double,
        durationMinutes: Double,
        averageHeartRate: Double,
        met: Double
    ): ExerciseResult {
        require(age in 13..100 && heightCm in 100.0..230.0 && weightKg in 25.0..350.0)
        require(durationMinutes >= 0 && met >= 1)
        val sexOffset = when (sex) { Sex.MALE -> 5.0; Sex.FEMALE -> -161.0; Sex.UNSPECIFIED -> -78.0 }
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + sexOffset
        val active = ((met - 1.0) * 3.5 * weightKg / 200.0 * durationMinutes).coerceAtLeast(0.0)
        val maxHeartRate = 208.0 - 0.7 * age
        val ratio = averageHeartRate / maxHeartRate
        val zone = when {
            ratio < .50 -> "低強度"
            ratio < .70 -> "燃脂"
            ratio < .85 -> "心肺強化"
            else -> "極限"
        }
        val epoc = when {
            ratio >= .85 && durationMinutes >= 20 -> "後燃效應可能延續數小時"
            ratio >= .70 -> "有輕至中度後燃效應"
            else -> "後燃效應通常有限"
        }
        return ExerciseResult(active.roundToInt(), (bmr + active).roundToInt(), zone, "${zone}刺激；$epoc。")
    }

    fun recovery(vo2Max: Double?, averageHeartRate: Double, maxHeartRate: Double, durationMinutes: Int, remainingHours: Int): RecoveryResult {
        require(averageHeartRate > 0 && maxHeartRate >= averageHeartRate && durationMinutes >= 0 && remainingHours >= 0)
        val ratio = averageHeartRate / maxHeartRate
        val fitnessFactor = vo2Max?.let { (45.0 / it.coerceAtLeast(20.0)).coerceIn(.75, 1.35) } ?: 1.0
        val additional = (durationMinutes / 60.0 * 18 * ratio.pow(2.4) * fitnessFactor).roundToInt().coerceIn(1, 72)
        val total = (remainingHours * .65 + additional).roundToInt().coerceIn(additional, 96)
        val effect = when { ratio >= .88 -> "無氧耐力／高負荷"; ratio >= .70 -> "有氧心肺"; else -> "低強度恢復" }
        val guidance = when { total >= 36 -> "恢復完成前僅建議散步、伸展或低強度恢復跑"; total >= 18 -> "下一場限制在低至中強度"; else -> "可依主觀疲勞逐步恢復正常訓練" }
        return RecoveryResult(additional, total, effect, guidance)
    }

    fun rhythm(rrIntervalsMs: List<Double>, hasSymptoms: Boolean): RhythmResult {
        val clean = rrIntervalsMs.filter { it in 300.0..2000.0 }
        if (clean.size < 30) return RhythmResult(false, "資料不足：需要至少 30 個有效 RR 間期", null, null)
        val mean = clean.average()
        val sdnn = sqrt(clean.sumOf { (it - mean).pow(2) } / (clean.size - 1))
        val differences = clean.zipWithNext { first, second -> second - first }
        val rmssd = sqrt(differences.sumOf { it.pow(2) } / differences.size)
        val successiveIrregular = differences.count { kotlin.math.abs(it) > 120.0 }.toDouble() / differences.size
        val irregular = sdnn / mean > .12 && successiveIrregular > .20
        val clinical = ClinicalReferenceTables.hrvCompare(sdnn, rmssd).joinToString("；")
        val risk = when {
            hasSymptoms && irregular -> "偵測到高度不規則且伴隨不適，請立即尋求醫療評估"
            irregular -> "可能受體動、感測接觸、咖啡因或心律不整影響；請以醫療級 ECG 確認 · $clinical"
            hasSymptoms -> "節律指標未達不規則門檻，但有症狀仍應就醫 · $clinical"
            else -> "此段 RR 間期未達不規則警示門檻 · $clinical"
        }
        return RhythmResult(irregular, risk, sdnn, rmssd)
    }

}
