package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class RawSensorDetailActivity : ComponentActivity() {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun receivedDataCount(prefs: android.content.SharedPreferences, kind: String): Int =
        RawSensorPayloadPolicy.displayPresenceCount(
            prefs.getInt("${kind}_count_contract_version", 0),
            prefs.getInt("${kind}_raw_value_count", -1).takeIf { it >= 0 },
            prefs.getInt("${kind}_event_count", 0),
            prefs.getInt("${kind}_preview_point_count", 0),
            prefs.getInt("${kind}_samples", 0)
        )

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val kind = intent.getStringExtra("kind") ?: "ppg"
        val prefs = RawSensorStore.open(this@RawSensorDetailActivity)
        val storedValues = if (kind == "bioz") {
            SensorSuiteCsvParser.parseFloatsWithGapMarkers(prefs.getString("bioz_display_waveform", "").orEmpty())
                .ifEmpty { SensorSuiteCsvParser.parseFloats(prefs.getString("${kind}_waveform", "").orEmpty()) }
        } else if (kind in setOf("ppg", "spo2_raw", "mi_spo2", "thermometer", "body_temperature_raw")) {
            SensorSuiteCsvParser.parseFloatsWithGapMarkers(prefs.getString("${kind}_waveform", "").orEmpty())
        } else SensorSuiteCsvParser.parse(prefs.getString("${kind}_waveform", "").orEmpty()).map(Double::toFloat)
        val ppgDisplayValues = if (kind == "ppg") {
            SensorWaveformSelectionPolicy.ppgDisplayWaveform(
                SensorSuiteCsvParser.parseFloatsWithGapMarkers(prefs.getString("ppg_display_waveform", "").orEmpty()),
                storedValues
            )
        } else storedValues
        val persistedResearchEncoding = prefs.getString("ecg_research_waveform_encoding", "").orEmpty()
        val persistedResearchStatus = prefs.getString("ecg_research_decode_status", "").orEmpty()
        val persistedResearchDecodeState = prefs.getString("ecg_research_decode_state", "").orEmpty()
        val persistedResearchEcgValues = if (kind == "ecg" &&
            EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(persistedResearchEncoding, persistedResearchStatus)
        ) SensorSuiteCsvParser.parseFloatsWithGapMarkers(
            prefs.getString("ecg_research_waveform", "").orEmpty(),
            EcgEventRowExportPolicy.MAX_CANDIDATE_POINTS
        ) else emptyList()
        val storedEcgEventTimestamps = if (kind == "ecg") SensorSuiteCsvParser.parseLongs(
            prefs.getString("ecg_event_timestamps_ns", "").orEmpty(),
            EcgEventRowExportPolicy.MAX_EVENT_ROWS
        ) else emptyList()
        val rawEventInspection = if (kind == "ecg") {
            EcgHalEventResearchDecoder.decode(
                prefs.getString("ecg_event_raw_values_rows", "").orEmpty(),
                storedEcgEventTimestamps
            )
        } else null
        val researchEcgValues = persistedResearchEcgValues.ifEmpty {
            rawEventInspection?.waveform?.map(Double::toFloat).orEmpty()
        }
        var values = if (storedValues.isNotEmpty()) storedValues else researchEcgValues
        val sourceValues = SensorSuiteCsvParser.parse(prefs.getString("${kind}_source_waveform", "").orEmpty()).map(Double::toFloat)
        val ecgRawEventRows = if (kind == "ecg") SensorSuiteCsvParser.parseRows(
            prefs.getString("ecg_event_raw_values_rows", "").orEmpty(),
            EcgEventRowExportPolicy.MAX_EVENT_ROWS,
            SensorSuiteCsvParser.MAX_ECG_EVENT_ROW_CHARS
        ) else emptyList()
        val ecgRawValuesStatus = prefs.getString("ecg_event_raw_values_status", "legacy_app_split_unverified").orEmpty()
        val sampleRate = prefs.getFloat("${kind}_sample_rate", 0f)
        val receivedCount = receivedDataCount(prefs, kind)
        val sensor = prefs.getString("${kind}_sensor", "未知感測器").orEmpty()
        val vendor = prefs.getString("${kind}_vendor", "").orEmpty()
        val storedSummary = prefs.getString("${kind}_summary", "尚未量測").orEmpty()
        var status = prefs.getString("${kind}_status", "not_measured").orEmpty()
            val reason = prefs.getString("${kind}_reason", "").orEmpty()
            val measurementTime = prefs.getLong("${kind}_time", 0L)
            val timestampSource = prefs.getString("${kind}_timestamp_source", "").orEmpty()
            val initialDisplayState = SensorDisplayState.from(
                samples = receivedCount,
                status = status,
                measuredAt = measurementTime,
                now = System.currentTimeMillis(),
                freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
                kind = kind,
                timestampSource = timestampSource
            )
        val latest = prefs.getString("${kind}_latest", "").orEmpty()
        val latestValues = SensorSuiteCsvParser.parse(latest)
        val encoding = prefs.getString("${kind}_waveform_encoding", "legacy_unverified").orEmpty()
        val sourceEncoding = prefs.getString("${kind}_source_waveform_encoding", "not_preserved").orEmpty()
        val storedEcgProfile = if (kind == "ecg") EcgResearchProfileStore(this).readActive(prefs) else null
        val ecgWaveformVerified = kind == "ecg" && EcgPayloadInterpretationPolicy.canInterpretAsWaveform(encoding)
        val ecgUnmapped = kind == "ecg" && !ecgWaveformVerified
        val ecgResearchDecoded = ecgUnmapped && researchEcgValues.isNotEmpty() &&
            EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(persistedResearchEncoding, persistedResearchStatus)
        if (kind == "ecg" && ecgUnmapped && !ecgResearchDecoded) values = emptyList()
        var ecgResearchMv = kind == "ecg" && encoding == "ecg_external_reference_mv" &&
            storedEcgProfile?.inputEncoding == persistedResearchEncoding && ecgResearchDecoded &&
            EcgResearchDisplay.canApplyValidatedMvProfile(researchEcgValues)
        if (ecgResearchMv) {
            val recalculated = storedEcgProfile?.apply(researchEcgValues).orEmpty()
            if (recalculated.size == researchEcgValues.size) values = recalculated else ecgResearchMv = false
        }
        if (kind == "ecg" && encoding == "ecg_external_reference_mv" && !ecgResearchMv) {
            values = researchEcgValues
        }
        if (kind == "ecg" && status == "calibrated_external_reference" && !ecgResearchMv) {
            status = "research_signal_unscaled"
        }
        val displayState = SensorDisplayState.from(
            samples = receivedCount,
            status = status,
            measuredAt = measurementTime,
            now = System.currentTimeMillis(),
            freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
            kind = kind,
            timestampSource = timestampSource
        ).let { updated ->
            if (updated == SensorDisplayState.RAW_ONLY && initialDisplayState != SensorDisplayState.RAW_ONLY) {
                initialDisplayState
            } else {
                updated
            }
        }
        val ecgResearchRate = if (ecgResearchDecoded) {
            EcgHalRatePolicy.sampleRateHz(
                prefs.getFloat("ecg_hal_sample_period_s", Float.NaN).toDouble()
                    .takeIf(Double::isFinite)
            )
        } else null
        val ecgResearchAnalysis = if (ecgResearchDecoded && ecgResearchRate != null) {
            EcgResearchSignalAnalysis.analyze(researchEcgValues, ecgResearchRate)
        } else null
        val profile = if (ecgUnmapped) SensorHealthAlgorithms.waveformQuality(emptyList(), 0f, kind)
            else SensorHealthAlgorithms.waveformQuality(values, sampleRate, kind)
        val eventTiming = if (kind in setOf("ppg", "ecg")) {
            SensorEventTimingDiagnostics.fromCsv(
                prefs.getString("${kind}_event_timestamps_ns", "").orEmpty()
            )
        } else null
        val callbackDeliveryTiming = if (kind in setOf("ppg", "ecg")) {
            SensorEventTimingDiagnostics.fromCsv(
                prefs.getString("${kind}_event_callback_elapsed_realtime_ns", "").orEmpty()
            )
        } else null
        val qualityScore = if (ecgUnmapped) 0 else prefs.getInt("${kind}_quality_score", profile.score)
        val wearQualified = if (prefs.contains("${kind}_wear_qualified")) prefs.getBoolean("${kind}_wear_qualified", false) else null
        val biomedicalSampleCount = if (kind == "ecg" && ecgResearchDecoded) {
            researchEcgValues.count(Float::isFinite)
        } else if (RawSensorPayloadPolicy.isBiomedicalKind(kind)) {
            prefs.getInt("${kind}_raw_value_count", -1).takeIf { it >= 0 } ?: 0
        } else {
            prefs.getInt("${kind}_samples", 0)
        }
        val rhythm = PhysiologyAlgorithms.rhythm(profile.rrIntervalsMs, false)
        val accent = accent(kind)
        val calibration = prefs.getString("${kind}_calibration", "").orEmpty()
        val externalProfile = if (OpticalPulseDisplayPolicy.isOpticalKind(kind) || ecgUnmapped) null
            else SensorCalibrationProfileStore(this).read(kind)
        val rawAverage = prefs.getFloat("${kind}_average", Float.NaN).toDouble().takeIf { it.isFinite() }
        val opticalEstimate = OpticalPulseDisplayPolicy.researchEstimateBpm(
            kind = kind,
            estimateBpm = prefs.getFloat("${kind}_formula_pulse_bpm", Float.NaN).takeIf { it.isFinite() },
            qualityScore = qualityScore,
            status = status,
            measuredAtMillis = measurementTime,
            nowMillis = System.currentTimeMillis(),
            sampleRateHz = sampleRate,
            channelMappingVerified = prefs.getBoolean("${kind}_channel_mapping_verified", false),
            timestampSource = timestampSource,
            wearQualified = wearQualified
        )
        val verdict = when (kind) {
            "ppg" -> ClinicalVerdict.forPpg(
                qualityScore,
                biomedicalSampleCount,
                sampleRate,
                status,
                wearQualified,
                opticalEstimate?.toDouble(),
                hasReceivedSignal = receivedCount > 0,
                indexPeriodSamples = prefs.getInt("ppg_dominant_period_samples", 0).takeIf { it > 0 },
                periodCorrelation = prefs.getString("ppg_period_correlation", null)?.toDoubleOrNull(),
                confidenceScore = prefs.getInt("ppg_confidence_score", 0).takeIf { it in 1..100 },
                source = prefs.getString("ppg_source_sensor", "$vendor/$sensor").orEmpty(),
                timestampSource = timestampSource.ifBlank { "UNKNOWN" },
                calibrationStatus = Regex("calibration=([^;\\n]+)").find(calibration)?.groupValues?.getOrNull(1)
                    ?: "UNVERIFIED",
                relativeAcDcRatio = prefs.getFloat("ppg_formula_relative_ac_dc", Float.NaN)
                    .takeIf { it.isFinite() }?.toDouble()
            ).let { result ->
                val motionIndex = prefs.getFloat("ppg_motion_activity_index", Float.NaN).toDouble()
                    .takeIf(Double::isFinite)
                val pairedCount = prefs.getInt("ppg_motion_paired_count", 0)
                val dynamicRms = prefs.getFloat("ppg_motion_dynamic_rms_mps2", Float.NaN).toDouble()
                    .takeIf(Double::isFinite)
                val matchedCoverage = prefs.getFloat("ppg_motion_matched_coverage", Float.NaN).toDouble()
                    .takeIf(Double::isFinite)
                val overlapCoverage = prefs.getFloat("ppg_motion_overlap_of_ppg_window", Float.NaN).toDouble()
                    .takeIf(Double::isFinite)
                val motionFinding = if (motionIndex != null) {
                    "同步腕部動作活動指數 ${"%.3f".format(motionIndex)}（0–1）· 動態加速度 RMS ${dynamicRms?.let { "%.3f".format(it) } ?: "—"} m/s² · 交集內配對率 ${matchedCoverage?.let { "%.0f".format(it * 100) } ?: "—"}% · 時間交集 ${overlapCoverage?.let { "%.0f".format(it * 100) } ?: "—"}% PPG 量測時段 · 配對 $pairedCount 點 · ${prefs.getString("ppg_motion_timestamp_source", "unknown")} · ${prefs.getString("ppg_motion_algorithm_version", "unknown")}；不是 PPG 偽影分數或脈率準確度"
                } else {
                    "同步腕部動作活動指數不可用：${prefs.getString("ppg_motion_status", "尚未取得同步動作資料").orEmpty()}"
                }
                result.copy(findings = result.findings + motionFinding)
            }
            "ecg" -> {
                val hr = if (ecgWaveformVerified) Regex("hr=([0-9.]+)").find(calibration)?.groupValues?.getOrNull(1)?.toDoubleOrNull() else null
                val rr = if (ecgWaveformVerified) Regex("rr=([0-9]+)").find(calibration)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: profile.rrIntervalsMs.size else 0
                ClinicalVerdict.forEcg(
                    qualityScore,
                    biomedicalSampleCount,
                    sampleRate.takeIf { ecgWaveformVerified } ?: 0f,
                    status,
                    hr,
                    rr,
                    wearQualified,
                    if (ecgResearchDecoded) listOf(
                        "研究波形依保存的樣本格式呈現；不得由事件欄位排列推定 ADC 取樣時鐘",
                        "HAL 回報點速率：${ecgResearchRate?.let { "%.0f 點/秒".format(it) } ?: "未提供"}；事件回呼間隔不等於 ADC 取樣率"
                    ) else if (ecgUnmapped) listOf("SensorEvent.values 欄位未映射；只顯示原始值，不計算波形品質、RR 或心率") +
                        EcgResearchDisplay.packetStructureSummaryLines(
                            rawEventInspection?.eventCount ?: 0,
                            rawEventInspection?.layoutComparison
                        ) else emptyList(),
                    waveformEncoding = encoding,
                    timelineSource = prefs.getString("ecg_research_timeline_source", "NORMALIZED_INDEX").orEmpty(),
                    rPeakIndices = prefs.getString("ecg_research_r_peak_indices", "").orEmpty()
                        .split(',').mapNotNull(String::toIntOrNull),
                    rPeakIndexDistancesSamples = prefs.getString("ecg_research_r_peak_index_distances_samples", "").orEmpty()
                        .split(',').mapNotNull(String::toIntOrNull),
                    externalCalibrationVerified = ecgResearchMv,
                    rhythmPatternTitle = prefs.getString("ecg_research_rhythm_pattern_title", null)
                ).let { result ->
                    if (ecgResearchDecoded) result.copy(
                        title = if (ecgResearchMv) "ECG 研究 mV 波形" else "ECG 未校準研究波形",
                        grade = "研究品質",
                        score = ecgResearchAnalysis?.stableWindowQualityScore,
                        headline = EcgResearchDisplay.decodedSampleHeadline(researchEcgValues),
                        findings = EcgResearchDisplay.evidenceLines(
                            waveform = values,
                            sampleRateHz = ecgResearchRate,
                            timelineSource = prefs.getString("ecg_research_timeline_source", "NORMALIZED_INDEX").orEmpty(),
                            rPeakIndices = prefs.getString("ecg_research_r_peak_indices", "").orEmpty()
                                .split(',').mapNotNull(String::toIntOrNull),
                            rPeakIndexDistancesSamples = prefs.getString("ecg_research_r_peak_index_distances_samples", "").orEmpty()
                                .split(',').mapNotNull(String::toIntOrNull),
                            calibratedMv = ecgResearchMv,
                            decoderTraceability = EcgResearchDisplay.decoderTraceabilityScore(
                                eventRowsStatus = ecgRawValuesStatus,
                                decoderEncoding = if (persistedResearchEcgValues.isNotEmpty()) {
                                    prefs.getString("ecg_research_waveform_encoding", "").orEmpty()
                                } else {
                                    EcgHalEventResearchDecoder.ENCODING
                                },
                                decoded = ecgResearchDecoded
                            )
                        ) + listOf(
                            "${EcgResearchDisplay.stableWindowQualityLabel(ecgResearchAnalysis?.stableWindowQualityScore)}；全段修正後 ${ResearchScoreDisplay.format(ecgResearchAnalysis?.engineeringQualityScore)}；原始 ${ResearchScoreDisplay.format(ecgResearchAnalysis?.rawEngineeringQualityScore)}（不是醫療準確度或疾病機率）",
                            if (ecgResearchMv) {
                                "研究 mV 尺度來自外部配對 profile；導聯與整機臨床效能仍未驗證"
                            } else {
                                "品質與 RR 尚未開放：缺少 OEM 導聯、增益、ADC 單位與裝置級驗證"
                            },
                            prefs.getString("ecg_research_rhythm_pattern_title", null)
                                ?.let { "RR 型態研究描述：$it；不代表 AF 或其他疾病判定" }
                                ?: "RR 型態研究描述：資料不足／時間軸或品質閘門未通過",
                            wearLine(wearQualified)
                        )
                    ) else result
                }
            }
            "bioz" -> {
                val rawChannels = BiozRawChannelEvidence.decode(calibration)
                val biozResearch = BiomedicalCalibration.calibrateBioz(
                    rawChannels.mapNotNull { it.second?.takeIf(Double::isFinite) }, values
                )
                val currentMetrics = BiozResearchDisplayPolicy.resolve(
                    biozResearch,
                    BiozResearchDisplayMetrics(
                        qualityScore,
                        prefs.getInt("bioz_waveform_median_position_index", -1).takeIf { it in 0..100 },
                        prefs.getFloat("bioz_relative_change_percent", Float.NaN).toDouble().takeIf(Double::isFinite),
                        prefs.getInt("bioz_stability_score", -1).takeIf { it in 0..100 },
                        prefs.getInt("bioz_confidence_score", -1).takeIf { it in 1..100 }
                    )
                )
                ClinicalVerdict.forBioz(
                    currentMetrics.qualityScore ?: 0,
                    null, null, null, null, status,
                    comparisons = biozResearch.comparisons,
                    rawSampleCount = biomedicalSampleCount,
                    hasReceivedSignal = receivedCount > 0,
                    waveformMedianPositionIndex = currentMetrics.waveformMedianPositionIndex,
                    relativeChangePercent = currentMetrics.relativeChangePercent,
                    stabilityScore = currentMetrics.stabilityScore,
                    confidenceScore = currentMetrics.confidenceScore,
                    timestampSource = prefs.getString("bioz_timestamp_source", null),
                    source = prefs.getString("bioz_source_sensor", "UNKNOWN").orEmpty(),
                    measuredAtMillis = measurementTime,
                    rawChannelValues = rawChannels
                )
            }
            "bioz_respiration" -> {
                val research = BiomedicalCalibration.calibrateBioz(emptyList(), values)
                ClinicalVerdict.forBiozRespiration(
                    research = research,
                    rawSampleCount = biomedicalSampleCount,
                    hasReceivedSignal = receivedCount > 0,
                    status = status,
                    source = prefs.getString("${kind}_source_sensor", "$vendor/$sensor").orEmpty(),
                    measuredAtMillis = measurementTime,
                    timestampSource = timestampSource,
                    wearQualified = wearQualified
                )
            }
            "body_temperature_raw", "thermometer" -> {
                val rawValue = TemperatureSensorValuePolicy.detailValue(rawAverage, values, latestValues)
                ClinicalVerdict.forTemperature(
                    rawValue,
                    biomedicalSampleCount,
                    status,
                    wearQualified,
                    source = "$vendor/$sensor",
                    measuredAtMillis = measurementTime,
                    timestampSource = timestampSource,
                    calibrationStatus = "UNVERIFIED"
                ).let { result ->
                    result.copy(
                        findings = result.findings + TemperatureDetailResearchPolicy.findings(
                            waveform = values,
                            fallbackRawValue = rawValue,
                            source = "$vendor/$sensor",
                            timestampSource = timestampSource,
                            measuredAtMillis = measurementTime,
                            nowMillis = System.currentTimeMillis()
                        )
                    )
                }
            }
            "spo2_raw", "mi_spo2" -> {
                val rawOptical = values.filter { it.isFinite() && it != 524287f }
                val rawSource = prefs.getString("${kind}_source_sensor", "$vendor/$sensor").orEmpty()
                val trustedMeasurementTime = HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(timestampSource) &&
                    measurementTime > 0L
                val evidenceScore = ResearchQualityIndexCalculator.calculate(
                    finiteRatio = values.size.takeIf { it > 0 }?.let { rawOptical.size.toDouble() / it },
                    sampleCount = rawOptical.size,
                    waveformStability = null,
                    sourceEvidence = ResearchSourceEvidence.score(rawSource),
                    timeEvidence = if (trustedMeasurementTime) 100 else 0
                ).score
                val baseVerdict = ClinicalVerdict.forRawSpO2(
                    biomedicalSampleCount,
                    status,
                    wearQualified,
                    hasReceivedSignal = receivedCount > 0,
                    researchEvidenceScore = evidenceScore,
                    source = rawSource.ifBlank { "UNKNOWN_SOURCE" },
                    measuredAtMillis = measurementTime,
                    timestampSource = timestampSource,
                    calibrationStatus = "OEM_PERCENTAGE_CURVE_UNVERIFIED"
                )
                baseVerdict.copy(
                    modality = kind,
                    title = if (kind == "mi_spo2") "Xiaomi SpO₂" else "SpO₂ 原始",
                    findings = baseVerdict.findings +
                        Spo2RawQualityPolicy.summary(Spo2RawQualityPolicy.assess(values, sampleRate)) +
                        Spo2RelativeSignalDisplay.describe(values)
                )
            }
            "max_hr", "lifeq_ott" -> ClinicalVerdict.forOpticalAdc(
                sampleCount = biomedicalSampleCount,
                rawAverage = rawAverage,
                estimateBpm = opticalEstimate?.toDouble(),
                status = status,
                wearQualified = wearQualified
            )
            "whs_heart_rate", "heart_rate_raw" -> {
                val candidateBpm = prefs.getFloat("${kind}_average", Float.NaN).takeIf { it.isFinite() }
                    ?: values.lastOrNull()
                val bpm = candidateBpm?.let {
                    HeartRatePresentationPolicy.visibleDetailBpm(
                        kind = kind,
                        bpm = it,
                        status = status,
                        measuredAt = measurementTime,
                        timestampSource = timestampSource,
                        now = System.currentTimeMillis()
                    )
                }
                val cal = ClinicalMetricCalibration.forHeartRate(bpm?.toDouble(), resting = false)
                ClinicalVerdict.Result(
                    modality = kind,
                    title = title(kind),
                    grade = if (bpm == null) "未評分" else "研究資料",
                    score = null,
                    headline = cal.headline,
                    findings = listOf(
                        HeartRatePresentationPolicy.detailValueLabel(bpm),
                        "狀態 $status · 來源 ${prefs.getString("${kind}_source_sensor", "UNKNOWN").orEmpty()}",
                        if (bpm == null) "未顯示即時 bpm：數值、來源時間或新鮮度未通過" else
                            HeartRatePresentationPolicy.detailTimeDescription(
                                kind, measurementTime, timestampSource, System.currentTimeMillis()
                            ),
                        "訊號品質：未評分；參考區間分類不代表量測品質或準確度",
                        wearLine(wearQualified)
                    ),
                    recommendation = "瞬時心率僅供參考，不能診斷心律疾病。",
                    clinicalComparisons = cal.comparisons
                )
            }
            else -> null
        }

        val renderedWaveform = when {
            ecgUnmapped && !ecgResearchDecoded -> emptyList()
            kind == "ppg" -> ppgDisplayValues.takeLast(320)
            else -> values.takeLast(320)
        }
        val chart = MedicalChartView(this).also {
            it.setWaveform(
                renderedWaveform,
                accent,
                kind,
                if (ecgResearchDecoded) EcgResearchDisplay.chartAxisLabel(ecgResearchMv) else null
            )
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(40))
            setBackgroundColor(Color.rgb(6, 10, 16))
            addView(label("‹  返回", 16f, HealthVisuals.blue).apply { setOnClickListener { finish() } })
            addView(label(title(kind), 28f, Color.WHITE), margins(top = 18))
            addView(label("手錶健康參考", 11f, accent), margins(top = 5, bottom = 14))
            if (kind == "ecg") {
                val ecgCommandFeedback = label(
                    if (values.isEmpty()) "目前沒有可顯示波形；可直接從此頁啟動手錶 ECG。" else "可重新啟動手錶 ECG 擷取新波形。",
                    13f,
                    HealthVisuals.secondary
                )
                addView(ecgCommandFeedback, margins(bottom = 8))
                addView(android.widget.Button(this@RawSensorDetailActivity).apply {
                    text = "在手錶開始 ECG"
                    isAllCaps = false
                    setOnClickListener {
                        isEnabled = false
                        text = "正在連接手錶…"
                        WearCommandDispatcher.send(this@RawSensorDetailActivity, "/control/ecg-now") { outcome ->
                            isEnabled = true
                            text = "在手錶開始 ECG"
                            ecgCommandFeedback.text = WearCommandDispatcher.feedback(outcome)
                            if (outcome !is WearCommandDispatcher.Outcome.Sent) {
                                android.widget.Toast.makeText(
                                    this@RawSensorDetailActivity,
                                    ecgCommandFeedback.text,
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }, margins(bottom = 12))
            }
            val outcome = UserFacingMeasurementOutcome.from(
                state = displayState,
                metricKind = kind,
                sourceStatus = status
            )
            val readableReading = when {
                displayState == SensorDisplayState.AVAILABLE && kind in setOf("heart_rate_raw", "whs_heart_rate") -> {
                    val bpm = rawAverage?.toFloat() ?: latestValues.lastOrNull()?.toFloat()
                    bpm?.let {
                        HeartRatePresentationPolicy.visibleDetailBpm(
                            kind, it, status, measurementTime, timestampSource, System.currentTimeMillis()
                        )
                    }?.let { "心率 ${"%.0f".format(it)} bpm · ${ConsumerVitalsSummary.heartRateLabel(it.toDouble())}" }
                }
                displayState == SensorDisplayState.AVAILABLE && kind == "pressure" ->
                    rawAverage?.takeIf { it in 300.0..1100.0 }?.let { "環境氣壓 ${"%.0f".format(it)} hPa" }
                kind in setOf("ppg", "max_hr", "lifeq_ott") ->
                    opticalEstimate?.let { "脈搏估算 ${"%.0f".format(it)} bpm · 研究參考" }
                else -> null
            }
            addView(panel().apply {
                addView(label("量測結果", 20f, accent))
                addView(label(readableReading ?: outcome.label, 28f, Color.WHITE), margins(top = 8))
                if (readableReading == null) {
                    addView(label(outcome.conclusion, 18f, Color.rgb(230, 235, 245)), margins(top = 10))
                    addView(label(outcome.nextAction, 14f, Color.rgb(200, 220, 200)), margins(top = 10))
                } else {
                    addView(label("可查看時間與近期趨勢", 14f, Color.rgb(200, 220, 200)), margins(top = 10))
                }
                val details = LinearLayout(this@RawSensorDetailActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                }
                addView(label("查看技術細節　⌄", 13f, Color.rgb(150, 190, 220)).apply {
                    isClickable = true
                    setOnClickListener {
                        details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        text = if (details.visibility == View.VISIBLE) "收起技術細節　⌃" else "查看技術細節　⌄"
                    }
                }, margins(top = 12))
                addView(details, margins(top = 8))
                if (verdict != null) {
                    details.addView(label(
                        ResearchSignalEvidenceText.summarize(
                            kind = kind,
                            sampleCount = biomedicalSampleCount,
                            status = status,
                            chartPoints = if (ecgResearchDecoded) values.size else if (ecgUnmapped) ecgRawEventRows.size else values.size,
                            unmapped = ecgUnmapped,
                            researchDecoded = ecgResearchDecoded
                        ),
                        12f,
                        Color.rgb(195, 205, 225)
                    ))
                    details.addView(label(verdict.findings.joinToString("\n") { "· $it" }, 13f, Color.rgb(180, 200, 215)))
                    if (verdict.clinicalComparisons.isNotEmpty()) {
                        details.addView(label("臨床公開資料比對\n${verdict.clinicalComparisons.joinToString("\n") { "· $it" }}", 12f, Color.rgb(170, 210, 200)), margins(top = 10))
                    }
                    details.addView(label(ClinicalVerdict.DISCLAIMER, 12f, Color.rgb(255, 200, 120)), margins(top = 12))
                } else {
                    details.addView(label("此通道目前只有狀態與原始證據，尚無對應的生理演算法。", 12f, Color.rgb(195, 205, 225)))
                }
            }, margins(bottom = 12))
            if (kind == "ecg" && ecgWaveformVerified) {
                val rhythmTitle = prefs.getString("ecg_rhythm_title", null)
                val rhythmDetail = prefs.getString("ecg_rhythm_detail", null)
                if (!rhythmTitle.isNullOrBlank()) {
                    addView(panel().apply {
                        addView(label("心律研究篩檢（手錶同步）", 19f, Color.rgb(255, 210, 120)))
                        addView(label(rhythmTitle, 16f, Color.WHITE), margins(top = 8))
                        if (!rhythmDetail.isNullOrBlank()) {
                            addView(label(rhythmDetail, 12f, Color.rgb(180, 190, 210)), margins(top = 8))
                        }
                        addView(label("研究級啟發式，非醫療診斷、非原廠 AF。", 12f, Color.rgb(255, 190, 120)), margins(top = 10))
                    }, margins(bottom = 12))
                }
            }
            val resultContentCount = childCount
            if (kind == "ecg" && ecgResearchAnalysis != null) {
                addView(panel().apply {
                    addView(label("ECG 研究頻譜與品質分析", 19f, Color.rgb(100, 210, 255)))
                    addView(MedicalChartView(this@RawSensorDetailActivity).apply {
                        setWaveform(
                            ecgResearchAnalysis.filtered.takeLast(1_500), accent, "ecg",
                            EcgResearchDisplay.chartAxisLabel(ecgResearchMv)
                        )
                    }, LinearLayout.LayoutParams(-1, dp(260)))
                    addView(label(
                        "原始頻譜：0.5–40 Hz ${"%.1f".format(ecgResearchAnalysis.ecgBandPowerRatio * 100)}% · " +
                            "60 Hz ${"%.1f".format(ecgResearchAnalysis.mains60PowerRatio * 100)}% · " +
                            ">40 Hz ${"%.1f".format(ecgResearchAnalysis.highFrequencyPowerRatio * 100)}% · " +
                            "<0.5 Hz ${"%.1f".format(ecgResearchAnalysis.baselinePowerRatio * 100)}% · " +
                            "主頻 ${"%.1f".format(ecgResearchAnalysis.dominantFrequencyHz)} Hz",
                        13f,
                        Color.WHITE
                    ), margins(top = 10))
                    addView(label(
                        "修正後：品質 ${ResearchScoreDisplay.format(ecgResearchAnalysis.engineeringQualityScore)} · " +
                            "0.5–40 Hz ${"%.1f".format(ecgResearchAnalysis.correctedEcgBandPowerRatio * 100)}% · " +
                            "60 Hz ${"%.1f".format(ecgResearchAnalysis.correctedMains60PowerRatio * 100)}% · " +
                            ">40 Hz ${"%.1f".format(ecgResearchAnalysis.correctedHighFrequencyPowerRatio * 100)}% · " +
                            "<0.5 Hz ${"%.1f".format(ecgResearchAnalysis.correctedBaselinePowerRatio * 100)}%",
                        13f,
                        Color.rgb(120, 225, 170)
                    ), margins(top = 8))
                    ecgResearchAnalysis.stableWindowQualityScore?.let { stableScore ->
                        addView(label(
                            "穩定區段：$stableScore/100 · ${ecgResearchAnalysis.stableWindowCount} 個 2 秒視窗 · " +
                                "覆蓋率 ${"%.0f".format(ecgResearchAnalysis.stableWindowCoverageRatio * 100)}%；全段分數仍保留，不以局部高分取代。",
                            13f,
                            Color.rgb(120, 225, 170)
                        ), margins(top = 8))
                    }
                    addView(label(
                        ecgResearchAnalysis.issues.joinToString("\n") { "· $it" }.ifBlank { "· 未發現明顯頻帶異常" },
                        13f,
                        if (ecgResearchAnalysis.issues.isEmpty()) Color.rgb(120, 225, 170) else Color.rgb(255, 190, 120)
                    ), margins(top = 8))
                    addView(label(
                        "顯示濾波：60 Hz notch＋0.5–40 Hz；原始資料未覆寫。本區是此筆波形的頻譜分析，不是逐拍資料庫比對。PhysioNet ECG-ID 僅作尺度無關品質參考，不能提供小米 mV 校準或疾病診斷。",
                        11f,
                        HealthVisuals.secondary
                    ), margins(top = 8))
                }, margins(bottom = 12))
            }
            val formulaEligibility = RawSensorFormulaDisplayPolicy.evaluate(
                kind = kind,
                samples = biomedicalSampleCount,
                status = status,
                measuredAtMillis = measurementTime,
                timestampSource = prefs.getString("${kind}_timestamp_source", null),
                hrvTimestampSource = prefs.getString("hrv_research_timestamp_source", null),
                sessionStartedAtMillis = prefs.getLong("${kind}_session_started_at_ms", 0L),
                nowMillis = System.currentTimeMillis(),
                qualityScore = qualityScore,
                sampleRateHz = sampleRate.takeIf { !ecgUnmapped } ?: 0f,
                channelMappingVerified = !ecgUnmapped && prefs.getBoolean("${kind}_channel_mapping_verified", false),
                formulaPulseBpm = prefs.getFloat("${kind}_formula_pulse_bpm", Float.NaN).takeIf { !ecgUnmapped && it.isFinite() },
                hrvMeasuredAtMillis = prefs.getLong("hrv_research_time", 0L).takeIf { !ecgUnmapped } ?: 0L,
                hrvSourceKind = prefs.getString("hrv_research_source_kind", "").orEmpty().takeIf { !ecgUnmapped }.orEmpty(),
                hrvSampleCount = prefs.getInt("hrv_research_samples", 0).takeIf { !ecgUnmapped } ?: 0,
                hrvRrCount = prefs.getInt("hrv_research_rr_count", 0).takeIf { !ecgUnmapped } ?: 0,
                ecgResearchOnly = !ecgUnmapped && prefs.getBoolean("ecg_rhythm_research_only", false),
                hasOpticalMetrics = !ecgUnmapped && listOf("formula_pi_percent", "formula_pulse_bpm", "formula_ratio_r")
                    .any { prefs.contains("${kind}_$it") },
                hasHrvMetrics = !ecgUnmapped && listOf("formula_rmssd_ms", "formula_nrmssd", "formula_sdnn_ms")
                    .any { prefs.contains("${kind}_$it") },
                hasEnvironmentMetrics = !ecgUnmapped && listOf("formula_altitude_m", "formula_pressure_hpa")
                    .any { prefs.contains("${kind}_$it") },
                wearQualified = wearQualified,
                waveformEncoding = prefs.getString("${kind}_waveform_encoding", null)
            )
            val formulaNote = prefs.getString("${kind}_formula_note", null).takeIf { !ecgUnmapped }
                ?.takeIf {
                    kind == "pressure" || formulaEligibility.showOpticalMetrics || formulaEligibility.showHrvMetrics ||
                        formulaEligibility.showEcgPulse || formulaEligibility.showEnvironmentMetrics
                }
            val hasFormula = formulaEligibility.showOpticalMetrics || formulaEligibility.showHrvMetrics ||
                formulaEligibility.showEcgPulse || formulaEligibility.showEnvironmentMetrics
            if (hasFormula || !formulaNote.isNullOrBlank()) {
                addView(panel().apply {
                    val formulaTitle = if (kind == "ecg") "ECG 研究公式輸出（非一般心率／非診斷）" else "文獻公式輸出"
                    addView(label(formulaTitle, 19f, Color.rgb(100, 210, 255)))
                    val bits = mutableListOf<String>()
                    if (formulaEligibility.showOpticalMetrics) {
                        prefs.getFloat("${kind}_formula_pi_percent", Float.NaN).takeIf { it.isFinite() }?.let {
                            bits += "PI ${"%.2f".format(it)}%（研究值）"
                        }
                        opticalEstimate?.let { bits += "脈率 ${"%.0f".format(it)} bpm（研究估算）" }
                        prefs.getFloat("${kind}_formula_ratio_r", Float.NaN).takeIf { it.isFinite() }?.let {
                            bits += "R ${"%.3f".format(it)}（未轉 SpO₂%）"
                        }
                    }
                    if (formulaEligibility.showEcgPulse) {
                        prefs.getFloat("${kind}_formula_pulse_bpm", Float.NaN).takeIf { it.isFinite() }?.let {
                            bits += "脈率 ${"%.0f".format(it)} bpm（研究估算，非一般心率量測）"
                        }
                    }
                    if (formulaEligibility.showHrvMetrics) {
                        prefs.getFloat("${kind}_formula_rmssd_ms", Float.NaN).takeIf { it.isFinite() }?.let {
                            bits += "RMSSD ${"%.0f".format(it)} ms（研究估算）"
                        }
                        prefs.getFloat("${kind}_formula_nrmssd", Float.NaN).takeIf { it.isFinite() }?.let {
                            bits += "nRMSSD ${"%.3f".format(it)}（研究估算）"
                        }
                        prefs.getFloat("${kind}_formula_sdnn_ms", Float.NaN).takeIf { it.isFinite() }?.let {
                            bits += "SDNN ${"%.0f".format(it)} ms（研究估算）"
                        }
                    }
                    if (formulaEligibility.showEnvironmentMetrics) {
                        prefs.getFloat("${kind}_formula_altitude_m", Float.NaN).takeIf { it.isFinite() }?.let {
                            bits += "ISA 高度 ${"%.1f".format(it)} m"
                        }
                        prefs.getFloat("${kind}_formula_pressure_hpa", Float.NaN).takeIf { it.isFinite() }?.let {
                            bits += "氣壓 ${"%.1f".format(it)} hPa"
                        }
                    }
                    if (formulaEligibility.showHrvMetrics && prefs.contains("${kind}_formula_stress_score")) {
                        bits += "研究壓力分數 ${prefs.getInt("${kind}_formula_stress_score", 0)}（非原廠）"
                    }
                    addView(label(bits.joinToString(" · ").ifBlank { "—" }, 14f, Color.WHITE), margins(top = 8))
                    if (formulaEligibility.showOpticalMetrics || formulaEligibility.showHrvMetrics || formulaEligibility.showEcgPulse) {
                        val researchLimit = if (kind == "ecg") {
                            "研究估算；不是經校準的心率／ECG 判讀，不可用於診斷或醫療決策。"
                        } else {
                            "與本感測器目前 session 的量測時間配對；僅供研究參考，非醫療校準或診斷。"
                        }
                        addView(label("$researchLimit\n量測時間：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(measurementTime))}", 12f, Color.rgb(255, 190, 120)), margins(top = 8))
                    }
                    if (!formulaNote.isNullOrBlank()) {
                        addView(label(formulaNote, 12f, Color.rgb(180, 190, 210)), margins(top = 8))
                    }
                    prefs.getString("${kind}_formula_citations", null)?.let {
                        addView(label(it, 11f, Color.rgb(140, 150, 170)), margins(top = 8))
                    }
                }, margins(bottom = 12))
            }
            val evidence = ResearchClinicalEvidence.forModality(kind)
            if (evidence.isNotEmpty()) {
                addView(panel().apply {
                    addView(label("研究方法與證據", 19f, Color.rgb(105, 210, 255)))
                    addView(label("每項判讀均標示方法、最低條件與不可越過的醫療邊界。", 12f, HealthVisuals.secondary), margins(top = 6))
                    evidence.forEach { item ->
                        addView(label(item.title, 16f, Color.WHITE), margins(top = 14))
                        addView(label("方法：${item.method}\n最低條件：${item.minimumInput}\n輸出：${item.output}", 12f, Color.rgb(190, 210, 225)), margins(top = 5))
                        addView(label(item.citation, 11f, Color.rgb(115, 180, 230)), margins(top = 5))
                        addView(label("限制：${item.boundary}", 11f, Color.rgb(255, 190, 120)), margins(top = 5))
                    }
                }, margins(bottom = 12))
            }
            if (!ecgUnmapped) ManufacturerSelfTest.evaluate(kind, values, sampleRate, status)?.let { report ->
                addView(panel().apply {
                    addView(label(report.title, 19f, Color.rgb(90, 220, 170)))
                    addView(label("${report.passedCount}/${report.checks.size} 項通過", 28f, Color.WHITE), margins(top = 8))
                    report.checks.forEach { check ->
                        val mark = if (check.passed) "✓" else "!"
                        val color = if (check.passed) Color.rgb(120, 225, 170) else Color.rgb(255, 185, 95)
                        addView(label("$mark ${check.name} · ${check.observed}\n參考：${check.reference}", 12f, color), margins(top = 9))
                    }
                    addView(label(report.note, 11f, Color.rgb(255, 195, 120)), margins(top = 12))
                    addView(label("來源：Analog Devices MAX86178 Rev.4 datasheet／MAX86178 EV Kit guide", 11f, HealthVisuals.secondary), margins(top = 8))
                }, margins(bottom = 12))
            }
            addView(panel().apply {
                addView(label(when {
                    ecgResearchMv -> "ECG 研究 mV 波形（外部參考 profile）"
                    ecgResearchDecoded -> "ECG 研究波形（已解碼、未校準）"
                    ecgUnmapped -> "Sensor API 未解碼事件值"
                    else -> "訊號預覽"
                }, 19f, Color.WHITE))
                addView(label(encoding.ifBlank { "waveform" }, 12f, HealthVisuals.secondary), margins(bottom = 8))
                addView(label(listOfNotNull(sensor, vendor.takeIf { it.isNotBlank() }).joinToString(" · "), 12f, HealthVisuals.secondary), margins(bottom = 10))
                if (ecgUnmapped) {
                    if (ecgResearchDecoded) {
                        addView(chart, LinearLayout.LayoutParams(-1, dp(260)))
                        addView(label(
                            if (ecgResearchMv) {
                                "${EcgResearchDisplay.waveformPointHeadline(values)} · 獨立留出驗證 profile · ${ecgResearchRate?.let { "推定 %.0f Hz".format(it) } ?: "時間尺度未知"}\n研究參考 mV 尺度，不代表 OEM 或臨床校準；不作疾病診斷。"
                            } else {
                                "${EcgResearchDisplay.waveformPointHeadline(values)} · 研究樣本格式已解析 · ${ecgResearchRate?.let { "HAL 回報 %.0f 點/秒".format(it) } ?: "時間尺度未知"}\n未校準原始碼值，不是 mV；不做 AF 或疾病診斷。"
                            },
                            12f,
                            HealthVisuals.secondary
                        ), margins(top = 10))
                    } else {
                        addView(label("SensorEvent.values 欄位結構未通過研究解碼；最新事件值：${latest.ifBlank { "—" }}", 13f, HealthVisuals.secondary))
                    }
                } else {
                    addView(chart, LinearLayout.LayoutParams(-1, dp(260)))
                    addView(label(
                        if (values.isEmpty()) "尚無波形；請先量測"
                        else SignalTimelineLabel.describe(renderedWaveform.size, sampleRate, timestampSource, measurementTime),
                        12f,
                        HealthVisuals.secondary
                    ), margins(top = 10))
                }
            })
            addView(panel().apply {
                addView(label("訊號品質", 19f, Color.WHITE))
                if (ecgUnmapped) {
                    addView(label(
                        if (ecgResearchDecoded) "研究波形品質 ${ResearchScoreDisplay.format(ecgResearchAnalysis?.engineeringQualityScore)} · 解碼來源 Sensor HAL；相對品質不代表診斷準確度。"
                        else "未評估：事件欄位及波形樣本映射未確認。",
                        14f, HealthVisuals.secondary
                    ), margins(top = 10))
                } else {
                    addView(label("本頁波形估算 ${ResearchScoreDisplay.format(profile.score)} · ${profile.level}", 17f, accent), margins(top = 10))
                    addView(label("接收端品質分數 ${ResearchScoreDisplay.format(qualityScore)}", 12f, HealthVisuals.secondary), margins(top = 3))
                    addView(label(profile.metrics, 13f, Color.rgb(220, 225, 235)), margins(top = 8))
                }
                eventTiming?.let { timing ->
                    val timingText = when {
                        timing.invalidTimestampCount > 0 -> "事件時戳無效 ${timing.invalidTimestampCount}/${timing.eventCount}；不能用這批事件推算脈搏間隔或 HRV。"
                        timing.outOfOrderCount > 0 -> "事件時戳倒序 ${timing.outOfOrderCount} 組；時間相關分析需拒收。"
                        timing.medianIntervalMs != null -> "SensorEvent 事件間隔中位數 ${"%.3f".format(timing.medianIntervalMs)} ms（不是 ADC 波形取樣率）。"
                        else -> "事件不足，無法計算事件間隔；這不是 ADC 波形取樣率。"
                    }
                    addView(label("事件時序診斷：$timingText", 12f, Color.rgb(255, 190, 120)), margins(top = 8))
                }
                callbackDeliveryTiming?.let { timing ->
                    val deliveryText = timing.medianIntervalMs?.let { "中位數 ${"%.3f".format(it)} ms" }
                        ?: "樣本不足，無法計算間隔"
                    addView(label(
                        "App callback 接收間隔：$deliveryText · 僅診斷傳遞節奏，不是感測事件時間、ADC 取樣率或心搏間隔。",
                        12f,
                        HealthVisuals.secondary
                    ), margins(top = 5))
                }
                val visibleCalibration = if (OpticalPulseDisplayPolicy.isOpticalKind(kind)) {
                    "此通道保存原始光學封包；封包數值不是 bpm，未套用外部線性校準。"
                } else calibration
                if (visibleCalibration.isNotBlank()) addView(label("校準：$visibleCalibration", 12f, Color.rgb(120, 220, 190)), margins(top = 8))
                val measurementReason = if (OpticalPulseDisplayPolicy.isOpticalKind(kind)) {
                    "光學原始碼值不代表心率；${displayState.label}"
                } else reason
                val visibleReason = listOfNotNull(
                    measurementReason.takeIf(String::isNotBlank),
                    "epoch_offset_drift_exceeded".takeIf { timestampSource == it }?.let {
                        "時間基準偏移超過 1 秒；本次量測時間不做換算，原始樣本保留。"
                    }
                ).joinToString("\n")
                val timeLabel = measurementTime.takeIf { it > 0L }?.let {
                    val formatted = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))
                    if (HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource)) {
                        "量測時間：$formatted"
                    } else {
                        "時間欄位：$formatted（來源未確認，不能確認為量測時間）"
                    }
                } ?: "時間欄位：無記錄"
                addView(label("資料狀態：${displayState.label}\n$timeLabel\n來源狀態：$status\n$visibleReason", 12f, HealthVisuals.secondary), margins(top = 8))
                val currentEventCount = prefs.getInt("${kind}_event_count", 0)
                val priorReceipt = prefs.getLong("${kind}_last_capture_receipt_at_ms", 0L)
                val receiptAt = if (currentEventCount > 0) {
                    priorReceipt.takeIf { it > 0L } ?: prefs.getLong("${kind}_published_at", 0L)
                } else priorReceipt
                if (receiptAt > 0L) {
                    val receiptLabel = when {
                        RawSensorDatedCapturePolicy.showPreviousCapture(currentEventCount, priorReceipt) ->
                            "上次原始資料接收時間"
                        priorReceipt <= 0L -> "手錶發送時間（舊版紀錄）"
                        prefs.getString("${kind}_last_capture_receipt_source", "") == "phone_receive_wall_clock" ->
                            "手機收到原始資料時間"
                        else -> "手錶收到原始事件時間"
                    }
                    val formatted = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(receiptAt))
                    addView(label("$receiptLabel：$formatted；不是已確認的量測時間。", 12f, HealthVisuals.secondary), margins(top = 6))
                    if (RawSensorDatedCapturePolicy.showPreviousCapture(currentEventCount, priorReceipt)) {
                        prefs.getString("${kind}_last_capture_raw_preview", "")
                            ?.takeIf(String::isNotBlank)?.let { preview ->
                                addView(label("上次原始預覽：$preview", 12f, HealthVisuals.secondary), margins(top = 4))
                            }
                    }
                }
                RawSensorDatedCapturePolicy.historicalTime(
                    measurementTime, prefs.getLong("${kind}_last_dated_time", 0L)
                )?.let { lastDated ->
                    val formatted = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastDated))
                    addView(label("上次有時間紀錄的原始擷取：$formatted（歷史紀錄）", 12f, HealthVisuals.secondary), margins(top = 6))
                }
                if (latest.isNotBlank()) {
                    val latestLabel = if (OpticalPulseDisplayPolicy.isOpticalKind(kind)) "最新原始碼值（非 bpm）" else "最新通道"
                    addView(label("$latestLabel：$latest", 12f, HealthVisuals.secondary), margins(top = 6))
                }
                val visibleSummary = if (OpticalPulseDisplayPolicy.isOpticalKind(kind)) {
                    verdict?.headline ?: "已收到光學原始資料；沒有可用的脈率估算"
                } else storedSummary
                addView(label(visibleSummary, 12f, HealthVisuals.secondary), margins(top = 8))
            }, margins(top = 12))
            if (sourceValues.isNotEmpty()) addView(panel().apply {
                val sourceTitle = if (ecgUnmapped) "舊版拆分預覽（非完整事件）" else "Sensor API 事件值預覽（欄位未映射）"
                addView(label(sourceTitle, 18f, Color.WHITE))
                addView(label("$sourceEncoding · ${sourceValues.size} 個值 · 不代表完整 SensorEvent 封包", 12f, HealthVisuals.secondary), margins(top = 6))
                addView(label(
                    if (ecgUnmapped) {
                        "這是舊版 App 將事件固定拆分後留下的前段數值，不能視為完整 SensorEvent 或已解碼 ECG 樣本。格式、單位與取樣時基未確認，因此不繪製波形、不計算品質、RR、心率、HRV 或 mV。"
                    } else {
                        "這是依目前解包規則取出的有限值預覽，可做描述性範圍／波形檢查；不等同完整 SensorEvent 封包。因 OEM 欄位、單位與取樣時鐘未確認，不換算成生理量或疾病結果。"
                    },
                    12f,
                    Color.rgb(185, 195, 210)
                ), margins(top = 7))
                addView(label(sourceValues.take(32).joinToString(", ") { "%.0f".format(it) }, 11f, HealthVisuals.secondary), margins(top = 8))
            }, margins(top = 12))
            if (kind == "ecg") addView(panel().apply {
                addView(label(EcgResearchDisplay.rawRowsPanelTitle(ecgRawEventRows.isNotEmpty()), 18f, Color.WHITE))
                val rawRowStatus = EcgResearchDisplay.rawRowsRetentionLabel(ecgRawValuesStatus)
                addView(label(
                    "$rawRowStatus 保存 ${ecgRawEventRows.size} 列；僅代表 Android callback 收到的 FloatArray 數值，不是二進位 HAL 封包、解碼 ECG 樣本或校準波形。",
                    12f,
                    HealthVisuals.secondary
                ), margins(top = 6))
                addView(label(
                    "解碼狀態：${persistedResearchDecodeState.ifBlank { rawEventInspection?.decodeState ?: "UNKNOWN" }} · 事件 ${rawEventInspection?.eventCount ?: ecgRawEventRows.size} · 欄位 ${rawEventInspection?.layoutComparison?.fieldSummaries?.size ?: 0} · 解碼點 ${rawEventInspection?.decodedSampleCount ?: 0}",
                    13f,
                    Color.WHITE
                ), margins(top = 8))
                val latestRawFields = ecgRawEventRows.lastOrNull()
                    ?.split(',')
                    ?.map { it.trim().toDoubleOrNull() }
                    .orEmpty()
                if (latestRawFields.isNotEmpty()) {
                    addView(label("最新原始欄位（僅位置名稱，語意未驗證）", 13f, Color.WHITE), margins(top = 10))
                    addView(label(
                        latestRawFields.mapIndexed { index, value ->
                            "欄位 ${index + 1}: ${value?.let { "%.6f".format(it) } ?: "無效"}"
                        }.joinToString("\n"),
                        12f,
                        Color.rgb(205, 215, 225)
                    ), margins(top = 5))
                }
                rawEventInspection?.let { inspection ->
                    EcgResearchDisplay.candidateInspectionLines(
                        inspection.candidateValueCount.takeIf { inspection.layoutComparison != null },
                        inspection.layoutComparison,
                        inspection.status,
                        inspection.inspectedFieldValueCount.takeIf { inspection.layoutComparison != null },
                        inspection.eventCount
                    ).forEach { line ->
                        addView(label(line, 12f, Color.rgb(185, 195, 210)), margins(top = 7))
                    }
                    inspection.layoutComparison?.fieldSummaries
                        ?.filter { it.normalizedEventIndexPreview.size >= 2 }
                        ?.forEach { field ->
                            val preview = field.normalizedEventIndexPreview.mapNotNull { value ->
                                value?.takeIf(Double::isFinite)?.toFloat()
                            }
                            if (preview.size >= 2) {
                                addView(label(
                                    "HAL 欄位 ${field.fieldIndex} 相對值 · ${field.finiteValueCount} 個事件；只表示欄位變化，不代表 ECG 波形或生理振幅。",
                                    12f,
                                    HealthVisuals.secondary
                                ), margins(top = 8))
                                addView(MedicalChartView(this@RawSensorDetailActivity).apply {
                                    setWaveform(preview, accent, "ecg_hal_field_${field.fieldIndex}_normalized_event_index")
                                }, LinearLayout.LayoutParams(-1, dp(120)))
                            }
                        }
                }
                ecgRawEventRows.lastOrNull()?.let { row ->
                    addView(label("最新事件列前 600 字：${row.take(600)}${if (row.length > 600) "…（本列其餘內容已省略）" else ""}", 11f, HealthVisuals.secondary), margins(top = 8))
                }
                val eventPreview = EcgRawEventPreview.firstField(ecgRawEventRows)
                if (eventPreview.size >= 2) {
                    addView(label("欄位 0 原始趨勢 · 橫軸事件序號，縱軸未校準欄位值；不是已解碼心電圖或 mV。", 12f, HealthVisuals.secondary), margins(top = 8))
                    addView(MedicalChartView(this@RawSensorDetailActivity).apply {
                        setWaveform(eventPreview, accent, "raw_event_field_0")
                    }, LinearLayout.LayoutParams(-1, dp(160)))
                }
            }, margins(top = 12))
            externalProfile?.let { profile ->
                addView(panel().apply {
                    addView(label("外部參考研究 profile", 19f, Color.rgb(112, 178, 255)))
                    addView(label("${profile.source} · ${profile.points} 點", 13f, Color.WHITE), margins(top = 8))
                    addView(label(
                        "slope=${"%.9f".format(profile.slope)} · offset=${"%.6f".format(profile.offset)} ${profile.targetUnit}\nfit RMSE=${"%.6f".format(profile.fitRmse)} · validation RMSE=${"%.6f".format(profile.validationRmse)}",
                        12f,
                        Color.rgb(175, 210, 230)
                    ), margins(top = 7))
                    rawAverage?.let { raw -> profile.apply(raw) }?.let { calibrated ->
                        addView(label("最新研究映射值 ${"%.4f".format(calibrated)} ${profile.targetUnit} · 單位未經裝置驗證", 18f, Color.WHITE), margins(top = 10))
                    }
                    addView(label("此研究 profile 不等於原廠校準或醫療驗證；不會改寫原始資料。", 12f, Color.rgb(255, 200, 120)), margins(top = 9))
                }, margins(top = 12))
            }
            if (wearQualified == true && kind == "ppg" && profile.rrIntervalsMs.size >= 3) {
                addView(panel().apply {
                    addView(label("RR 研究指標", 19f, Color.WHITE))
                    addView(label(
                        "RR ${profile.rrIntervalsMs.size} · SDNN ${rhythm.sdnn?.let { "%.1f".format(it) } ?: "—"} · RMSSD ${rhythm.rmssd?.let { "%.1f".format(it) } ?: "—"}",
                        14f,
                        Color.rgb(220, 225, 235)
                    ), margins(top = 8))
                    addView(label(ClinicalVerdict.DISCLAIMER, 12f, Color.rgb(255, 200, 120)), margins(top = 8))
                }, margins(top = 12))
            }
            addView(panel().apply {
                addView(label("重要限制", 18f, Color.rgb(255, 190, 95)))
                addView(label(disclaimer(kind), 13f, HealthVisuals.secondary), margins(top = 8))
                if (kind == "ecg" || kind == "ppg") addView(label(
                    "本次研究判讀可能不準確，不能診斷或排除疾病。若有胸痛、暈厥、呼吸困難或持續不適，請就醫；以醫師評估為準。",
                    13f, Color.rgb(255, 200, 120)
                ), margins(top = 8))
                addView(label(ClinicalVerdict.DISCLAIMER, 12f, Color.rgb(255, 200, 120)), margins(top = 10))
            }, margins(top = 12))
            if (kind == "ecg") addView(panel().apply {
                addView(label("ECG 校準狀態", 18f, Color.rgb(255, 190, 95)))
                addView(label("MAX86178 晶片資料表列出 single-lead 能力、約 ±1000 mV 輸入範圍、200 mVpp AC 動態範圍、0.05–40 Hz 參考頻寬與 15.3 ENOB；這些是晶片參考規格，不是 Xiaomi Watch 2 Pro 的實際電極接法或校準值。\n\n官方未提供本手錶的 ADC 至 mV 換算、增益、取樣時鐘或臨床驗證常數，因此目前只保留 Android Sensor API 原始事件值與描述性檢查。", 13f, HealthVisuals.secondary), margins(top = 8))
            }, margins(top = 12))
            if (childCount > resultContentCount) {
                val technicalDetails = LinearLayout(this@RawSensorDetailActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                }
                while (childCount > resultContentCount) {
                    val child = getChildAt(resultContentCount)
                    removeViewAt(resultContentCount)
                    technicalDetails.addView(child)
                }
                addView(label("查看波形與技術資料　⌄", 16f, HealthVisuals.blue).apply {
                    setPadding(0, dp(12), 0, dp(12))
                    setOnClickListener {
                        technicalDetails.visibility = if (technicalDetails.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        text = if (technicalDetails.visibility == View.VISIBLE) "收起波形與技術資料　⌃" else "查看波形與技術資料　⌄"
                    }
                })
                addView(technicalDetails)
            }
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(6, 10, 16))
            clipToPadding = false
            addView(body)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
    }

    private fun disclaimer(kind: String): String = when (kind) {
        "ecg" -> "ECG 缺少導聯與臨床校準，不能診斷房顫、缺血或心律疾病。"
        "bioz" -> "BIOZ 無原廠阻抗換算，不能代表體脂、水分或血糖。"
        "ppg" -> "PPG 只做波形與品質分析，不能直接診斷疾病。"
        "body_temperature_raw", "thermometer" -> "溫度感測原始資料；單位與生理意義未驗證，不作體溫或發燒判定。"
        else -> "原始感測資料僅供研究參考。"
    }

    private fun title(kind: String) = when (kind) {
        "heart_rate_raw", "whs_heart_rate", "max_hr" -> "心率"
        "ppg", "lifeq_ott" -> "脈搏"
        "ecg" -> "心電圖"
        "spo2_raw", "mi_spo2" -> "血氧"
        "bioz", "bioz_respiration" -> "體成分"
        "body_temperature_raw", "thermometer" -> "腕部溫度"
        "pressure" -> "環境氣壓"
        else -> WatchSensorCatalog.items.firstOrNull { it.kind == kind }?.label ?: kind
    }
    private fun wearLine(wearQualified: Boolean?): String = when (wearQualified) {
        true -> "佩戴校正：合格"
        false -> "佩戴校正：未貼合"
        null -> "佩戴校正：未知"
    }
    private fun accent(kind: String) = when (kind) {
        "ppg" -> Color.rgb(255, 80, 115)
        "ecg" -> Color.rgb(80, 220, 170)
        "bioz" -> Color.rgb(135, 115, 255)
        else -> HealthVisuals.blue
    }

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(14, 22, 34))
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    private fun margins(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }
}

internal object RawSensorFormulaDisplayPolicy {
    internal data class Eligibility(
        val showOpticalMetrics: Boolean,
        val showHrvMetrics: Boolean,
        val showEcgPulse: Boolean,
        val showEnvironmentMetrics: Boolean
    )

    fun evaluate(
        kind: String,
        samples: Int,
        status: String,
        measuredAtMillis: Long,
        timestampSource: String?,
        hrvTimestampSource: String?,
        sessionStartedAtMillis: Long,
        nowMillis: Long,
        qualityScore: Int,
        sampleRateHz: Float,
        channelMappingVerified: Boolean,
        formulaPulseBpm: Float?,
        hrvMeasuredAtMillis: Long,
        hrvSourceKind: String,
        hrvSampleCount: Int,
        hrvRrCount: Int,
        ecgResearchOnly: Boolean,
        hasOpticalMetrics: Boolean,
        hasHrvMetrics: Boolean,
        hasEnvironmentMetrics: Boolean,
        wearQualified: Boolean? = null,
        waveformEncoding: String? = null
    ): Eligibility {
        val displayState = SensorDisplayState.from(
            samples = samples,
            status = status,
            measuredAt = measuredAtMillis,
            now = nowMillis,
            freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
            kind = kind,
            timestampSource = timestampSource
        )
        val sameFreshSession = sessionStartedAtMillis > 0L && measuredAtMillis >= sessionStartedAtMillis &&
            displayState in setOf(
                SensorDisplayState.AVAILABLE,
                SensorDisplayState.SIGNAL_VALID_RESEARCH,
                SensorDisplayState.CALIBRATION_MISSING
            )
        val opticalEstimate = if (sameFreshSession && hasOpticalMetrics) {
            OpticalPulseDisplayPolicy.researchEstimateBpm(
                kind = kind,
                estimateBpm = formulaPulseBpm,
                qualityScore = qualityScore,
                status = status,
                measuredAtMillis = measuredAtMillis,
                nowMillis = nowMillis,
                sampleRateHz = sampleRateHz,
                channelMappingVerified = channelMappingVerified,
                timestampSource = timestampSource,
                wearQualified = wearQualified
            )
        } else null
        val hrvEligible = sameFreshSession && hasHrvMetrics && hrvSourceKind == kind &&
            hrvMeasuredAtMillis == measuredAtMillis && HrvSourceValidity.isFreshAndQualified(
                sourceKind = hrvSourceKind,
                measuredAt = hrvMeasuredAtMillis,
                timestampSource = hrvTimestampSource,
                now = nowMillis,
                status = status,
                qualityScore = qualityScore,
                sampleRateHz = sampleRateHz,
                sampleCount = hrvSampleCount,
                rrCount = hrvRrCount,
                channelMappingVerified = channelMappingVerified,
                wearQualified = wearQualified,
                waveformEncoding = waveformEncoding
            )
        val isEcg = kind == "ecg"
        return Eligibility(
            showOpticalMetrics = !isEcg && opticalEstimate != null,
            showHrvMetrics = hrvEligible,
            showEcgPulse = isEcg && ecgResearchOnly && formulaPulseBpm != null && hrvEligible,
            showEnvironmentMetrics = hasEnvironmentMetrics && !isEcg && !OpticalPulseDisplayPolicy.isOpticalKind(kind)
        )
    }
}
