package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine

/**
 * High-quality reference verdicts for wearable signals.
 * Always non-diagnostic; compares readings to [ClinicalReferenceTables].
 */
object ClinicalVerdict {
    const val DISCLAIMER =
        "本程式提供參考，非醫療用途。結果不能作為診斷、治療、急症判斷或用藥依據；如有不適請就醫。"

    data class Result(
        val modality: String,
        val title: String,
        val grade: String,
        val score: Int?,
        val headline: String,
        val findings: List<String>,
        val recommendation: String,
        val clinicalComparisons: List<String> = emptyList(),
        val disclaimer: String = DISCLAIMER
    ) {
        val qualityScoreAvailable: Boolean
            get() = score?.let { it in 1..100 } == true && grade !in setOf("研究資料", "研究原始資料", "—", "無資料", "未校準")
    }

    private fun displayedScore(score: Int?): String = ResearchScoreDisplay.format(score)

    internal fun isEcgEstimateUsable(
        qualityScore: Int,
        sampleCount: Int,
        sampleRate: Float,
        status: String,
        rrCount: Int,
        wearQualified: Boolean?,
        waveformEncoding: String? = null,
        timelineSource: String = "NORMALIZED_INDEX"
    ): Boolean = qualityScore >= 50 && sampleCount >= 32 &&
        rrCount >= EcgCoreResearchEngine.MIN_RR_INTERVALS_FOR_RESEARCH_HR &&
        sampleRate.isFinite() && sampleRate > 0f && wearQualified == true &&
        timelineSource == "SENSOR" &&
        status in setOf("research_signal_unscaled", "calibrated_external_reference", "has_data") &&
        EcgPayloadInterpretationPolicy.canInterpretAsWaveform(waveformEncoding)

    internal fun isEcgPulseDisplayable(
        qualityScore: Int,
        sampleCount: Int,
        sampleRate: Float,
        status: String,
        rrCount: Int,
        wearQualified: Boolean?,
        heartRateBpm: Double?,
        waveformEncoding: String? = null,
        timelineSource: String = "NORMALIZED_INDEX"
    ): Boolean = heartRateBpm?.let { it.isFinite() && it in 30.0..220.0 } == true &&
        isEcgEstimateUsable(qualityScore, sampleCount, sampleRate, status, rrCount, wearQualified, waveformEncoding, timelineSource)

    fun forOpticalAdc(
        sampleCount: Int,
        rawAverage: Double?,
        estimateBpm: Double?,
        status: String,
        wearQualified: Boolean?
    ): Result = Result(
        modality = "optical_adc",
        title = "光學原始通道",
        grade = "研究原始資料",
        score = null,
        headline = if (estimateBpm != null) "研究估算脈率 ${"%.0f".format(estimateBpm)} bpm；非原始感測器直出、未經裝置臨床驗證"
        else "已收到光學原始封包；原始碼值不是 bpm",
        findings = buildList {
            add("原始樣本 $sampleCount 點；通道單位／封包映射未公開")
            rawAverage?.takeIf { it.isFinite() }?.let { add("封包平均碼值 ${"%.1f".format(it)}（不可解讀為生理數值）") }
            add("來源狀態：$status")
            add(wearLine(wearQualified))
        },
        recommendation = "只檢視原始波形。沒有裝置級解碼、取樣時鐘與品質驗證時，不輸出心率、HRV 或疾病判讀。",
        clinicalComparisons = emptyList()
    )

    fun forPpg(
        qualityScore: Int,
        sampleCount: Int,
        sampleRate: Float,
        status: String,
        wearQualified: Boolean?,
        pulseRateBpm: Double? = null,
        comparisons: List<String> = emptyList(),
        hasReceivedSignal: Boolean = sampleCount > 0,
        healthServicesHeartRateBpm: Double? = null,
        confidenceScore: Int? = null,
        source: String = "PPG_RAW_OR_RESEARCH",
        timestampSource: String = "UNKNOWN",
        calibrationStatus: String = "UNVERIFIED",
        indexPeriodSamples: Int? = null,
        periodCorrelation: Double? = null,
        relativeAcDcRatio: Double? = null
    ): Result {
        val fs = BiomedicalCalibration.sanitizePpgSampleRate(sampleRate, sampleCount)
        val rawSignalPresent = hasReceivedSignal && status in setOf(
            "has_data", "valid_signal", "research_optical", "research_signal_unscaled", "unscaled_optical", "invalid_optical"
        )
        val researchWaveformUsable = status == "research_optical" && fs >= 20f &&
            wearQualified == true && qualityScore >= 50 && sampleCount >= 64
        val researchPulseUsable = researchWaveformUsable && pulseRateBpm?.let {
            it.isFinite() && it >= ClinicalReferenceTables.PLAUSIBLE_HR_BPM.min &&
                it <= ClinicalReferenceTables.PLAUSIBLE_HR_BPM.max
        } == true
        val valid = status == "valid_signal" && fs >= 20f && wearQualified == true &&
            qualityScore >= 50 && sampleCount >= 64
        val displayedScore = qualityScore.coerceIn(0, 100).takeIf { valid }
        val grade = displayedScore?.let { gradeOf(it, true) } ?: "—"
        val comps = comparisons.ifEmpty {
            buildList {
                add(ClinicalReferenceTables.ppgRateCompare(fs.takeIf { it > 0f }))
                if ((valid || researchPulseUsable) && pulseRateBpm != null) add(ClinicalReferenceTables.hrVsResting(pulseRateBpm))
            }
        }
        return Result(
            modality = "ppg",
            title = "PPG 光學脈搏",
            grade = grade,
            score = qualityScore.takeIf { rawSignalPresent && sampleCount > 0 && it in 1..100 },
            headline = when {
                wearQualified == false -> "佩戴未貼合，光學訊號不宜作為參考判定"
                researchPulseUsable -> "PPG 研究估算脈率 ${"%.0f".format(pulseRateBpm)} bpm（非醫療校準）"
                researchWaveformUsable -> "已收到可供工程檢視的 PPG 波形；未校準，不換算脈率／HRV／血氧"
                valid && qualityScore >= 75 -> "光學脈搏訊號品質良好，可供參考觀察"
                valid && qualityScore >= 50 -> "光學脈搏訊號可用，仍受動作與光線影響"
                rawSignalPresent -> "已取得光學訊號，尚無可解讀的脈率"
                else -> "光學脈搏訊號偏弱，請重新貼合量測"
            },
            findings = buildList {
                add(if (fs > 0f) "樣本 $sampleCount 點 · 取樣率 ${"%.1f".format(fs)} Hz"
                    else "樣本 $sampleCount 點 · 取樣率未知；可查看索引域原始波形")
                add(displayedScore?.let { "PPG 波形指標 $it/100 · 分級 $grade（工程品質分數，非臨床有效度）" }
                    ?: qualityScore.takeIf { rawSignalPresent && sampleCount > 0 && it in 1..100 }
                        ?.let { "PPG 波形指標 $it/100（依波形振幅、變化與完整度；不代表生理值、臨床品質或診斷準確度）" }
                    ?: "品質分數：未評分（目前狀態／取樣條件不允許評分）")
                add("動作干擾：未評分；目前沒有與 PPG 時間同步的加速度／陀螺儀樣本，未知不代表 0")
                add("狀態：$status")
                add("研究證據信心指數：${confidenceScore?.takeIf { it in 1..100 }?.let(::displayedScore) ?: "未評分"}（資料完整度指標，非準確率／概率）· 來源：$source · 時間：$timestampSource · 校準：$calibrationStatus")
                if (indexPeriodSamples != null && indexPeriodSamples > 0 && periodCorrelation?.isFinite() == true) {
                    add("索引域重複週期：$indexPeriodSamples 點／週期；最大自相關 ${"%.2f".format(periodCorrelation)}（非秒、非脈率）")
                }
                relativeAcDcRatio?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                    add("相對 AC/DC ${"%.4f".format(it)}（無單位研究描述指標；不等於灌流指數或生理值）")
                }
                if (researchPulseUsable) pulseRateBpm?.let { add("估脈率 ${"%.0f".format(it)} bpm（研究估計；非醫療校準）") }
                else if (valid) pulseRateBpm?.let { add("估脈率 ${"%.0f".format(it)} bpm（研究估計）") }
                else if (pulseRateBpm != null) add("已忽略脈率欄位：目前訊號狀態／取樣條件不允許採用")
                healthServicesHeartRateBpm?.takeIf { it.isFinite() && it in 30.0..220.0 }?.let {
                    add("Health Services 心率 ${"%.0f".format(it)} bpm（獨立來源；不是由 PPG 原始波形推導）")
                }
                add(wearLine(wearQualified))
            },
            recommendation = if (researchPulseUsable) "此脈率是依研究演算法估算；不等同醫療量測，也不可用於疾病診斷。"
            else if (valid) "可作為日常脈搏波形參考；若胸悶、暈厥或持續心悸請就醫。"
            else if (researchWaveformUsable || rawSignalPresent) "可查看光學訊號；若想知道心率，請使用手錶心率量測。"
            else "請將錶帶貼合、手腕靜止、避開強光後重測。",
            clinicalComparisons = comps
        )
    }

    fun forEcg(
        qualityScore: Int,
        sampleCount: Int,
        sampleRate: Float,
        status: String,
        heartRateBpm: Double?,
        rrCount: Int,
        wearQualified: Boolean?,
        comparisons: List<String> = emptyList(),
        waveformEncoding: String? = null,
        timelineSource: String = "NORMALIZED_INDEX",
        rPeakIndices: List<Int> = emptyList(),
        rPeakIndexDistancesSamples: List<Int> = emptyList(),
        externalCalibrationVerified: Boolean = false,
        rhythmPatternTitle: String? = null
    ): Result {
        val hasRr = rrCount > 0
        val externallyCalibrated = externalCalibrationVerified &&
            status == "calibrated_external_reference" && waveformEncoding == "ecg_external_reference_mv"
        val waveformVerified = EcgPayloadInterpretationPolicy.canInterpretAsWaveform(waveformEncoding)
        val researchUsable = wearQualified == true && sampleCount >= 32 &&
            status in setOf("research_signal_unscaled", "calibrated_external_reference", "has_data") &&
            (waveformVerified && sampleRate.isFinite() && sampleRate > 0f ||
                status == "research_signal_unscaled" || externallyCalibrated &&
                EcgPayloadInterpretationPolicy.canInterpretAsResearchWaveform(waveformEncoding))
        val estimateUsable = isEcgPulseDisplayable(
            qualityScore, sampleCount, sampleRate, status, rrCount, wearQualified, heartRateBpm, waveformEncoding, timelineSource
        )
        val scoreForDisplay = qualityScore.coerceIn(0, 100)
        val scoreAvailable = scoreForDisplay in 1..100
        val grade = if (researchUsable && scoreAvailable) gradeOf(scoreForDisplay, true) else "—"
        val comps = comparisons.ifEmpty {
            listOf("僅保留 HAL 相對事件時間軸；尚無原廠導聯、增益與 ADC→mV 校準")
        }
        return Result(
            modality = "ecg",
            title = "ECG 心電參考",
            grade = grade,
            score = scoreForDisplay.takeIf { researchUsable && scoreAvailable },
            headline = when {
                estimateUsable && hasRr && heartRateBpm != null -> "已開放研究 RR · 估心率 ${"%.0f".format(heartRateBpm)} bpm"
                !researchUsable -> "尚無可判讀的有效 ECG 訊號"
                !scoreAvailable -> "已收到 ECG 波形；品質未評分，暫不輸出心率"
                qualityScore < 50 -> "已收到 ECG 波形；品質偏低，隱藏心率估算"
                researchUsable && hasRr -> "已開放研究 RR（$rrCount 段）"
                externallyCalibrated -> "已套用外部線性參考的研究波形（非小米校準／非臨床驗證）"
                researchUsable && scoreForDisplay >= 70 -> "已取得研究用原始 ECG 波形（尚未 mV／導聯校準）"
                sampleCount >= 32 -> "研究波形可讀；RR 待更多峰值"
                else -> "ECG 樣本尚少，請繼續接觸電極"
            },
            findings = buildList {
                add("研究估算：ECG 原始波形／事件分析")
                add(if (externallyCalibrated) {
                    "顯示尺度來源：外部參考配對研究 profile；非小米／醫療校準"
                } else {
                    "ECG mV 校準：未建立；MAX86178 公開規格僅供晶片參考，非手錶 raw→mV 校準"
                })
                add("信心：${if (researchUsable && scoreAvailable) displayedScore(scoreForDisplay) else "未知"} · 原始／推估來源：原始 HAL 事件＋研究演算法")
                add("樣本 $sampleCount 點 · ${when (timelineSource) {
                    "SENSOR" -> "SENSOR 事件時間軸 · HAL 回報 ${"%.1f".format(sampleRate)} 點/秒（取樣率未經裝置校準）"
                    "ESTIMATED" -> "ESTIMATED_TIMELINE · ${"%.1f".format(sampleRate)} 點/秒（估計／回報，不等同感測器時鐘）"
                    else -> "NORMALIZED_INDEX · 相對點位（無物理時間尺度）"
                }}")
                add(if (rPeakIndices.isEmpty()) "R 峰候選索引：未檢出" else
                    "R 峰候選索引（研究用；非診斷）：${rPeakIndices.take(32).joinToString(", ")}" +
                        if (rPeakIndices.size > 32) " … 共 ${rPeakIndices.size} 個" else "")
                if (rPeakIndexDistancesSamples.isNotEmpty() && timelineSource == "NORMALIZED_INDEX") {
                    add("相鄰 R 峰索引間距（samples；非 ms）：${rPeakIndexDistancesSamples.take(32).joinToString(", ")}" +
                        if (rPeakIndexDistancesSamples.size > 32) " … 共 ${rPeakIndexDistancesSamples.size} 段" else "")
                }
                rhythmPatternTitle?.takeIf(String::isNotBlank)?.let { add("RR 型態研究描述：$it；不代表 AF 或其他疾病判定") }
                add(if (researchUsable && scoreAvailable) "研究訊號品質 ${displayedScore(scoreForDisplay)} · $grade（不代表診斷準確度）"
                    else "品質分數：未評分（狀態／品質證據不足）")
                add(
                    when {
                        estimateUsable && heartRateBpm != null &&
                            rrCount >= EcgCoreResearchEngine.MIN_RR_INTERVALS_FOR_RESEARCH_HR ->
                            "研究 RR $rrCount 段 · 估心率 ${"%.1f".format(heartRateBpm)} bpm（已開放，不可作 AF 判定）"
                        researchUsable && !scoreAvailable -> "品質未評分；保留波形與事件，不輸出心率"
                        researchUsable && qualityScore < 50 -> "ECG 品質 ${displayedScore(scoreForDisplay)} 未達研究估算門檻；保留原始波形，不輸出心率"
                        researchUsable && rrCount > 0 -> "研究 RR $rrCount 段已開放；估心率尚不穩定"
                        rrCount > 0 && status == "research_signal_unscaled" -> "研究 RR 欄位已保留，但取樣時基未知；不換算心率"
                        rrCount > 0 -> "已忽略舊 RR 欄位：目前狀態／取樣時基不允許採用"
                        else -> "尚未偵測到 R 峰；請保持雙指接觸後重測"
                    }
                )
                add(wearLine(wearQualified))
                add(if (externallyCalibrated) "外部線性擬合只轉換顯示尺度；不驗證導聯、頻率響應或生理準確度，也不解鎖診斷" else "缺少外部 raw→mV、原廠導聯與增益校準")
            },
            recommendation = "此為腕式原始電位參考圖，不能診斷心律不整、缺血或心肌梗塞。",
            clinicalComparisons = comps
        )
    }

    fun forBioz(
        qualityScore: Int,
        impedanceOhm: Double?,
        resistanceOhm: Double?,
        reactanceOhm: Double?,
        hydrationIndex: Int?,
        status: String,
        comparisons: List<String> = emptyList(),
        rawSampleCount: Int = 0,
        hasReceivedSignal: Boolean = rawSampleCount > 0,
        waveformMedianPositionIndex: Int? = null,
        relativeChangePercent: Double? = null,
        stabilityScore: Int? = null,
        confidenceScore: Int? = null,
        timestampSource: String? = null,
        source: String = "UNKNOWN",
        measuredAtMillis: Long = 0L,
        rawChannelValues: List<Pair<Int, Double?>> = emptyList()
    ): Result {
        val mode = ClinicalReferenceTables.BiozMode.UNKNOWN
        val ok = false
        val rawSignalPresent = hasReceivedSignal
        val grade = if (rawSignalPresent) "研究資料" else "無資料"
        val comps = (comparisons + "HAL 原始欄位尚未驗證 Ω 單位與電極模型；不做阻抗或體水判讀").distinct()
        return Result(
            modality = "bioz",
            title = "BIOZ 阻抗參考",
            grade = grade,
            score = qualityScore.takeIf { rawSignalPresent && rawSampleCount > 0 && it in 1..100 },
            headline = when (mode) {
                ClinicalReferenceTables.BiozMode.SEGMENTAL_WRIST ->
                    if (ok) "已取得腕部局部阻抗（非全身體組成）" else "阻抗訊號不足"
                ClinicalReferenceTables.BiozMode.WHOLE_BODY_LIKE ->
                    if (ok) "已取得接近全身量級之阻抗參考（仍缺 BIVA 正規化）" else "阻抗訊號不足"
                ClinicalReferenceTables.BiozMode.UNKNOWN ->
                    if (ok) "已取得阻抗讀值（量級未歸類）"
                    else if (rawSignalPresent) "BioZ 原始波形參考"
                    else "尚未收到 BioZ 原始訊號"
            },
            findings = buildList {
                add(if (waveformMedianPositionIndex != null || relativeChangePercent != null || stabilityScore != null)
                    "研究估算：BioZ 原始索引序列描述分析"
                    else "研究資料：BioZ 原始封包欄位；欄位到時間樣本映射未驗證")
                if (waveformMedianPositionIndex != null || relativeChangePercent != null || stabilityScore != null) {
                    add("演算法：${BiomedicalCalibration.BIOZ_RESEARCH_VERSION}；僅描述索引域相對位置、變化及離散度")
                } else {
                    add("尚無可驗證的 BioZ 時間序列；保留原始欄位值，不跨欄位計算波形指標")
                }
                add("校準狀態：未校準；MAX86178 晶片規格不是本手錶 raw→Ω 換算")
                add("研究證據信心指數：${displayedScore(confidenceScore)}（資料完整度指標，非準確率／概率）· 原始／推估來源：原始 HAL 事件＋研究演算法")
                add(if (HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(timestampSource) && measuredAtMillis > 0L) {
                    val measuredAt = java.time.Instant.ofEpochMilli(measuredAtMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    val freshness = if (HealthAnalysisFreshnessPolicy.isFreshSensor(
                            measuredAtMillis, timestampSource, System.currentTimeMillis()
                        )) "近期" else "歷史或時間異常"
                    "來源：$source · 感測器時間：$measuredAt（$freshness；$timestampSource；不是取樣時鐘）"
                } else {
                    "來源：$source · 時間軸：NORMALIZED_INDEX；量測時間未確認（${timestampSource ?: "UNKNOWN"}）"
                })
                if (rawSignalPresent) add("原始波形點數 $rawSampleCount；此數值不是阻抗讀值")
                if (rawChannelValues.isNotEmpty()) add(
                    "HAL 原始通道值（裝置碼，單位未驗證）：" + rawChannelValues.joinToString(" · ") { channel ->
                        "通道 ${channel.first}=${channel.second?.let { "%.5f".format(it) } ?: "非有限值"}"
                    }
                )
                waveformMedianPositionIndex?.let { add("單次波形中位位置 $it（視窗內相對位置，非阻抗值或跨次趨勢）") }
                relativeChangePercent?.let { add("首尾四分位區段相對差 ${"%+.2f".format(it)}%（以整段波形跨度正規化；非跨次趨勢）") }
                stabilityScore?.let { add("波形相對離散度 $it%（標準差／波形全幅；非時間穩定度、接觸品質或生理指標）") }
                add("阻抗、相位角與體水：未輸出（裝置級單位尚未驗證）")
                add("原始波形變化覆蓋分數 ${if (rawSignalPresent) displayedScore(qualityScore) else "未評分（未收到訊號）"}（不判定接觸、訊號校準或臨床品質）")
                add("無原廠電極模型與體組成換算公式")
            },
            recommendation = if (rawSignalPresent) "BioZ 原始波形僅供參考；目前無法可靠換算阻抗、體脂或體水數值。"
            else "不可用來判斷脫水、體脂或營養狀態；先確認感測器有回傳原始資料。",
            clinicalComparisons = comps
        )
    }

    fun forBiozRespiration(
        research: BiomedicalCalibration.BiozResult,
        rawSampleCount: Int,
        hasReceivedSignal: Boolean,
        status: String,
        source: String,
        measuredAtMillis: Long,
        timestampSource: String?,
        wearQualified: Boolean?
    ): Result {
        val base = forBioz(
            qualityScore = research.contactScore,
            impedanceOhm = null,
            resistanceOhm = null,
            reactanceOhm = null,
            hydrationIndex = null,
            status = status,
            comparisons = research.comparisons,
            rawSampleCount = rawSampleCount,
            hasReceivedSignal = hasReceivedSignal,
            waveformMedianPositionIndex = research.waveformMedianPositionIndex,
            relativeChangePercent = research.relativeChangePercent,
            stabilityScore = research.stabilityScore,
            confidenceScore = research.confidenceScore,
            timestampSource = timestampSource,
            source = source,
            measuredAtMillis = measuredAtMillis,
            rawChannelValues = emptyList()
        )
        return base.copy(
            modality = "bioz_respiration",
            title = "呼吸研究（BioZ）",
            headline = if (hasReceivedSignal) "BioZ 呼吸研究波形；未輸出呼吸率" else "尚未收到 BioZ 呼吸研究波形",
            findings = listOf(
                "有限原始值 $rawSampleCount；預覽點 ${research.displayWaveform.size}",
                "波形橫軸：NORMALIZED_INDEX；相對振幅，不代表秒或 Ω",
                wearLine(wearQualified)
            ) + base.findings,
            recommendation = "僅供 BioZ 波形研究觀察；目前不推導或診斷呼吸率。"
        )
    }

    fun forRawSpO2(
        sampleCount: Int,
        status: String,
        wearQualified: Boolean?,
        hasReceivedSignal: Boolean = sampleCount > 0,
        researchEvidenceScore: Int? = null,
        source: String = "RAW_OPTICAL_EVENT",
        measuredAtMillis: Long = 0L,
        timestampSource: String? = null,
        calibrationStatus: String = "UNVERIFIED"
    ): Result {
        val rawPresent = hasReceivedSignal && status !in setOf("no_events", "not_measured")
        val timeline = SignalTimelineLabel.evidence(measuredAtMillis, timestampSource)
        val timeLabel = when {
            timeline.source == "ESTIMATED_TIMELINE" && measuredAtMillis <= 0L -> "估算時間：未提供具體時間值"
            measuredAtMillis <= 0L -> "量測時間：未知"
            timeline.source == "SENSOR_TIME" || timeline.source == "MEASUREMENT_TIME" ->
                "量測時間：$measuredAtMillis"
            timeline.source == "ESTIMATED_TIMELINE" -> "估算時間：$measuredAtMillis"
            else -> "時間欄位：$measuredAtMillis（來源未確認，不能確認為量測時間）"
        }
        return Result(
            modality = "oxygen",
            title = "SpO₂ 血氧參考",
            grade = if (rawPresent) "研究資料" else "無資料",
            score = researchEvidenceScore?.coerceIn(0, 100)?.takeIf { rawPresent && it in 1..100 },
            headline = if (rawPresent) "SpO₂ 原始波形參考"
            else "尚未收到 SpO₂ 原始通道資料",
            findings = buildList {
                add("研究描述統計：${ResearchSignalSummary.VERSION} · SpO₂ 原始光學編碼")
                add("資料來源：$source · 時間來源：${timestampSource ?: "UNKNOWN"} · $timeLabel")
                add("時間軸：${timeline.source} · estimated_timeline=${timeline.estimated}")
                add("校準狀態：$calibrationStatus；MAX86178／OEM 換算曲線未提供")
                add("研究資料完整度：${if (rawPresent) displayedScore(researchEvidenceScore) else "未知"}（非訊號品質／準確率）· 原始／推估來源：原始光學事件＋描述統計")
                if (rawPresent) add("原始波形點數 $sampleCount · 狀態 $status；不是血氧百分比")
                if (rawPresent) add("資料完整度指數 ${displayedScore(researchEvidenceScore)}；不代表 SpO₂ 準確度")
                add("原始通道單位／波長組合／OEM 換算曲線未驗證，不輸出 %")
                add(wearLine(wearQualified))
            },
            recommendation = if (rawPresent) "原始曲線僅供參考；血氧數值請使用 Health Connect 的可信百分比或合格指夾血氧計。"
            else "先確認手錶韌體有提供 SpO₂ 原始事件；勿以空值或原始 ADC 計數代替百分比。",
            clinicalComparisons = emptyList()
        )
    }

    fun forTemperature(
        deviceRawValue: Double?,
        sampleCount: Int,
        status: String,
        wearQualified: Boolean?,
        source: String = "UNKNOWN",
        measuredAtMillis: Long = 0L,
        timestampSource: String? = null,
        calibrationStatus: String = "UNVERIFIED"
    ): Result {
        val usable = deviceRawValue != null && deviceRawValue.isFinite()
        val now = System.currentTimeMillis()
        val timeline = SignalTimelineLabel.evidence(measuredAtMillis, timestampSource)
        val timeStatus = when {
            measuredAtMillis <= 0L -> "量測時間缺失"
            measuredAtMillis > now -> "時間戳在未來"
            !HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource) -> "時間來源未確認"
            HealthAnalysisFreshnessPolicy.isFreshMeasurement(
                measuredAtMillis, timestampSource, now, 24 * 60 * 60 * 1000L
            ) -> "近期"
            else -> "歷史／過期"
        }
        val timeLabel = when (timeline.source) {
            "SENSOR_TIME", "MEASUREMENT_TIME" -> "量測時間：$measuredAtMillis"
            "ESTIMATED_TIMELINE" -> if (measuredAtMillis > 0L) {
                "估算時間：$measuredAtMillis"
            } else {
                "估算時間：未提供具體時間值"
            }
            else -> if (measuredAtMillis > 0L) {
                "時間欄位：$measuredAtMillis（來源未確認，不能確認為量測時間）"
            } else {
                "量測時間：未知"
            }
        }
        val hasFreshTrustedTime = HealthAnalysisFreshnessPolicy.isFreshMeasurement(
            measuredAtMillis, timestampSource, now, 24 * 60 * 60 * 1000L
        )
        val evidence = ResearchQualityIndexCalculator.calculate(
            finiteRatio = if (usable) 1.0 else 0.0,
            sampleCount = sampleCount,
            waveformStability = null,
            sourceEvidence = if (status in setOf("has_data", "feature_snapshot", "research_signal_unscaled")) 100 else 40,
            timeEvidence = if (hasFreshTrustedTime) 100 else 0
        )
        val evidenceScore = evidence.score?.takeIf { usable }
        return Result(
            modality = "temperature",
            title = "溫度感測器原始值",
            grade = if (usable) "研究資料" else "無資料",
            score = evidenceScore,
            headline = if (!usable) "尚未取得有效裝置讀值"
                else if (hasFreshTrustedTime) "已收到近期裝置讀值；單位與整機校準未驗證"
                else "已保存原始讀值（$timeStatus）；不是已確認的最新量測",
            findings = buildList {
                deviceRawValue?.takeIf { usable }?.let { add("裝置原始值 ${"%.2f".format(it)}（單位未驗證）") }
                add("樣本 $sampleCount · 狀態 $status")
                add("時間狀態：$timeStatus")
                add("時間軸：${timeline.source} · estimated_timeline=${timeline.estimated}")
                if (usable) add("研究證據完整度 ${displayedScore(evidence.score)}；方法 ${ResearchQualityIndexCalculator.VERSION}，使用有限值比例、樣本量、來源與可信時間證據；不代表溫度準確度")
                add("資料來源：$source · 時間來源：${timestampSource ?: "UNKNOWN"} · $timeLabel")
                add("校準狀態：$calibrationStatus · 單位未驗證")
                add("佩戴狀態：${wearQualified?.let { if (it) "已確認" else "未確認貼合" } ?: "未知"}")
                add("缺少手錶整機單位與校準驗證，不作溫度區間比較或醫療判讀")
            },
            recommendation = "如需測量體溫，請使用合格體溫計。",
            clinicalComparisons = emptyList()
        )
    }

    fun forSuite(
        withData: Int,
        total: Int,
        worn: Boolean?,
        ppg: Result?,
        ecg: Result?,
        bioz: Result?,
        temperature: Result?
    ): Result {
        val parts = listOfNotNull(ppg, ecg, bioz, temperature)
        val scoredParts = parts.filter { it.qualityScoreAvailable }
        val coverage = if (total > 0) withData * 100 / total else 0
        val score = scoredParts.mapNotNull { it.score }.takeIf { it.isNotEmpty() }
            ?.average()?.toInt()?.coerceIn(0, 100)
        val weak = scoredParts.filter { it.score?.let { value -> value < 50 } == true }.map { it.title }
        fun displayPart(part: Result) = if (part !in scoredParts) {
            "${part.title}：${part.grade}（未計分）"
        } else {
            "${part.title}：${part.grade}（${part.score}）"
        }
        val comps = buildList {
            add("通道覆蓋 $withData/$total（${coverage}%）")
            parts.forEach { part ->
                add(displayPart(part))
                part.clinicalComparisons.take(1).forEach { add("  · $it") }
            }
            add("通道覆蓋僅代表收到資料的比例，不代表生理品質或健康程度")
        }
        return Result(
            modality = "suite",
            title = "全感測器掃描",
            grade = when {
                score != null -> gradeOf(score, true)
                parts.isNotEmpty() -> "研究資料"
                else -> "無效"
            },
            score = score,
            headline = "掃描完成：$withData/$total 通道有資料" +
                when (worn) {
                    true -> " · 佩戴合格"
                    false -> " · 未貼合"
                    null -> ""
                },
            findings = buildList {
                add(score?.let { "綜合參考分數 $it/100（可計分通道平均）" }
                    ?: "尚無可計分通道；保留各通道原始結果")
                parts.forEach { add(displayPart(it)) }
                if (weak.isNotEmpty()) add("偏弱項目：${weak.joinToString("、")}")
            },
            recommendation = if (scoredParts.isEmpty()) "已收到的通道可檢視原始資料；量測條件仍需確認。"
            else if (weak.isEmpty()) "已計分通道可作日常訊號參考觀察。"
            else "建議針對偏弱項目單獨重測，並保持貼合與靜止。",
            clinicalComparisons = comps
        )
    }

    private fun gradeOf(score: Int, ok: Boolean): String = when {
        !ok || score < 25 -> "無效"
        score < 50 -> "偏弱"
        score < 75 -> "可用"
        score < 90 -> "良好"
        else -> "優良"
    }

    private fun wearLine(wearQualified: Boolean?): String = when (wearQualified) {
        true -> "佩戴校正：合格"
        false -> "佩戴校正：未貼合"
        null -> "佩戴校正：未知"
    }
}
