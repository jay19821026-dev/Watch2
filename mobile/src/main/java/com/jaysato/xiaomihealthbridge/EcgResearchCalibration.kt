package com.jaysato.xiaomihealthbridge

import kotlin.math.sqrt
import com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine

/**
 * Research-only calibration using an external ECG simulator or reference source.
 * This does not infer Xiaomi's private ADC, gain, lead wiring, or clinical status.
 */
object EcgResearchCalibration {
    data class Fit(
        val slopeMvPerRaw: Double,
        val offsetMv: Double,
        val rmseMv: Double,
        val points: Int,
        val usable: Boolean,
        val note: String
    )

    data class SampleRate(
        val hz: Double?,
        val medianIntervalMs: Double?,
        val jitterMs: Double?,
        val usable: Boolean,
        val note: String
    )

    data class LeaveOneOutValidation(
        val rmseMv: Double?,
        val heldOutPoints: Int,
        val usable: Boolean,
        val note: String
    )

    data class IndependentHoldoutValidation(
        val rmseMv: Double?,
        val heldOutPoints: Int,
        val usable: Boolean,
        val note: String,
        val maximumAllowedRmseMv: Double? = null,
        val passed: Boolean = false
    )

    data class CalibrationProfile(
        val fit: Fit,
        val sampleRate: SampleRate,
        val source: String = "external_reference_research_only",
        val clinicalValidation: Boolean = false
    )

    fun canSaveResearchProfile(fit: Fit, validation: LeaveOneOutValidation): Boolean =
        fit.usable && fit.points >= MIN_RESEARCH_PROFILE_POINTS &&
            fit.slopeMvPerRaw.isFinite() && fit.slopeMvPerRaw != 0.0 &&
            fit.offsetMv.isFinite() && fit.rmseMv.isFinite() && fit.rmseMv >= 0.0 &&
            validation.usable && validation.rmseMv?.isFinite() == true &&
            validation.rmseMv >= 0.0 &&
            validation.heldOutPoints >= MIN_RESEARCH_PROFILE_POINTS

    fun fitRawToMillivolts(raw: List<Double>, referenceMv: List<Double>): Fit {
        if (raw.size != referenceMv.size || raw.size < 3 || raw.size > MAX_RESEARCH_CALIBRATION_POINTS ||
            raw.any { !it.isFinite() } || referenceMv.any { !it.isFinite() }) {
            return Fit(0.0, 0.0, Double.NaN, minOf(raw.size, referenceMv.size), false,
                "需提供 3 至 $MAX_RESEARCH_CALIBRATION_POINTS 組等長、有限數值的同步配對點")
        }
        val points = raw.zip(referenceMv)
        if (referenceMv.max() - referenceMv.min() <= 1e-9) {
            return Fit(0.0, 0.0, Double.NaN, points.size, false, "參考 mV 沒有變化，無法驗證增益")
        }
        val xMean = points.map { it.first }.average()
        val yMean = points.map { it.second }.average()
        val denominator = points.sumOf { (x, _) -> (x - xMean) * (x - xMean) }
        if (denominator <= 1e-12) {
            return Fit(0.0, 0.0, Double.NaN, points.size, false, "參考點的 raw 值沒有變化")
        }
        val slope = points.sumOf { (x, y) -> (x - xMean) * (y - yMean) } / denominator
        val offset = yMean - slope * xMean
        val rmse = sqrt(points.map { (x, y) ->
            val error = slope * x + offset - y
            error * error
        }.average())
        if (!slope.isFinite() || !offset.isFinite() || !rmse.isFinite()) {
            return Fit(0.0, 0.0, Double.NaN, points.size, false, "擬合數值溢位")
        }
        return Fit(slope, offset, rmse, points.size, true, "配對點線性擬合；尚未完成獨立驗證或 OEM ADC 校準")
    }

    const val MIN_RESEARCH_PROFILE_POINTS = 5
    const val MAX_RESEARCH_CALIBRATION_POINTS = 256

    /** Leave-one-out validation prevents reporting a fit-only RMSE as independent evidence. */
    fun validateWithLeaveOneOut(raw: List<Double>, referenceMv: List<Double>): LeaveOneOutValidation {
        if (raw.size != referenceMv.size || raw.size < 5 || raw.size > MAX_RESEARCH_CALIBRATION_POINTS ||
            raw.any { !it.isFinite() } || referenceMv.any { !it.isFinite() }) {
            return LeaveOneOutValidation(null, 0, false, "留一驗證至少需要五組有限且同步的 raw:mV 配對點")
        }
        val errors = raw.indices.mapNotNull { heldOut ->
            val trainRaw = raw.filterIndexed { index, _ -> index != heldOut }
            val trainReference = referenceMv.filterIndexed { index, _ -> index != heldOut }
            val fit = fitRawToMillivolts(trainRaw, trainReference)
            if (!fit.usable) null else {
                val predicted = raw[heldOut] * fit.slopeMvPerRaw + fit.offsetMv
                (predicted - referenceMv[heldOut]).takeIf(Double::isFinite)
            }
        }
        if (errors.size != raw.size) {
            return LeaveOneOutValidation(null, errors.size, false, "留出驗證無法為每個點建立有效線性模型")
        }
        val rmse = sqrt(errors.map { it * it }.average())
        return LeaveOneOutValidation(rmse, errors.size, true, "同一資料集的 leave-one-out 交叉驗證；仍非獨立外部、OEM 或臨床驗證")
    }

    fun validateIndependentHoldout(
        trainRaw: List<Double>,
        trainReferenceMv: List<Double>,
        holdoutRaw: List<Double>,
        holdoutReferenceMv: List<Double>,
        maximumAllowedRmseMv: Double
    ): IndependentHoldoutValidation {
        if (!maximumAllowedRmseMv.isFinite() || maximumAllowedRmseMv <= 0.0 ||
            holdoutRaw.size != holdoutReferenceMv.size ||
            holdoutRaw.size !in MIN_RESEARCH_PROFILE_POINTS..MAX_RESEARCH_CALIBRATION_POINTS ||
            trainRaw.size != trainReferenceMv.size || trainRaw.size < MIN_RESEARCH_PROFILE_POINTS ||
            trainRaw.any { !it.isFinite() } || trainReferenceMv.any { !it.isFinite() } ||
            holdoutRaw.any { !it.isFinite() } || holdoutReferenceMv.any { !it.isFinite() }) {
            return IndependentHoldoutValidation(null, 0, false, "訓練集與獨立留出集都需至少五組同步有限配對點")
        }
        val trainingRawMinimum = trainRaw.minOrNull() ?: return IndependentHoldoutValidation(null, 0, false, "訓練 raw 範圍無效")
        val trainingRawMaximum = trainRaw.maxOrNull() ?: return IndependentHoldoutValidation(null, 0, false, "訓練 raw 範圍無效")
        if (holdoutRaw.any { it !in trainingRawMinimum..trainingRawMaximum }) {
            return IndependentHoldoutValidation(null, 0, false, "留出 raw 超出訓練範圍；拒絕外插校準")
        }
        val holdoutReferenceRange = (holdoutReferenceMv.maxOrNull() ?: return IndependentHoldoutValidation(null, 0, false, "留出參考範圍無效")) -
            (holdoutReferenceMv.minOrNull() ?: return IndependentHoldoutValidation(null, 0, false, "留出參考範圍無效"))
        if (!holdoutReferenceRange.isFinite() || holdoutReferenceRange <= 0.0 ||
            maximumAllowedRmseMv > EcgCoreResearchEngine.MAX_ABSOLUTE_HOLDOUT_RMSE_MV ||
            maximumAllowedRmseMv > holdoutReferenceRange * EcgCoreResearchEngine.MAX_HOLDOUT_RMSE_FRACTION_OF_REFERENCE_RANGE) {
            return IndependentHoldoutValidation(null, 0, false,
                "RMSE 門檻過寬；不得超過獨立留出參考振幅範圍的 5% 或 ${EcgCoreResearchEngine.MAX_ABSOLUTE_HOLDOUT_RMSE_MV} mV（App 研究閘門，非標準符合聲明）")
        }
        val fit = fitRawToMillivolts(trainRaw, trainReferenceMv)
        if (!fit.usable) return IndependentHoldoutValidation(null, 0, false, "訓練集無法建立有效線性 profile")
        val errors = holdoutRaw.indices.map { index ->
            fit.slopeMvPerRaw * holdoutRaw[index] + fit.offsetMv - holdoutReferenceMv[index]
        }
        if (errors.any { !it.isFinite() }) return IndependentHoldoutValidation(null, 0, false, "獨立留出集預測數值無效")
        val rmse = sqrt(errors.map { it * it }.average())
        val passed = rmse <= maximumAllowedRmseMv
        return IndependentHoldoutValidation(rmse, errors.size, true,
            if (passed) "獨立留出 RMSE 符合方案門檻；仍非 OEM 或臨床驗證"
            else "獨立留出 RMSE 超過方案門檻；拒絕 profile",
            maximumAllowedRmseMv, passed)
    }

    fun estimateSampleRate(timestampMs: List<Long>): SampleRate {
        val intervals = timestampMs.zipWithNext()
            .map { it.second - it.first }
            .filter { it in 1L..10_000L }
            .sorted()
        if (intervals.size < 2) {
            return SampleRate(null, null, null, false, "時間戳不足或無有效間隔")
        }
        val middle = intervals.size / 2
        val median = if (intervals.size % 2 == 0) {
            (intervals[middle - 1] + intervals[middle]) / 2.0
        } else intervals[middle].toDouble()
        val mean = intervals.average()
        val jitter = sqrt(intervals.map { (it - mean) * (it - mean) }.average())
        val hz = 1000.0 / median
        return SampleRate(hz, median, jitter, true, "由 raw timestamp 推估；不是 OEM 時鐘設定")
    }

    fun estimateEventRateNs(timestampsNs: List<Long>): SampleRate {
        val intervals = timestampsNs.zipWithNext()
            .mapNotNull { (previous, current) -> if (current > previous) current - previous else null }
            .filter { it in 1_000_000L..10_000_000_000L }
            .sorted()
        if (intervals.size < 2) {
            return SampleRate(null, null, null, false, "ECG 事件時間戳不足")
        }
        val medianNs = if (intervals.size % 2 == 0) {
            (intervals[intervals.size / 2 - 1].toDouble() + intervals[intervals.size / 2]) / 2.0
        } else intervals[intervals.size / 2].toDouble()
        val meanNs = intervals.map(Long::toDouble).average()
        val jitterNs = sqrt(intervals.map { (it - meanNs) * (it - meanNs) }.average())
        return SampleRate(1_000_000_000.0 / medianNs, medianNs / 1_000_000.0,
            jitterNs / 1_000_000.0, true, "Sensor HAL 事件頻率，非 ADC 取樣率")
    }

    fun estimatePackedPointRateNs(
        timestampsNs: List<Long>,
        sampleCounts: List<Int>,
        waveformEncoding: String?
    ): SampleRate {
        if (!EcgPayloadInterpretationPolicy.canInterpretAsWaveform(waveformEncoding)) {
            return SampleRate(null, null, null, false, "ECG 事件值映射未驗證；不估算波形點速率")
        }
        if (timestampsNs.size != sampleCounts.size || timestampsNs.size < 3) {
            return SampleRate(null, null, null, false, "事件時間與封包點數不完整")
        }
        val intervals = timestampsNs.zipWithNext()
            .mapNotNull { (previous, current) -> if (current > previous) current - previous else null }
            .filter { it in 1_000_000L..10_000_000_000L }
            .sorted()
        if (intervals.size < 2) {
            return SampleRate(null, null, null, false, "沒有足夠的正向事件間隔")
        }
        val medianIntervalNs = intervals[intervals.size / 2]
        val pointRates = timestampsNs.indices.drop(1).mapNotNull { index ->
            val previous = timestampsNs[index - 1]
            val current = timestampsNs[index]
            if (current <= previous) return@mapNotNull null
            val intervalNs = current - previous
            val points = sampleCounts[index]
            if (intervalNs in 1_000_000L..(medianIntervalNs * 3) && points in 1..32) {
                points * 1_000_000_000.0 / intervalNs
            } else null
        }.sorted()
        if (pointRates.size < 2) {
            return SampleRate(null, null, null, false, "連續有效封包不足")
        }
        return SampleRate(pointRates[pointRates.size / 2], medianIntervalNs / 1_000_000.0,
            null, true, "依連續 HAL 封包估算的有效點速率；不是已驗證 ADC 時鐘")
    }
}
