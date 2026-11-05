package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.ClockEvidence
import com.jaysato.xiaomihealthbridge.engine.EcgCoreInput
import com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine
import com.jaysato.xiaomihealthbridge.engine.EcgCalibrationProfile
import com.jaysato.xiaomihealthbridge.engine.EcgRhythmPatternEngine
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Research-grade calibration aligned to [ClinicalReferenceTables].
 * Not clinical lead/gain metadata or OEM body-composition coefficients.
 */
object BiomedicalCalibration {
    private const val PPG_FILL = 524287f
    const val BIOZ_RESEARCH_VERSION = "bioz-relative-waveform-v2"

    data class PpgResult(
        val waveform: List<Float>,
        /** Engineering waveform gate only; not a physiological or clinical validity claim. */
        val valid: Boolean,
        val qualityScore: Int,
        val pulseRateBpm: Double?,
        val rrCount: Int,
        val effectiveSampleRateHz: Float,
        val comparisons: List<String>,
        val summary: String,
        val confidenceScore: Int? = null,
        val source: String = "MAX86178_HAL_RAW",
        val timestampSource: String = "UNKNOWN",
        val calibrationStatus: String = "UNVERIFIED",
        val rateEligible: Boolean = false,
        val dominantPeriodSamples: Int? = null,
        val periodCorrelation: Double? = null,
        val displayWaveform: List<Float> = emptyList()
    )

    data class EcgResult(
        val waveform: List<Float>,
        val contactScore: Int,
        val heartRateBpm: Double?,
        val rrCount: Int,
        val rrCv: Double?,
        val comparisons: List<String>,
        val summary: String,
        val calibrated: Boolean,
        val rPeakIndices: List<Int> = emptyList(),
        val rPeakIndexDistancesSamples: List<Int> = emptyList(),
        val timelineSource: String = "NORMALIZED_INDEX",
        val researchMvWaveform: List<Float>? = null,
        val rhythmPattern: EcgRhythmPatternEngine.Result? = null
    )

    data class BiozResult(
        val resistanceOhm: Double?,
        val reactanceOhm: Double?,
        val impedanceOhm: Double?,
        val phaseDeg: Double?,
        val hydrationIndex: Int?,
        val waveformMedianPositionIndex: Int?,
        val relativeChangePercent: Double?,
        val stabilityScore: Int?,
        val confidenceScore: Int?,
        val mode: ClinicalReferenceTables.BiozMode,
        val contactScore: Int,
        val waveform: List<Float>,
        val comparisons: List<String>,
        val summary: String,
        val calibrated: Boolean,
        val rawChannelValues: List<Double> = emptyList(),
        val displayWaveform: List<Float> = emptyList()
    )

    fun refinePpg(
        raw: List<Float>,
        sampleRateHz: Float,
        timestampSource: String = "UNKNOWN",
        channelMappingVerified: Boolean = false,
        wearQualified: Boolean = false
    ): PpgResult {
        val cleaned = raw.filter { it.isFinite() && it != PPG_FILL }
        val fs = sanitizePpgSampleRate(sampleRateHz, cleaned.size)
        val fillRatio = if (raw.isEmpty()) 1.0 else (raw.size - cleaned.size).toDouble() / raw.size
        val rawSpan = cleaned.maxOrNull()?.minus(cleaned.minOrNull() ?: 0f) ?: 0f
        val waveformWellFormed = raw.size >= 64 && fillRatio < 0.65 && cleaned.size >= 64 && rawSpan >= 24f
        val indexPeriod = estimateIndexDomainPeriodicity(raw)
        val indexDomainQuality = researchPpgIndexDomainQuality(raw, cleaned, rawSpan, indexPeriod)
        if (fs <= 0f || raw.size > cleaned.size * 2 || !waveformWellFormed) {
            val reason = when {
                fillRatio >= 0.65 -> "高比例 HAL 填充值，請先確認佩戴貼合與原廠 LED 模式"
                !waveformWellFormed -> "原始光學波形點數／振幅不足"
                else -> "PPG 取樣率未提供"
            }
            return PpgResult(
                cleaned,
                false,
                indexDomainQuality,
                null,
                0,
                fs,
                listOf(
                    "Sensor HAL 未提供可驗證的 PPG 單通道映射與 ADC 取樣時鐘；事件吞吐率不是波形取樣率",
                    "ADC 取樣時鐘未驗證；單通道脈率／HRV 推算已停用",
                    indexPeriod?.let {
                        "索引域重複週期：${it.first} 個索引／週期；最大自相關 ${"%.2f".format(it.second)}，非脈率或時間週期"
                    },
                    reason,
                    "只保留原始資料；不估算 BPM、HRV、SpO₂ 或疾病風險"
                ).filterNotNull(),
                buildString {
                    append("PPG 原始封包已保留；缺少可信通道／取樣時鐘，未計算脈率或 HRV")
                    indexPeriod?.let { append(" · 索引域重複週期 ${it.first} 點／週期（自相關 ${"%.2f".format(it.second)}）") }
                },
                confidenceScore = ppgEvidenceScore(
                    raw, cleaned, indexDomainQuality, fs,
                    timestampSource, channelMappingVerified
                ),
                timestampSource = timestampSource,
                dominantPeriodSamples = indexPeriod?.first,
                periodCorrelation = indexPeriod?.second,
                displayWaveform = indexPreservingPpgWaveform(raw)
            )
        }
        if (cleaned.size < 24) {
            return PpgResult(
                cleaned, false, 0, null, 0, sampleRateHz.coerceAtLeast(0f),
                listOf(ClinicalReferenceTables.ppgRateCompare(sampleRateHz.takeIf { it.isFinite() && it > 0f })),
                "PPG 有效樣本不足；請貼合錶帶並保持靜止後重測",
                confidenceScore = null,
                timestampSource = timestampSource,
                displayWaveform = indexPreservingPpgWaveform(raw)
            )
        }
        val detrended = removeMovingAverage(cleaned, (fs / 2f).toInt().coerceIn(3, 25))
        val quality = if (fillRatio > 0.0) {
            ppgQualityPreservingGaps(raw, fs)
        } else {
            SensorHealthAlgorithms.waveformQuality(detrended, fs.coerceAtLeast(1f), "ppg")
        }
        val sourceTimeVerified = timestampSource == "android_sensor_event_elapsed_realtime"
        val rateEvidence = channelMappingVerified && sourceTimeVerified && wearQualified && fillRatio == 0.0
        val rr = if (rateEvidence) quality.rrIntervalsMs else emptyList()
        val pr = if (rateEvidence) stableRateFromRr(rr) else null
        val heartRateGate = fs >= 20f
        val variabilityGate = fs > 64f
        val rateGate = heartRateGate && rateEvidence
        val completenessAdjustedScore = (quality.score * (1.0 - fillRatio)).roundToInt()
        val score = completenessAdjustedScore
            .let { if (fs > 0f && fs < 10f) (it * 0.75).toInt() else it }
            .coerceIn(0, 100)
        val valid = heartRateGate && score >= 50 &&
            (detrended.max() - detrended.min()) > 1e-2f &&
            cleaned.size >= 64 &&
            rateGate
        val comparisons = buildList {
            add(ClinicalReferenceTables.ppgRateCompare(fs.takeIf { it > 0f }))
            if (!channelMappingVerified) add("PPG 通道映射未驗證；保留相對波形與品質，不換算脈率／HRV")
            if (!sourceTimeVerified) add("來源事件時間未確認；保留索引域波形，不將樣本索引換算成時間")
            if (!wearQualified) add("佩戴貼合未確認；不發布 PPG 脈率研究估算")
            if (fillRatio > 0.0) add("波形含缺值／HAL 填充值；移除無效點會改變時間間距，保留波形與索引週期但不換算脈率／HRV")
            if (fillRatio > 0.0) add("波形品質依各連續有效片段分別評估；不跨越缺口配對相鄰樣本")
            if (fillRatio > 0.0) add("PPG 品質分段摘要：${quality.metrics}")
            indexPeriod?.let {
                add("索引域重複週期：${it.first} 個索引／週期；最大自相關 ${"%.2f".format(it.second)}，非脈率或時間週期")
            }
            if (!heartRateGate) add("有效取樣率不足 20 Hz；不輸出脈率或 HRV")
            if (!variabilityGate) add("取樣率未超過 64 Hz；封鎖 PPG 派生 HRV／PRV")
            if (pr != null && heartRateGate) add(ClinicalReferenceTables.hrVsResting(pr))
            else add("脈率：尚無穩定 RR（SQI／模板一致度不足）")
            add(
                "Orphanidou SQI（IEEE JBHI 2015）：PPG 模板相關門檻 " +
                    "${ClinicalReferenceTables.PPG_SQI_CORR_THRESHOLD}；此分數為簡化實作"
            )
        }
        return PpgResult(
            waveform = detrended,
            valid = valid,
            qualityScore = score,
            pulseRateBpm = pr,
            rrCount = rr.size,
            effectiveSampleRateHz = fs,
            comparisons = comparisons,
            summary = if (valid && score >= 50) {
                buildString {
                    append("PPG 波形品質可供工程檢視 · 品質 ${ResearchScoreDisplay.format(score)}")
                    pr?.let { append(" · 研究脈率 ${"%.0f".format(it)} bpm") }
                    append(" · 原始通道與取樣率未經 OEM 驗證；不作醫療判讀")
                }
            } else {
                "PPG 訊號偏弱／取樣過稀或仍含填充值 · 品質 ${ResearchScoreDisplay.format(score)}；請貼合並重測"
            },
            confidenceScore = ppgEvidenceScore(raw, cleaned, score, fs, timestampSource, channelMappingVerified),
            timestampSource = timestampSource,
            rateEligible = valid,
            dominantPeriodSamples = indexPeriod?.first,
            periodCorrelation = indexPeriod?.second,
            displayWaveform = if (fillRatio > 0.0) indexPreservingPpgWaveform(raw) else detrended
        )
    }

    private fun indexPreservingPpgWaveform(raw: List<Float>): List<Float> = raw.map { value ->
        value.takeIf { it.isFinite() && it != PPG_FILL } ?: PPG_FILL
    }

    private fun estimateIndexDomainPeriodicity(values: List<Float>): Pair<Int, Double>? {
        if (values.size < 32 || values.size > 16_384) return null
        val count = values.size
        val valid = BooleanArray(count) { index ->
            val value = values[index]
            value.isFinite() && value != PPG_FILL
        }
        val validCount = valid.count { it }
        if (validCount < 32) return null
        val centerX = (count - 1) / 2.0
        val mean = values.indices.filter { valid[it] }.map { values[it].toDouble() }.average()
        val denominator = values.indices.filter { valid[it] }.sumOf { index ->
            val offset = index - centerX
            offset * offset
        }
        val slope = if (denominator > 0.0) {
            values.indices.filter { valid[it] }.sumOf { index ->
                (index - centerX) * (values[index].toDouble() - mean)
            } / denominator
        } else 0.0
        val detrended = DoubleArray(count) { index ->
            if (valid[index]) values[index].toDouble() - (mean + slope * (index - centerX)) else 0.0
        }
        val energy = values.indices.filter { valid[it] }.sumOf { detrended[it] * detrended[it] }
        if (!energy.isFinite() || energy <= 1e-12) return null
        val maxLag = (count / 2).coerceAtMost(512)
        if (maxLag < 3) return null
        val correlations = DoubleArray(maxLag + 1)
        for (lag in 2..maxLag) {
            var numerator = 0.0
            var leftEnergy = 0.0
            var rightEnergy = 0.0
            var pairCount = 0
            for (index in 0 until count - lag) {
                if (!valid[index] || !valid[index + lag]) continue
                val left = detrended[index]
                val right = detrended[index + lag]
                numerator += left * right
                leftEnergy += left * left
                rightEnergy += right * right
                pairCount++
            }
            val norm = kotlin.math.sqrt(leftEnergy * rightEnergy)
            correlations[lag] = if (pairCount >= maxOf(16, validCount / 3) && norm > 1e-12) numerator / norm else 0.0
        }
        val best = (3 until maxLag).asSequence()
            .filter { lag -> correlations[lag] >= correlations[lag - 1] && correlations[lag] >= correlations[lag + 1] }
            .maxByOrNull { correlations[it] } ?: return null
        val correlation = correlations[best].coerceIn(-1.0, 1.0)
        return (best to correlation).takeIf { correlation >= 0.35 }
    }

    private fun ppgEvidenceScore(
        raw: List<Float>,
        cleaned: List<Float>,
        qualityScore: Int,
        sampleRateHz: Float,
        timestampSource: String,
        channelMappingVerified: Boolean
    ): Int? {
        if (cleaned.size < 64 || raw.isEmpty()) return null
        val timeEvidence = when {
            timestampSource == "android_sensor_event_elapsed_realtime" -> 80
            sampleRateHz.isFinite() && sampleRateHz > 0f -> 35
            else -> 0
        }
        return ResearchQualityIndexCalculator.calculate(
            finiteRatio = cleaned.size.toDouble() / raw.size,
            sampleCount = cleaned.size,
            waveformStability = qualityScore,
            sourceEvidence = if (channelMappingVerified) 85 else 35,
            timeEvidence = timeEvidence
        ).score
    }

    private fun researchPpgIndexDomainQuality(
        raw: List<Float>,
        cleaned: List<Float>,
        span: Float,
        indexPeriod: Pair<Int, Double>?
    ): Int {
        val baseScore = researchOpticalQuality(raw, cleaned, span)
        val periodicity = indexPeriod?.second?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
        return (baseScore * periodicity).roundToInt().coerceIn(0, 99)
    }

    internal fun researchOpticalQuality(raw: List<Float>, cleaned: List<Float>, span: Float): Int {
        if (raw.isEmpty() || cleaned.isEmpty() || !span.isFinite() || span <= 0f) return 0
        val finiteRatio = (cleaned.size.toDouble() / raw.size).coerceIn(0.0, 1.0)
        val amplitudeEvidence = (span / 24f)
            .toDouble().coerceIn(0.0, 1.0)
        var adjacentValidPairs = 0
        var changingPairs = 0
        raw.zipWithNext().forEach { (left, right) ->
            if (left.isFinite() && right.isFinite() && left != PPG_FILL && right != PPG_FILL) {
                adjacentValidPairs++
                if (kotlin.math.abs(right.toDouble() - left.toDouble()) > span.toDouble() * 0.001) {
                    changingPairs++
                }
            }
        }
        val variationCoverage = if (adjacentValidPairs == 0) 0.0 else
            changingPairs.toDouble() / adjacentValidPairs
        return ((finiteRatio * 70.0 + amplitudeEvidence * 30.0) * variationCoverage)
            .roundToInt().coerceIn(0, 99)
    }

    private fun ppgQualityPreservingGaps(
        raw: List<Float>,
        sampleRateHz: Float
    ): SensorHealthAlgorithms.WaveformQuality {
        val segments = mutableListOf<List<Float>>()
        var current = mutableListOf<Float>()
        raw.forEach { value ->
            if (value.isFinite() && value != PPG_FILL) {
                current += value
            } else if (current.isNotEmpty()) {
                if (current.size >= 8) segments += current
                current = mutableListOf()
            }
        }
        if (current.size >= 8) segments += current
        if (segments.isEmpty()) {
            return SensorHealthAlgorithms.WaveformQuality(
                score = 0,
                level = "未評分",
                metrics = "缺口分段後沒有至少 8 點的連續片段。",
                snrDb = null,
                rrIntervalsMs = emptyList()
            )
        }
        val evaluated = segments.map { segment ->
            val filtered = removeMovingAverage(segment, (sampleRateHz / 2f).toInt().coerceIn(3, 25))
            segment.size to SensorHealthAlgorithms.waveformQuality(filtered, sampleRateHz.coerceAtLeast(1f), "ppg")
        }
        val sampleCount = evaluated.sumOf { it.first }
        val score = evaluated.sumOf { (count, quality) -> count * quality.score }.toDouble()
            .div(sampleCount).toInt().coerceIn(0, 100)
        return SensorHealthAlgorithms.WaveformQuality(
            score = score,
            level = "連續片段研究品質",
            metrics = "分段評估 ${evaluated.size} 段、$sampleCount 點；缺口不跨段計分。",
            snrDb = null,
            rrIntervalsMs = emptyList()
        )
    }

    fun calibrateEcg(
        raw: List<Float>,
        sampleRateHz: Float,
        clockEvidence: ClockEvidence? = null,
        waveformMappingVerified: Boolean = false,
        wearQualified: Boolean = false,
        calibration: EcgCalibrationProfile? = null,
        waveformEncoding: String? = null,
        sensorProfileFingerprint: String? = null
    ): EcgResult {
        val cleaned = raw.filter { it.isFinite() }
        if (cleaned.size < 32) {
            return EcgResult(
                raw.toList(), 0, null, 0, null,
                listOf(ClinicalReferenceTables.ecgRateCompare(sampleRateHz.takeIf { it.isFinite() && it > 0f })),
                "ECG 樣本不足；請以兩指穩壓電極後重測", false
            )
        }
        val baseline = cleaned.average().toFloat()
        val centered = cleaned.map { it - baseline }
        val span = (centered.max() - centered.min()).toDouble().coerceAtLeast(1e-6)
        val scaled = centered.map { (it / span.toFloat()) * 1000f }
        val fs = sampleRateHz.takeIf { it.isFinite() && it > 0f }
        val evidence = clockEvidence ?: ClockEvidence(
            sourceTimestampsNanos = emptyList(),
            callbackTimestampsNanos = emptyList(),
            nominalSampleRateHz = fs?.toDouble(),
            sampleRateSource = null,
            eventOrderingVerified = false,
            samplesPerEventKnown = false,
            sessionOrderingVerified = false,
            sourceClockVerified = false
        )
        if (fs == null) {
            val core = EcgCoreResearchEngine.analyze(
                EcgCoreInput(raw.map(Float::toDouble), evidence, waveformMappingVerified, 0, wearQualified,
                    waveformEncoding, sensorProfileFingerprint),
                calibration
            )
            return EcgResult(
                waveform = core.relativeWaveform.map { it.toFloat() },
                contactScore = core.qualityScore,
                heartRateBpm = null,
                rrCount = 0,
                rrCv = null,
                comparisons = listOf(
                    "ECG 核心引擎已保留並正規化 HAL 原始波形。",
                    "品質未評分：取樣率未知；保留波形與候選索引，但不輸出品質信心數字。",
                    "已改用 NORMALIZED_INDEX 索引時間軸供研究波形顯示；不得視為感測器時鐘。",
                    "因缺少可信取樣時鐘，RR、心率與 HRV 計算已停用。",
                    "相對縮放值無物理單位，不代表 mV 或臨床校準。"
                ),
                summary = "ECG 研究波形 · NORMALIZED_INDEX 索引時間軸 · 不計算 RR／心率／HRV",
                calibrated = core.researchMv != null,
                rPeakIndices = core.rPeakIndices,
                rPeakIndexDistancesSamples = core.rPeakIndexDistancesSamples,
                timelineSource = core.timeline.name,
                researchMvWaveform = core.researchMv?.map(Double::toFloat),
                rhythmPattern = EcgRhythmPatternEngine.assess(emptyList())
            )
        }
        val quality = if (raw.all(Float::isFinite)) {
            SensorHealthAlgorithms.waveformQuality(scaled, fs, "ecg")
        } else {
            SensorHealthAlgorithms.WaveformQuality(
                score = 0,
                level = "未評分",
                metrics = "序列含計數器缺口；不將壓縮後有限樣本當作完整波形評分。",
                snrDb = null,
                rrIntervalsMs = emptyList()
            )
        }
        val carrierHz = repetitiveCarrierHz(centered, fs)
        val core = EcgCoreResearchEngine.analyze(
            EcgCoreInput(
                rawValues = raw.map(Float::toDouble),
                clock = evidence,
                waveformMappingVerified = waveformMappingVerified,
                qualityScore = quality.score,
                wearQualified = wearQualified,
                waveformEncoding = waveformEncoding,
                sensorProfileFingerprint = sensorProfileFingerprint
            ),
            calibration
        )
        val rr = if (carrierHz == null) core.rrIntervalsMs else emptyList()
        val hr = stableRateFromRr(rr)
        val cv = rrCoefficientOfVariation(rr)
        val rhythmPattern = EcgRhythmPatternEngine.assess(rr)
        val contact = quality.score.coerceIn(0, 100)
        val calibrated = core.researchMv != null
        val comparisons = buildList {
            add(ClinicalReferenceTables.ecgRateCompare(fs))
            add("ECG 品質分數只描述此段波形品質；不代表 AF 分類準確率或診斷信心")
            add(
                "Orphanidou SQI（IEEE JBHI 2015）：ECG 模板相關門檻 " +
                    "${ClinicalReferenceTables.ECG_SQI_CORR_THRESHOLD}"
            )
            add("IEC 60601-2-47／AAMI：動態心電常需 ≥500 samples/s；腕式單導無臨床導聯／增益驗證")
            if ("NONFINITE_SAMPLE_GAP" in core.reasonCodes) {
                add("ECG 序列含非有限缺樣；研究波形改用索引時間軸，停用 R 峰、RR、心率與 mV 換算")
            }
            carrierHz?.let { add("偵測到約 ${"%.1f".format(it)} Hz 高重複樣式；依品質保護封鎖 RR／心率") }
            add("核心時間軸：${core.timeline}；R 峰候選 ${core.rPeakIndices.size} 個索引（索引不等於時間）")
            if (core.rPeakIndexDistancesSamples.isNotEmpty()) {
                add("相鄰 R 峰索引間距：${core.rPeakIndexDistancesSamples.joinToString(", ")} samples（非毫秒；不換算 bpm）")
            }
            if (rr.isEmpty()) add("核心品質／時鐘／波形映射閘門未通過；不輸出 RR 或心率")
            add("RR 型態研究描述：${rhythmPattern.title} · ${rhythmPattern.rrCount} 段 · ${rhythmPattern.method}；不能用於 AF／疾病診斷")
            if (hr != null) add(ClinicalReferenceTables.hrVsResting(hr))
            else if (rr.isNotEmpty()) add("已發布研究 RR ${rr.size} 段，估心率尚不穩定")
            else add("估心率／RR：峰偵測不足")
            cv?.let { add("RR 變異係數 ${"%.3f".format(it)}（研究用，非心律診斷）") }
        }
        return EcgResult(
            waveform = if (raw.all(Float::isFinite)) scaled else core.relativeWaveform.map(Double::toFloat),
            contactScore = contact,
            heartRateBpm = hr,
            rrCount = rr.size,
            rrCv = cv,
            comparisons = comparisons,
            summary = buildString {
                append("ECG 研究訊號 · 品質 ${ResearchScoreDisplay.format(contact)}")
                hr?.let { append(" · 估心率 ${"%.0f".format(it)} bpm") }
                append(" · RR ${rr.size}")
                if (carrierHz != null) append(" · 載波警示")
                append(if (calibrated) {
                    " · 品質分數非 AF 準確率 · 已套用外部配對研究 mV 映射（非 OEM／臨床校準）"
                } else {
                    " · 品質分數非 AF 準確率 · 已去基線並相對縮放（無導聯／臨床 mV）"
                })
            },
            calibrated = calibrated,
            rPeakIndices = core.rPeakIndices,
            rPeakIndexDistancesSamples = core.rPeakIndexDistancesSamples,
            timelineSource = core.timeline.name,
            researchMvWaveform = core.researchMv?.map(Double::toFloat),
            rhythmPattern = rhythmPattern
    )

}

    fun repetitiveCarrierHz(values: List<Float>, sampleRateHz: Float): Double? {
        if (values.size < 256 || !sampleRateHz.isFinite() || sampleRateHz < 20f) return null
        val mean = values.map(Float::toDouble).average()
        val minLag = (sampleRateHz / 40f).toInt().coerceAtLeast(3)
        val maxLag = (sampleRateHz / 4f).toInt().coerceAtMost(values.size / 4)
        var bestCorrelation = -1.0
        var bestLag = 0
        for (lag in minLag..maxLag) {
            var numerator = 0.0
            var leftPower = 0.0
            var rightPower = 0.0
            for (index in 0 until values.size - lag) {
                val left = values[index] - mean
                val right = values[index + lag] - mean
                numerator += left * right
                leftPower += left * left
                rightPower += right * right
            }
            val denominator = kotlin.math.sqrt(leftPower * rightPower)
            val correlation = if (denominator > 1e-12) numerator / denominator else 0.0
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }
        val frequency = if (bestLag > 0) sampleRateHz / bestLag else 0f
        return frequency.toDouble().takeIf { bestCorrelation >= 0.98 && it >= 4.0 }
    }

    fun calibrateBioz(latest: List<Double>, waveform: List<Float>): BiozResult {
        val cleanedWave = waveform.filter(Float::isFinite)
        val rawChannels = latest.filter(Double::isFinite)
        val span = if (cleanedWave.size >= 2) cleanedWave.max().toDouble() - cleanedWave.min().toDouble() else 0.0
        val median = cleanedWave.sorted().let { values ->
            if (values.isEmpty()) null
            else if (values.size % 2 == 1) values[values.size / 2].toDouble()
            else values[values.size / 2 - 1].toDouble() / 2.0 + values[values.size / 2].toDouble() / 2.0
        }
        val waveformMedianPositionIndex = median?.takeIf { it.isFinite() && span.isFinite() && span > 0.0 }
            ?.let { ((it - cleanedWave.min().toDouble()) / span * 100.0).roundToInt().coerceIn(0, 100) }
        val mean = cleanedWave.takeIf { it.isNotEmpty() }?.average()
        val changeThreshold = span * 0.001
        val validAdjacentPairs = waveform.zipWithNext().filter { (left, right) -> left.isFinite() && right.isFinite() }
        val changingPairRatio = if (validAdjacentPairs.isEmpty()) 0.0 else validAdjacentPairs.count { (left, right) ->
            abs(right.toDouble() - left.toDouble()) > changeThreshold
        }.toDouble() / validAdjacentPairs.size
        val deviation = mean?.takeIf { cleanedWave.size >= 16 && span.isFinite() && span > 0.0 }?.let { average ->
            sqrt(cleanedWave.sumOf { (it - average) * (it - average) } / cleanedWave.size) / span.toDouble()
        }
        val stability = deviation?.takeIf(Double::isFinite)?.let { (it * 100.0).roundToInt() }
        val quartileSize = (waveform.size / 4).coerceAtLeast(1)
        val firstQuartile = waveform.take(quartileSize).filter(Float::isFinite)
        val lastQuartile = waveform.takeLast(quartileSize).filter(Float::isFinite)
        val relativeChange = if (firstQuartile.size >= 4 && lastQuartile.size >= 4 && span.isFinite() && span > 0.0) {
            ((lastQuartile.average() - firstQuartile.average()) / span) * 100.0
        } else null
        val packetEvidenceScore = when {
            cleanedWave.size >= 1024 && span > 0.0 -> 92
            cleanedWave.size >= 256 && span > 0.0 -> 85
            cleanedWave.size >= 64 && span > 0.0 -> 72
            cleanedWave.size >= 16 -> 45
            else -> 0
        }
        val waveformEvidenceScore = (packetEvidenceScore * (changingPairRatio * 4.0).coerceIn(0.0, 1.0))
            .roundToInt()
        val confidenceScore = if (cleanedWave.size >= 64 && waveform.isNotEmpty()) {
            ResearchQualityIndexCalculator.calculate(
                finiteRatio = cleanedWave.size.toDouble() / waveform.size,
                sampleCount = cleanedWave.size,
                waveformStability = null,
                sourceEvidence = 35,
                timeEvidence = 0
            ).score
        } else null
        val contact = waveformEvidenceScore
        val calibrated = false
        val mode = ClinicalReferenceTables.BiozMode.UNKNOWN
        return BiozResult(
            resistanceOhm = null,
            reactanceOhm = null,
            impedanceOhm = null,
            phaseDeg = null,
            hydrationIndex = null,
            waveformMedianPositionIndex = waveformMedianPositionIndex,
            relativeChangePercent = relativeChange,
            stabilityScore = stability,
            confidenceScore = confidenceScore,
            mode = mode,
            contactScore = contact,
            waveform = cleanedWave,
            comparisons = buildList {
                add("HAL 未提供經驗證 Ω／激勵電流／電極模型；不可換算體脂或體水％")
                addAll(ClinicalReferenceTables.biozCompare(null, null, null))
                add("研究波形證據分數計入相鄰樣本變化覆蓋率；平線／稀疏尖峰不視為高品質或接觸良好")
                add("相鄰變化覆蓋率：${"%.1f".format(changingPairRatio * 100.0)}%（描述性特徵，非校準或準確率）")
                add("相鄰配對依原始索引，只統計兩側皆有效的連續樣本：${validAdjacentPairs.size} 對／${(waveform.size - 1).coerceAtLeast(0)} 個可能相鄰位置")
                add("時間證據：未提供；此資料完整度指數不代表任何物理時間尺度")
                add("單次波形中位位置：${waveformMedianPositionIndex ?: "未計算"}（視窗內相對位置，非阻抗值或跨次趨勢）")
                add("波形相對離散度：${stability?.let { "$it%（標準差／波形全幅）" } ?: "未評分（樣本不足或無波形變化）"}；不是時間穩定度、接觸品質或生理指標")
                add("研究資料完整度指數：${ResearchScoreDisplay.format(confidenceScore)}（計入樣本量、有限值比例、波形變化、來源與時間證據；非準確率／概率）")
            },
            summary = buildString {
                append("BIOZ 研究訊號 · 波形證據 ${ResearchScoreDisplay.format(contact)} · ${rawChannels.size} 通道 · ${cleanedWave.size} 點")
                append(" · 首尾區段相對差 ${relativeChange?.let { "%+.2f%%".format(it) } ?: "未評分"}（非跨次趨勢）")
                append(" · 波形中位位置 ${waveformMedianPositionIndex ?: "未計算"}（非阻抗）")
                append(" · 單位未驗證，不輸出 Ω／體組成分")
            },
            calibrated = calibrated,
            rawChannelValues = latest.take(BiozRawChannelEvidence.MAX_CHANNELS),
            displayWaveform = waveform.map { value -> value.takeIf(Float::isFinite) ?: Float.NaN }
        )
    }

    /** Preserve the observed rate instead of inventing an ADC sampling frequency. */
    fun sanitizePpgSampleRate(reported: Float, sampleCount: Int): Float {
        return reported.takeIf { it.isFinite() && it > 0f } ?: 0f
    }

    fun ppgMeasurementStatus(result: PpgResult): String = when {
        result.valid -> "research_optical"
        result.waveform.isNotEmpty() && !result.rateEligible -> "research_signal_unscaled"
        else -> "invalid_optical"
    }

    fun stableRateFromRr(rrMs: List<Double>): Double? {
        if (rrMs.size < 3) return null
        val sorted = rrMs.sorted()
        val median = sorted[sorted.size / 2]
        val filtered = rrMs.filter { abs(it - median) <= median * 0.30 }
        if (filtered.size < 3) return null
        val bpm = 60000.0 / filtered.average()
        return bpm.takeIf { ClinicalReferenceTables.PLAUSIBLE_HR_BPM.contains(it) }
    }

    fun rrCoefficientOfVariation(rrMs: List<Double>): Double? {
        if (rrMs.size < 3) return null
        val mean = rrMs.average()
        if (mean <= 1e-6) return null
        val variance = rrMs.sumOf { (it - mean) * (it - mean) } / rrMs.size
        return sqrt(variance) / mean
    }

    private fun removeMovingAverage(values: List<Float>, window: Int): List<Float> {
        if (values.isEmpty() || window <= 1) return values
        val half = window / 2
        return values.indices.map { index ->
            val from = (index - half).coerceAtLeast(0)
            val to = (index + half).coerceAtMost(values.lastIndex)
            val mean = values.subList(from, to + 1).average().toFloat()
            values[index] - mean
        }
    }
}
