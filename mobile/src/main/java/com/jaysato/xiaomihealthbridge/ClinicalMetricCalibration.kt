package com.jaysato.xiaomihealthbridge

/**
 * Clinical-reference calibration for non-waveform Health Connect / raw metrics.
 */
object ClinicalMetricCalibration {
    data class Result(
        val metric: String,
        val value: Double?,
        val grade: String,
        val score: Int?,
        val headline: String,
        val comparisons: List<String>,
        val trusted: Boolean
    )

    fun forOxygen(percent: Double?, qualityHint: Double? = null): Result {
        val p = percent?.takeIf { it.isFinite() && it in 70.0..100.0 }
        val comps = ClinicalReferenceTables.spo2Compare(p)
        return Result(
            metric = "oxygen",
            value = p,
            grade = "未評分",
            score = null,
            headline = when {
                p == null -> "尚未取得可用的 SpO₂ 百分比"
                p >= 95.0 -> "血氧落在健康成人海平面常見帶（參考）"
                p >= 92.0 -> "血氧略低於常見下限；請確認灌注／海拔後複測"
                p >= 90.0 -> "血氧偏低；建議用指夾血氧或就醫確認"
                else -> "血氧低於公開緊急對照門檻；請儘速就醫（腕式不可替代急診）"
            },
            comparisons = comps,
            trusted = false
        )
    }

    fun forHeartRate(bpm: Double?, resting: Boolean): Result {
        val band = if (resting) ClinicalReferenceTables.RESTING_HR_BPM else ClinicalReferenceTables.PLAUSIBLE_HR_BPM
        val comps = ClinicalReferenceTables.compareMetric(if (resting) "resting_heart_rate" else "heart_rate", bpm)
        val score = when {
            bpm == null || !bpm.isFinite() -> null
            !ClinicalReferenceTables.PLAUSIBLE_HR_BPM.contains(bpm) -> null
            resting && band.contains(bpm) -> 92
            resting -> 55
            ClinicalReferenceTables.RESTING_HR_BPM.contains(bpm) -> 90
            else -> 78
        }
        return Result(
            metric = if (resting) "resting_heart_rate" else "heart_rate",
            value = bpm,
            grade = score?.let(::grade) ?: "未評分",
            score = score,
            headline = when {
                bpm == null -> "尚未取得心率"
                !ClinicalReferenceTables.PLAUSIBLE_HR_BPM.contains(bpm) -> "心率超出生理可置信窗，可能為偽訊"
                resting && band.contains(bpm) -> "靜止心率落在成人安靜常見帶"
                resting -> "靜止心率偏離安靜常見帶（可能受恢復／藥物／心律影響）"
                else -> "瞬時心率在可置信窗內（非診斷）"
            },
            comparisons = comps,
            trusted = bpm != null && ClinicalReferenceTables.PLAUSIBLE_HR_BPM.contains(bpm)
        )
    }

    fun forHrv(sdnnMs: Double?, rmssdMs: Double?): Result {
        val comps = ClinicalReferenceTables.hrvCompare(sdnnMs, rmssdMs)
        val primary = rmssdMs ?: sdnnMs
        val inBand = when {
            rmssdMs != null -> ClinicalReferenceTables.HRV_RMSSD_MS.contains(rmssdMs)
            sdnnMs != null -> ClinicalReferenceTables.HRV_SDNN_MS.contains(sdnnMs)
            else -> false
        }
        val score = when {
            primary == null -> null
            inBand -> 90
            else -> 45
        }
        return Result(
            metric = "hrv",
            value = primary,
            grade = score?.let(::grade) ?: "未評分",
            score = score,
            headline = if (primary == null) "尚無 HRV" else if (inBand) "短時 HRV 落在研究常見帶（需安靜標準化）" else "HRV 偏離短時研究常見帶或條件不符",
            comparisons = comps,
            trusted = primary != null
        )
    }

    fun forSleepHours(hours: Double?): Result {
        val comps = ClinicalReferenceTables.sleepCompare(hours)
        val score = when {
            hours == null -> null
            ClinicalReferenceTables.SLEEP_HOURS.contains(hours) -> 90
            hours in 6.0..10.0 -> 55
            else -> 30
        }
        return Result(
            metric = "sleep",
            value = hours,
            grade = score?.let(::grade) ?: "未評分",
            score = score,
            headline = when {
                hours == null -> "尚無睡眠時數"
                ClinicalReferenceTables.SLEEP_HOURS.contains(hours) -> "睡眠總時長落在成人常見建議帶"
                else -> "睡眠總時長偏離 7–9 小時常見建議（非分期診斷）"
            },
            comparisons = comps,
            trusted = hours != null
        )
    }

    fun forSteps(steps: Double?): Result {
        val comps = ClinicalReferenceTables.stepsCompare(steps)
        val score = when {
            steps == null -> null
            steps >= 10000 -> 90
            steps >= 7000 -> 75
            steps >= 4000 -> 55
            else -> 35
        }
        return Result(
            metric = "steps",
            value = steps,
            grade = score?.let(::grade) ?: "未評分",
            score = score,
            headline = when {
                steps == null -> "尚無步數"
                steps >= 7000 -> "步數達公共衛生常引用活動量級"
                else -> "步數低於常引用 7,000 步／日量級（非臨床診斷）"
            },
            comparisons = comps,
            trusted = steps != null
        )
    }

    private fun grade(score: Int): String = when {
        score < 25 -> "無效"
        score < 50 -> "偏弱"
        score < 75 -> "可用"
        score < 90 -> "良好"
        else -> "優良"
    }
}
