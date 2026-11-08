package com.jaysato.xiaomihealthbridge

import android.content.Context
import java.text.DateFormat

/**
 * Calculates sensor values and compares them to [ClinicalReferenceTables],
 * producing non-diagnostic health conclusions for every modality we can read.
 */
object HealthConclusionEngine {
    data class Item(
        val id: String,
        val title: String,
        val valueText: String,
        val clinicalCompare: String,
        val conclusion: String,
        val level: Level,
        val score: Int?,
        val available: Boolean,
        val evidenceCategory: EvidenceCategory = EvidenceCategory.RESEARCH_SIGNAL
    ) {
        init {
            require(score == null || score in 1..100)
        }

        val userState: UserState
            get() = if (!available) UserState.UNAVAILABLE else when (level) {
                Level.GOOD -> UserState.NORMAL
                Level.WATCH, Level.ATTENTION -> UserState.ATTENTION
                Level.RESEARCH -> UserState.RESEARCH
                Level.UNAVAILABLE -> UserState.UNAVAILABLE
            }
    }

    enum class Level { GOOD, WATCH, ATTENTION, UNAVAILABLE, RESEARCH }
    enum class EvidenceCategory { RESEARCH_SIGNAL, RECORDED_PHYSIOLOGICAL }
    enum class UserState { NORMAL, ATTENTION, RESEARCH, UNAVAILABLE }

    private data class HeartRateCandidate(
        val bpm: Double,
        val measuredAt: Long,
        val source: String,
        val estimate: Boolean
    )

    data class Report(
        val items: List<Item>,
        val overallHeadline: String,
        val overallDetail: String,
        val disclaimer: String = ClinicalVerdict.DISCLAIMER
    )

    fun build(context: Context): Report {
        val raw = try {
            RawSensorStore.open(context)
        } catch (error: Exception) {
            return unavailableReport("感測資料儲存讀取例外：${error.javaClass.simpleName}")
        }
        val feature = try {
            context.getSharedPreferences(WearDataProcessor.FEATURE_PREFS, Context.MODE_PRIVATE)
        } catch (error: Exception) {
            return unavailableReport("健康資料讀取例外：${error.javaClass.simpleName}")
        }
        val items = buildCoreSensorItems(raw, feature) + listOf(
            buildSafely("hrv", "HRV") { hrvConclusion(raw) },
            buildSafely("pressure", "氣壓高度") { pressureConclusion(raw, feature) },
            buildSafely("sleep", "睡眠") {
                sleepConclusion(context.getSharedPreferences("health_summary_query_diagnostics", Context.MODE_PRIVATE))
            },
            buildSafely("blood_pressure", "血壓") { bloodPressureConclusion(context, raw) },
            buildSafely("glucose", "血糖") { glucoseSlot() }
        )
        val summary = HealthConclusionSummaryPolicy.summarize(items, items.size)
        return Report(items, summary.first, summary.second)
    }

    internal fun buildCoreSensorItems(
        raw: android.content.SharedPreferences,
        feature: android.content.SharedPreferences,
        safeBuild: (String, String, () -> Item) -> Item = { id, title, calculate ->
            buildSafely(id, title, calculate)
        }
    ): List<Item> = listOf(
            safeBuild("heart", "心率") { heartConclusion(raw, feature) },
            safeBuild("spo2", "血氧") { spo2Conclusion(raw) },
            safeBuild("ecg", "ECG") { ecgConclusion(raw) },
            safeBuild("temperature", "體表溫度") { temperatureConclusion(raw, feature) },
            safeBuild("bioz", "BioZ") { biozConclusion(raw) },
            safeBuild("ppg", "PPG") { ppgConclusion(raw) }
        )

    internal fun buildSafely(id: String, title: String, calculate: () -> Item): Item = try {
        calculate()
    } catch (error: Exception) {
        Item(
            id = id,
            title = title,
            valueText = "分析暫時不可用",
            clinicalCompare = "單項分析例外：${error.javaClass.simpleName}；未替代或推估原始資料。",
            conclusion = "此項暫無法判定；其他健康項目仍會繼續分析。",
            level = Level.UNAVAILABLE,
            score = null,
            available = false
        )
    }

    internal fun unavailableReport(reason: String): Report {
        val items = listOf("心率", "血氧", "ECG", "HRV", "體表溫度", "氣壓高度", "BioZ", "PPG", "睡眠", "血壓", "血糖")
            .mapIndexed { index, title ->
                Item(
                    id = "analysis_unavailable_$index",
                    title = title,
                    valueText = "資料讀取暫時不可用",
                    clinicalCompare = reason,
                    conclusion = "讀取資料時發生問題；請重試，原始紀錄不會被改寫。",
                    level = Level.UNAVAILABLE,
                    score = null,
                    available = false
                )
            }
        return Report(items, "健康分析資料暫時無法讀取", reason)
    }

    private fun trustedSensorTime(raw: android.content.SharedPreferences, kind: String): Long =
        raw.getLong("${kind}_time", 0L).takeIf {
            HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(raw.getString("${kind}_timestamp_source", null))
        } ?: 0L

    internal fun heartConclusion(raw: android.content.SharedPreferences, feature: android.content.SharedPreferences): Item {
        val now = System.currentTimeMillis()
        val candidate = feature.getFloat("whs_heart_rate", Float.NaN).takeIf {
            it in 30f..220f && HealthAnalysisFreshnessPolicy.isFreshMeasurement(
                feature.getLong("whs_heart_rate_timestamp", 0L),
                feature.getString("whs_heart_rate_timestamp_source", null),
                now,
                TEN_MINUTES
            )
        }?.let {
            HeartRateCandidate(it.toDouble(), feature.getLong("whs_heart_rate_timestamp", 0L), "Health Services", false)
            }
            ?: raw.getFloat("whs_heart_rate_average", Float.NaN).takeIf {
                it in 30f..220f && HealthAnalysisFreshnessPolicy.isFreshMeasurement(
                    raw.getLong("whs_heart_rate_time", 0L),
                    raw.getString("whs_heart_rate_timestamp_source", null),
                    now,
                    TEN_MINUTES
                )
            }?.let {
                HeartRateCandidate(it.toDouble(), raw.getLong("whs_heart_rate_time", 0L), "手錶心率", false)
            }
            ?: raw.getFloat("health_connect_heart_rate_bpm", Float.NaN).takeIf {
                it in 30f..220f &&
                    raw.getString("health_connect_heart_rate_origin", "").orEmpty().isNotBlank() &&
                    now - raw.getLong("health_connect_heart_rate_time", 0L) in 0..TEN_MINUTES
            }?.let {
                HeartRateCandidate(it.toDouble(), raw.getLong("health_connect_heart_rate_time", 0L),
                    "Health Connect 紀錄", false)
            }
            ?: raw.getFloat("heart_rate_raw_average", Float.NaN).takeIf {
                it in 30f..220f && raw.getString("heart_rate_raw_status", "") in setOf("has_data", "valid_signal") &&
                    HealthAnalysisFreshnessPolicy.isFreshSensor(
                        raw.getLong("heart_rate_raw_time", 0L),
                        raw.getString("heart_rate_raw_timestamp_source", null),
                        now,
                        TEN_MINUTES
                    )
            }?.let {
                HeartRateCandidate(
                    it.toDouble(), raw.getLong("heart_rate_raw_time", 0L), "手錶心率感測通道", false
                )
            }
            ?: raw.getFloat("ppg_formula_pulse_bpm", Float.NaN).takeIf {
                it in 30f..220f && raw.getString("ppg_status", "") == "valid_signal" &&
                    raw.getBoolean("ppg_wear_qualified", false) &&
                    raw.getBoolean("ppg_channel_mapping_verified", false) && raw.getInt("ppg_raw_value_count", 0) >= 64 &&
                    raw.getInt("ppg_quality_score", 0) >= 60 && raw.getFloat("ppg_sample_rate", 0f) >= 25f &&
                    HealthAnalysisFreshnessPolicy.isFreshSensor(
                        trustedSensorTime(raw, "ppg"), raw.getString("ppg_timestamp_source", null), now
                    )
            }?.let {
                HeartRateCandidate(it.toDouble(), trustedSensorTime(raw, "ppg"), "PPG 研究估算", true)
            }
            ?: raw.getFloat("max_hr_formula_pulse_bpm", Float.NaN).takeIf {
                it in 30f..220f && raw.getString("max_hr_status", "") == "valid_signal" &&
                    raw.getBoolean("max_hr_wear_qualified", false) &&
                    raw.getBoolean("max_hr_channel_mapping_verified", false) && raw.getInt("max_hr_raw_value_count", 0) >= 64 &&
                    raw.getInt("max_hr_quality_score", 0) >= 70 && raw.getFloat("max_hr_sample_rate", 0f) >= 25f &&
                    HealthAnalysisFreshnessPolicy.isFreshSensor(
                        trustedSensorTime(raw, "max_hr"), raw.getString("max_hr_timestamp_source", null), now
                    )
            }?.let {
                HeartRateCandidate(it.toDouble(), trustedSensorTime(raw, "max_hr"), "MAX86178 光學研究估算", true)
            }
        if (candidate == null) {
            val historyCandidate = listOfNotNull(
                raw.getFloat("health_connect_heart_rate_bpm", Float.NaN).takeIf {
                    it in 30f..220f && now - raw.getLong("health_connect_heart_rate_time", 0L) > TEN_MINUTES &&
                        raw.getLong("health_connect_heart_rate_time", 0L) in 1L..now &&
                        !raw.getString("health_connect_heart_rate_origin", "").isNullOrBlank()
                }?.let {
                    HeartRateCandidate(it.toDouble(), raw.getLong("health_connect_heart_rate_time", 0L),
                        "Health Connect (${raw.getString("health_connect_heart_rate_origin", "")})", false)
                },
                feature.getFloat("whs_heart_rate", Float.NaN).takeIf {
                    it in 30f..220f && HealthAnalysisFreshnessPolicy.isFreshMeasurement(
                        feature.getLong("whs_heart_rate_timestamp", 0L),
                        feature.getString("whs_heart_rate_timestamp_source", null),
                        now,
                        Long.MAX_VALUE
                    )
                }
                    ?.let { HeartRateCandidate(it.toDouble(), feature.getLong("whs_heart_rate_timestamp", 0L), "Health Services", false) },
                raw.getFloat("whs_heart_rate_average", Float.NaN).takeIf {
                    it in 30f..220f && HealthAnalysisFreshnessPolicy.isFreshMeasurement(
                        raw.getLong("whs_heart_rate_time", 0L),
                        raw.getString("whs_heart_rate_timestamp_source", null),
                        now,
                        Long.MAX_VALUE
                    )
                }
                    ?.let { HeartRateCandidate(it.toDouble(), raw.getLong("whs_heart_rate_time", 0L), "手錶心率", false) }
            ).filter {
                val age = now - it.measuredAt
                it.measuredAt > 0L && age > TEN_MINUTES
            }.maxByOrNull { it.measuredAt }
            val explanation = if (historyCandidate != null) {
                val recordedAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(java.util.Date(historyCandidate.measuredAt))
                "有較舊的心率紀錄（${historyCandidate.source}，$recordedAt），但超出本頁 10 分鐘分析輸入窗；不作目前結論。"
            } else {
                "目前沒有符合本頁 10 分鐘分析輸入窗的心率紀錄；請在手錶量測或佩戴同步。"
            }
            if (historyCandidate != null) {
                return Item(
                    "hr", "心率", "%.0f bpm · 歷史紀錄".format(historyCandidate.bpm),
                    "來源：${historyCandidate.source} · 已超出目前 10 分鐘分析輸入窗。",
                    explanation, Level.RESEARCH, null, true, EvidenceCategory.RECORDED_PHYSIOLOGICAL
                )
            }
            return Item("hr", "心率", "無法判定（無近期可信讀值）", ClinicalReferenceTables.RESTING_HR_BPM.compare(null),
                explanation, Level.UNAVAILABLE, null, false)
        }
        return heartRateItem(candidate.bpm, candidate.source, candidate.measuredAt, candidate.estimate)
    }

    internal fun heartRateItem(
        bpm: Double,
        source: String? = null,
        measuredAt: Long? = null,
        researchEstimate: Boolean = false
    ): Item {
        val plausible = ClinicalReferenceTables.PLAUSIBLE_HR_BPM.contains(bpm)
        val provenance = if (!source.isNullOrBlank() && measuredAt != null && measuredAt > 0L) {
            "來源：$source · 量測時間：${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(java.util.Date(measuredAt))}。 "
        } else ""
        val compare = provenance + if (plausible) "未與靜息心率範圍比較：目前資料未證明是在靜止狀態量測。"
        else "讀值超出工程可置信範圍。"
        val level = if (plausible) Level.RESEARCH else Level.ATTENTION
        val conclusion = if (plausible) {
            if (researchEstimate) "此為 $source，不等同經確認的心率或靜息心率；不套用安靜心率臨床參考帶。"
            else "近期心率讀值；不是經確認的靜息心率，不套用安靜心率臨床參考帶。需要靜息值時，請在安靜休息後量測。"
        } else {
            "讀值超出工程可置信窗，可能為訊號或接觸異常；請在靜止、佩戴穩定時重測。"
        }
        val value = if (researchEstimate) "%.0f bpm · 研究估算".format(bpm) else "%.0f bpm".format(bpm)
        return Item("hr", "心率", value, compare, conclusion, level, null, true,
            if (researchEstimate) EvidenceCategory.RESEARCH_SIGNAL else EvidenceCategory.RECORDED_PHYSIOLOGICAL)
    }

    internal fun spo2Conclusion(raw: android.content.SharedPreferences): Item {
        val rawKind = if (
            raw.getInt("spo2_raw_value_count", 0) > 0 ||
            raw.getInt("spo2_raw_event_count", 0) > 0 ||
            raw.getString("spo2_raw_waveform", "").orEmpty().isNotBlank()
        ) "spo2_raw" else "mi_spo2"
        val samples = raw.getInt("${rawKind}_raw_value_count", 0)
        val rawMeasuredAt = raw.getLong("${rawKind}_time", 0L)
        val measuredAt = raw.getLong("spo2_trusted_time", 0L)
        // Never invent SpO2 % from uncalibrated HAL — only conclude when external/trusted % exists.
        val reference = raw.getFloat("spo2_trusted_percent", Float.NaN).takeIf {
            it in 70f..100f && measuredAt > 0L &&
                measuredAt <= System.currentTimeMillis() &&
                raw.getString("spo2_trusted_source", "") in setOf("health_connect", "validated_oximeter")
        }?.toDouble()
        if (reference == null) {
            return rawSpO2Item(
                samples = samples,
                eventCount = raw.getInt("${rawKind}_event_count", 0),
                measuredAt = rawMeasuredAt,
                timestampSource = raw.getString("${rawKind}_timestamp_source", null),
                now = System.currentTimeMillis(),
                waveform = SensorSuiteCsvParser.parseFloatsWithGapMarkers(
                    raw.getString("${rawKind}_waveform", "").orEmpty()
                ),
                source = raw.getString("${rawKind}_source_sensor", rawKind).orEmpty()
            )
        }
        val origin = raw.getString("spo2_trusted_origin", "Health Connect") ?: "Health Connect"
        val recordedAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(java.util.Date(measuredAt))
        return Item(
            "spo2", "血氧 SpO₂", "%.0f%% · 已記錄".format(reference),
            "來源：$origin · 記錄時間：$recordedAt · Health Connect 新鮮度規則尚未設定。",
            "顯示來源紀錄供參考；因尚未設定並驗證此來源的時間窗，不標示為目前值、不分級、不計分。",
            Level.RESEARCH, null, true, EvidenceCategory.RECORDED_PHYSIOLOGICAL
        )
    }

    internal fun persistSpO2Reference(
        raw: android.content.SharedPreferences,
        percent: Float,
        measuredAt: Long,
        origin: String
    ): Boolean {
        if (!isValidSpO2Reference(percent, measuredAt, origin)) return false
        raw.edit()
            .putFloat("spo2_trusted_percent", percent)
            .putLong("spo2_trusted_time", measuredAt)
            .putString("spo2_trusted_source", "health_connect")
            .putString("spo2_trusted_origin", origin)
            .apply()
        return true
    }

    internal fun isValidSpO2Reference(percent: Float, measuredAt: Long, origin: String): Boolean =
        percent in 70f..100f && measuredAt > 0L && measuredAt <= System.currentTimeMillis() && origin.isNotBlank()

    internal fun rawSpO2Item(
        samples: Int,
        measuredAt: Long,
        timestampSource: String?,
        now: Long,
        waveform: List<Float> = emptyList(),
        source: String = "unknown",
        eventCount: Int = 0
    ): Item {
        val trustedTimestamp = HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(timestampSource)
        val finitePreviewCount = waveform.count { it.isFinite() && it != 524287f }
        val hasEvidence = samples > 0 || eventCount > 0 || finitePreviewCount > 0
        val fresh = hasEvidence && HealthAnalysisFreshnessPolicy.isFreshSensor(
            measuredAt, timestampSource, now, TEN_MINUTES
        )
        val quality = Spo2RawQualityPolicy.assess(waveform, 0f)
        val waveformEvidence = if (waveform.isNotEmpty()) {
            Spo2RawQualityPolicy.summary(quality)
        } else {
            "沒有保存可分析波形預覽；原始事件數與預覽點數分開保留"
        }
        val counts = "原始樣本 $samples · 原始事件 $eventCount · 預覽有效點 $finitePreviewCount/${waveform.size}"
        val showDetailedCounts = eventCount > 0 || waveform.isNotEmpty()
        val valueText = when {
            !hasEvidence -> "0 筆原始值 · 無法判定"
            fresh && quality.score != null -> if (showDetailedCounts) {
                "$counts · 索引形狀品質 ${ResearchScoreDisplay.format(quality.score)}（非血氧）"
            } else "$samples 筆原始值（無 %）"
            fresh && waveform.isNotEmpty() -> "$counts · 形狀品質未評分"
            fresh -> if (showDetailedCounts) "$counts · 無百分比" else "$samples 筆原始值（無 %）"
            trustedTimestamp && measuredAt > 0L && now >= measuredAt -> if (showDetailedCounts) {
                "$counts · 歷史資料（無 %）"
            } else "$samples 筆歷史原始值（無 %）"
            else -> if (showDetailedCounts) "$counts · 時間未確認（無 %）" else "$samples 筆原始值（時間未確認，無 %）"
        }
        val provenance = "來源：$source · 時間來源：${timestampSource ?: "UNKNOWN"} · 校準：未驗證 · 時間軸：NORMALIZED_INDEX"
        val note = when {
            !hasEvidence -> "尚未收到可用的 SpO₂ 原始事件或波形預覽。"
            fresh -> "已保存 SpO₂ 原始通道；以下只分析波形形狀與有限樣本，不推算血氧百分比。"
            !trustedTimestamp -> "已保留 SpO₂ 原始事件，但時間來源未驗證，不判定為近期量測。"
            measuredAt <= 0L -> "已保留 SpO₂ 原始事件，但缺少量測時間，不判定為近期量測。"
            now < measuredAt -> "已保留 SpO₂ 原始事件，但時間戳晚於目前時間，不判定為近期量測。"
            else -> "上次 SpO₂ 原始量測已超出健康結論 10 分鐘分析輸入窗；請重新量測。"
        }
        return Item(
            "spo2", "血氧 SpO₂", valueText,
            "${ClinicalReferenceTables.SPO2_NORMAL_PCT.compare(null)} · $provenance · $waveformEvidence",
            "$note $waveformEvidence。無裝置級校準，不推算血氧百分比；臨床參考：健康成人海平面約 95–100%，原始訊號品質不等同血氧數值。",
            if (hasEvidence) Level.RESEARCH else Level.UNAVAILABLE,
            null,
            hasEvidence
        )
    }

    private fun ecgConclusion(raw: android.content.SharedPreferences): Item = ecgConclusion(
        samples = raw.getInt("ecg_raw_value_count", 0),
        eventCount = raw.getInt("ecg_event_count", 0),
        qualityScore = raw.getInt("ecg_quality_score", 0),
        hr = raw.getFloat("ecg_rhythm_bpm", Float.NaN).takeIf { it.isFinite() }?.toDouble(),
        rrCount = raw.getInt("ecg_rhythm_rr_count", 0),
        titleRhythm = raw.getString("ecg_rhythm_title", null),
        measuredAt = trustedSensorTime(raw, "ecg"),
        now = System.currentTimeMillis(),
        sampleRate = raw.getFloat("ecg_sample_rate", 0f),
        status = raw.getString("ecg_status", "").orEmpty(),
        wearQualified = if (raw.contains("ecg_wear_qualified")) raw.getBoolean("ecg_wear_qualified", false) else null,
        waveformEncoding = raw.getString("ecg_waveform_encoding", ""),
        timelineSource = raw.getString("ecg_research_timeline_source", "NORMALIZED_INDEX").orEmpty(),
        timestampSource = raw.getString("ecg_timestamp_source", null),
        decodedEventCount = if (EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
            raw.getString("ecg_research_waveform_encoding", null),
            raw.getString("ecg_research_decode_status", null)
        ))
            raw.getInt("ecg_research_decoded_event_count", 0) else 0,
        decodedValueCount = if (EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
            raw.getString("ecg_research_waveform_encoding", null),
            raw.getString("ecg_research_decode_status", null)
        ))
            raw.getInt("ecg_research_decoded_value_count", 0) else 0
    )

    internal fun ecgConclusion(
        samples: Int,
        eventCount: Int = 0,
        qualityScore: Int,
        hr: Double?,
        rrCount: Int,
        titleRhythm: String?,
        measuredAt: Long,
        now: Long,
        sampleRate: Float = 0f,
        status: String = "",
        wearQualified: Boolean? = null,
        waveformEncoding: String? = null,
        timelineSource: String = "NORMALIZED_INDEX",
        timestampSource: String? = null,
        decodedEventCount: Int = 0,
        decodedValueCount: Int = 0
    ): Item {
        val decoded = decodedEventCount > 0 && decodedEventCount <= eventCount && decodedValueCount > 0
        val evidencePoints = if (decoded) maxOf(samples, decodedValueCount) else samples
        val provenance = buildString {
            append("來源：公開 Sensor HAL 原始通道")
            append(" · 時間來源：${timestampSource?.takeIf(String::isNotBlank) ?: "UNKNOWN"}")
            append(" · 時間軸：$timelineSource")
            append(
                if (waveformEncoding == "ecg_external_reference_mv") {
                    " · 振幅：外部參考研究映射"
                } else {
                    " · 振幅校準：未驗證"
                }
            )
        }
        if (evidencePoints <= 0 || measuredAt <= 0L || now - measuredAt !in 0..TEN_MINUTES) {
            if (evidencePoints > 0 || eventCount > 0) {
                val evidence = if (decoded) "$eventCount 個原始事件 · $decodedEventCount 個研究解碼事件／$decodedValueCount 點"
                    else if (samples > 0) "$samples 點歷史原始資料" else "$eventCount 個歷史原始事件"
                return Item(
                    "ecg", "ECG", evidence, "尚未取得可驗證 ECG 波形與研究 RR · $provenance",
                    (if (decoded) "近期保存子集已研究解碼；不代表所有事件都已解碼。來源時間或校準尚未驗證，不輸出 bpm、AF 或正常／異常判斷。"
                    else "原始證據已保存（$evidence），但量測時間已過期、時間來源無效或事件尚未形成可判讀波形；請重新量測。") + ECG_RESEARCH_SAFETY_NOTE,
                    Level.RESEARCH, null, true
                )
            }
            return Item("ecg", "ECG", "0 點／0 事件 · 無法判定", "需單導波形與研究 RR",
                if (samples <= 0) "尚未取得 ECG；請雙指接觸電極量測。" else "上次 ECG 已超出健康結論 10 分鐘分析輸入窗；請重新量測後再查看波形分析。",
                Level.UNAVAILABLE, null, false)
        }
        val compare = buildString {
            append(if (decoded) "研究解碼 $decodedEventCount/$eventCount 個事件 · $decodedValueCount 點" else "原始樣本 $samples")
            append(" · 工程訊號品質 ${ResearchScoreDisplay.format(qualityScore)}")
            if (rrCount > 0) append(" · 研究 RR 間隔數 $rrCount")
            append(" · $provenance · 無裝置級臨床驗證")
        }
        if (samples <= 0 && decoded) {
            return Item(
                "ecg", "ECG", "$decodedValueCount 點 · 研究解碼", compare,
                "本次取得 $eventCount 個 ECG 原始事件，其中 $decodedEventCount 個事件形成 $decodedValueCount 個研究波形點。已能確認資料存在；逐點時鐘與裝置校準尚未驗證，因此不輸出 bpm 或正常／異常心律判讀。請查看波形並保持電極接觸後重測。$ECG_RESEARCH_SAFETY_NOTE",
                Level.RESEARCH, null, true
            )
        }
        if (samples < MIN_ECG_SAMPLES) {
            return Item(
                "ecg", "ECG", "$samples 點 · 研究資料", compare,
                "已收到 ECG 原始資料，但樣本數低於工程最低完整性門檻 $MIN_ECG_SAMPLES；不輸出心率品質分級或正向結論。$ECG_RESEARCH_SAFETY_NOTE",
                Level.RESEARCH, null, true
            )
        }
        val estimateEligible = ClinicalVerdict.isEcgPulseDisplayable(
            qualityScore = qualityScore,
            sampleCount = samples,
            sampleRate = sampleRate,
            status = status,
            rrCount = rrCount,
            wearQualified = wearQualified,
            heartRateBpm = hr,
            waveformEncoding = waveformEncoding,
            timelineSource = timelineSource
        )
        val value = if (estimateEligible) "%.0f bpm · 研究估算 · RR $rrCount".format(hr) else "$samples 點 · 原始研究資料"
        val conclusion = if (estimateEligible) {
            "此數值僅為通過工程品質門檻的研究估算，不等同校準心率或醫療 ECG 判讀；不可用於診斷。$ECG_RESEARCH_SAFETY_NOTE"
        } else {
            "波形已收到，但工程訊號品質或 RR 資料未達估算門檻；隱藏 bpm，請靜止並保持電極接觸後重測。$ECG_RESEARCH_SAFETY_NOTE"
        }
        return Item("ecg", "ECG", value, compare, conclusion, Level.RESEARCH, null, true)
    }

    internal fun hrvConclusion(raw: android.content.SharedPreferences): Item {
        val now = System.currentTimeMillis()
        val hrvTime = raw.getLong("hrv_research_time", 0L)
        val hrvSource = raw.getString("hrv_research_source_kind", "").orEmpty()
        val ppgFresh = raw.getString("ppg_status", "") == "valid_signal" &&
            raw.getBoolean("ppg_wear_qualified", false) && raw.getBoolean("ppg_channel_mapping_verified", false) && raw.getInt("ppg_raw_value_count", 0) >= 32 &&
            raw.getInt("ppg_quality_score", 0) >= 70 && raw.getFloat("ppg_sample_rate", 0f) > 64f &&
            HealthAnalysisFreshnessPolicy.isFresh(trustedSensorTime(raw, "ppg"), now)
        val researchFresh = hrvSource != "ecg" && HrvSourceValidity.isFreshAndQualified(
            sourceKind = hrvSource,
            measuredAt = hrvTime,
            timestampSource = raw.getString("hrv_research_timestamp_source", null),
            now = now,
            status = raw.getString("${hrvSource}_status", "").orEmpty(),
            qualityScore = raw.getInt("${hrvSource}_quality_score", 0),
            sampleRateHz = raw.getFloat("${hrvSource}_sample_rate", 0f),
            sampleCount = raw.getInt("hrv_research_samples", 0),
            rrCount = raw.getInt("hrv_research_rr_count", 0),
            channelMappingVerified = raw.getBoolean("${hrvSource}_channel_mapping_verified", false),
            wearQualified = if (raw.contains("${hrvSource}_wear_qualified")) raw.getBoolean("${hrvSource}_wear_qualified", false) else null,
            waveformEncoding = raw.getString("${hrvSource}_waveform_encoding", null)
        )
        val rmssd = raw.getFloat("hrv_research_formula_rmssd_ms", Float.NaN).takeIf { it.isFinite() && researchFresh }?.toDouble()
            ?: raw.getFloat("ppg_formula_rmssd_ms", Float.NaN).takeIf { it.isFinite() && ppgFresh }?.toDouble()
            ?: raw.getFloat("max_hr_formula_rmssd_ms", Float.NaN).takeIf {
                it.isFinite() && raw.getString("max_hr_status", "") == "valid_signal" &&
                    raw.getBoolean("max_hr_wear_qualified", false) && raw.getBoolean("max_hr_channel_mapping_verified", false) &&
                    raw.getInt("max_hr_quality_score", 0) >= 70 && raw.getFloat("max_hr_sample_rate", 0f) > 64f &&
                    raw.getInt("max_hr_raw_value_count", 0) >= 32 &&
                    HealthAnalysisFreshnessPolicy.isFresh(trustedSensorTime(raw, "max_hr"), now)
            }?.toDouble()
        val sdnn = raw.getFloat("hrv_research_formula_sdnn_ms", Float.NaN).takeIf { it.isFinite() && researchFresh }?.toDouble()
            ?: raw.getFloat("ppg_formula_sdnn_ms", Float.NaN).takeIf { it.isFinite() && ppgFresh }?.toDouble()
        val stress = when {
            researchFresh && raw.contains("hrv_research_formula_stress_score") ->
                raw.getInt("hrv_research_formula_stress_score", -1).takeIf { it >= 0 }
            ppgFresh && raw.contains("ppg_formula_stress_score") ->
                raw.getInt("ppg_formula_stress_score", -1).takeIf { it >= 0 }
            else -> null
        }
        if (rmssd == null && sdnn == null) {
            val diagnosticSource = when {
                hrvSource.isNotBlank() -> hrvSource
                raw.getInt("ppg_raw_value_count", 0) > 0 -> "ppg"
                raw.getInt("max_hr_raw_value_count", 0) > 0 -> "max_hr"
                raw.getInt("ecg_raw_value_count", 0) > 0 -> "ecg"
                else -> ""
            }
            val diagnosticFromHrv = diagnosticSource == hrvSource && hrvSource.isNotBlank()
            val reason = HrvSourceValidity.unavailableReason(
                sourceKind = diagnosticSource,
                measuredAt = if (diagnosticFromHrv) hrvTime else raw.getLong("${diagnosticSource}_time", 0L),
                now = now,
                status = raw.getString("${diagnosticSource}_status", "").orEmpty(),
                qualityScore = raw.getInt("${diagnosticSource}_quality_score", 0),
                sampleRateHz = raw.getFloat("${diagnosticSource}_sample_rate", 0f),
                sampleCount = if (diagnosticFromHrv) raw.getInt("hrv_research_samples", 0)
                    else raw.getInt("${diagnosticSource}_raw_value_count", 0),
                rrCount = raw.getInt("hrv_research_rr_count", 0),
                channelMappingVerified = raw.getBoolean("${diagnosticSource}_channel_mapping_verified", false),
                wearQualified = if (raw.contains("${diagnosticSource}_wear_qualified")) raw.getBoolean("${diagnosticSource}_wear_qualified", false) else null,
                waveformEncoding = raw.getString("${diagnosticSource}_waveform_encoding", null)
            )
            val diagnosticSamples = if (diagnosticFromHrv) raw.getInt("hrv_research_samples", 0)
                else raw.getInt("${diagnosticSource}_raw_value_count", 0)
            if (diagnosticSamples > 0) {
                return Item(
                    "hrv", "HRV／壓力", "$diagnosticSamples 筆歷史原始樣本",
                    ClinicalReferenceTables.HRV_RMSSD_MS.compare(null), reason,
                    Level.RESEARCH, null, true
                )
            }
            return Item("hrv", "HRV／壓力", "$diagnosticSamples 筆原始樣本 · 無法判定", ClinicalReferenceTables.HRV_RMSSD_MS.compare(null),
                reason, Level.UNAVAILABLE, null, false)
        }
        val compare = buildList {
            rmssd?.let { add(ClinicalReferenceTables.HRV_RMSSD_MS.compare(it)) }
            sdnn?.let { add(ClinicalReferenceTables.HRV_SDNN_MS.compare(it)) }
            stress?.let { add("研究壓力指數 $it/100（高 nRMSSD → 較低分數）") }
        }.joinToString("；")
        val (level, conclusion, score) = when {
            rmssd != null && ClinicalReferenceTables.HRV_RMSSD_MS.contains(rmssd) ->
                Triple(Level.GOOD, "短時 RMSSD 落在公開研究常見帶；可作恢復／壓力粗參考，非診斷。", 80)
            rmssd != null && rmssd < ClinicalReferenceTables.HRV_RMSSD_MS.min ->
                Triple(Level.WATCH, "RMSSD 偏低於研究常見帶；可能與壓力、疲勞或量測過短有關，休息後重測。", 55)
            else ->
                Triple(Level.RESEARCH, "已計算 HRV 指標；腕式／短時資料不可直接套用臨床 5 分鐘 ECG 標準。", 60)
        }
        val value = buildString {
            rmssd?.let { append("RMSSD %.0f ms".format(it)) }
            sdnn?.let { if (isNotEmpty()) append(" · "); append("SDNN %.0f ms".format(it)) }
            stress?.let { if (isNotEmpty()) append(" · "); append("壓力 $it") }
        }
        return Item("hrv", "HRV／壓力", value, compare, conclusion, level, score, true)
    }

    internal fun temperatureConclusion(raw: android.content.SharedPreferences, feature: android.content.SharedPreferences): Item {
        val now = System.currentTimeMillis()
        val temperatureChannels = listOf(
            TemperaturePresentation.Channel(
                source = "thermometer",
                value = SensorSuiteCsvParser.parse(raw.getString("thermometer_latest", "").orEmpty())
                    .firstOrNull(),
                measuredAt = raw.getLong("thermometer_time", 0L),
                timestampSource = raw.getString("thermometer_timestamp_source", null),
                status = raw.getString("thermometer_status", "unknown").orEmpty(),
                count = raw.getInt("thermometer_raw_value_count", 0),
                waveform = SensorSuiteCsvParser.parseFloatsWithGapMarkers(
                    raw.getString("thermometer_waveform", "").orEmpty()
                )
            ),
            TemperaturePresentation.Channel(
                source = "feature",
                value = feature.getFloat("temperature", Float.NaN).toDouble(),
                measuredAt = feature.getLong("temperature_timestamp", 0L),
                timestampSource = feature.getString("temperature_timestamp_source", null),
                status = "feature_snapshot",
                count = 0,
                waveform = emptyList(),
                unitStatus = feature.getString("temperature_unit_status", "unverified").orEmpty(),
                aggregationMethod = feature.getString("temperature_aggregation_method", "unreported").orEmpty(),
                aggregationSampleCount = feature.getInt("temperature_aggregation_sample_count", 0)
            ),
            TemperaturePresentation.Channel(
                source = "body_temperature_raw",
                value = raw.getFloat("body_temperature_raw_average", Float.NaN).toDouble(),
                measuredAt = raw.getLong("body_temperature_raw_time", 0L),
                timestampSource = raw.getString("body_temperature_raw_timestamp_source", null),
                status = raw.getString("body_temperature_raw_status", "unknown").orEmpty(),
                count = raw.getInt("body_temperature_raw_value_count", 0),
                waveform = SensorSuiteCsvParser.parseFloatsWithGapMarkers(
                    raw.getString("body_temperature_raw_waveform", "").orEmpty()
                )
            )
        )
        val selected = TemperaturePresentation.select(temperatureChannels, now)
        val source = when (selected?.source) {
            "thermometer" -> raw.getString("thermometer_source_sensor", "thermometer").orEmpty()
            "feature" -> feature.getString("temperature_source", "Health Services").orEmpty()
            "body_temperature_raw" -> raw.getString("body_temperature_raw_source_sensor", "原始感測器").orEmpty()
            else -> "NO_FINITE_TEMPERATURE_SOURCE"
        }
        val receivedAt = when (selected?.source) {
            "thermometer" -> raw.getLong("thermometer_published_at", 0L)
            "body_temperature_raw" -> raw.getLong("body_temperature_raw_published_at", 0L)
            else -> 0L
        }
        return temperatureResearchItem(
            value = selected?.value,
            measuredAt = selected?.measuredAt ?: 0L,
            source = source,
            now = now,
            timestampSource = selected?.timestampSource ?: "UNKNOWN",
            receivedAt = receivedAt
        )
    }

    internal fun temperatureResearchItem(
        value: Double?,
        measuredAt: Long,
        source: String,
        now: Long,
        timestampSource: String = "android_sensor_event_elapsed_realtime",
        receivedAt: Long = 0L
    ): Item {
        if (value == null || !value.isFinite()) {
            return Item("temp", "溫度原始值", "無法判定（無有限裝置讀值）", "單位未驗證；未作生理參考比較",
                "沒有可用的有限裝置讀值。", Level.UNAVAILABLE, null, false)
        }
        val trustedMeasurementTime = measuredAt > 0L &&
            HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource) && measuredAt <= now
        val measurementTimeOrigin = if (timestampSource == HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE)
            "Health Services 資料點時間" else "感測事件時間"
        val timeDescription = when {
            trustedMeasurementTime && now - measuredAt <= 24 * 60 * 60 * 1000L ->
                "量測時間 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(java.util.Date(measuredAt))}（$measurementTimeOrigin）"
            trustedMeasurementTime ->
                "歷史量測時間 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(java.util.Date(measuredAt))}（超過 24 小時）"
            receivedAt > 0L && receivedAt <= now ->
                "接收時間 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(java.util.Date(receivedAt))}（不是量測時間；新鮮度未知）"
            else -> "量測時間未知；僅能按本次序列索引檢視"
        }
        return Item(
            "temp", "溫度原始值", "裝置原始值 %.1f · %s".format(value, source),
            "單位未驗證；未作生理參考比較 · $timeDescription",
            "研究資料：保留裝置原始讀值；通道單位與整機校準未知，此數值不是體溫，不對照體溫參考帶。",
            Level.RESEARCH, null, true
        )
    }

    internal fun pressureConclusion(raw: android.content.SharedPreferences, feature: android.content.SharedPreferences): Item {
        val now = System.currentTimeMillis()
        val pressureFresh = HealthAnalysisFreshnessPolicy.isFresh(trustedSensorTime(raw, "pressure"), now)
        val rawHpa = raw.getFloat("pressure_average", Float.NaN)
            .takeIf { it.isFinite() && it in 300f..1100f }?.toDouble()
        val hpa = rawHpa?.takeIf { pressureFresh }
        val alt = feature.getFloat("altitude", Float.NaN).takeIf {
            it.isFinite() && HealthAnalysisFreshnessPolicy.isFreshSensor(
                feature.getLong("altitude_timestamp", 0L),
                feature.getString("altitude_timestamp_source", null),
                now,
                TEN_MINUTES
            )
        }?.toDouble()
            ?: raw.getFloat("pressure_formula_altitude_m", Float.NaN).takeIf { it.isFinite() }?.toDouble()
        if (!pressureFresh || hpa == null) {
            if (rawHpa != null) {
                val measuredAt = trustedSensorTime(raw, "pressure")
                val timeDescription = when {
                    measuredAt <= 0L -> "量測時間未確認"
                    measuredAt > now -> "時間戳在未來"
                    else -> "歷史量測 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(java.util.Date(measuredAt))}"
                }
                val source = raw.getString("pressure_source_sensor", "手錶氣壓感測器").orEmpty()
                return Item(
                    "pressure", "氣壓／高度", "%.1f hPa · 歷史讀值".format(rawHpa),
                    "來源：$source · $timeDescription · 非即時值；不計算目前高度。",
                    "保留已取得的氣壓原始讀值；時間來源不可信或已過期，不視為目前量測。氣壓不能單獨判定樓層或活動爬升。",
                    Level.RESEARCH, null, true
                )
            }
            return Item("pressure", "氣壓／高度", "—", "標準大氣壓力高度估算",
                if (raw.getLong("pressure_time", 0L) == 0L) "尚未取得氣壓計讀值。" else "氣壓讀值時間來源未確認或已過期；請重新量測。",
                Level.UNAVAILABLE, null, false)
        }
        val value = buildString {
            hpa?.let { append("%.1f hPa".format(it)) }
            alt?.let { if (isNotEmpty()) append(" · "); append("標準大氣壓力高度估算 %.0f m".format(it)) }
        }
        return Item(
            "pressure", "氣壓／高度", value,
            "依標準海平面氣壓公式換算；不是當地校準海拔，也不是活動基準爬升。",
            "顯示氣壓量測及公式估算值；天氣與室內壓力變化會影響高度估算，不據此判定樓層或爬升。",
            Level.RESEARCH, null, true
        )
    }

    internal fun biozConclusion(raw: android.content.SharedPreferences): Item {
        val samples = raw.getInt("bioz_raw_value_count", 0)
        val measuredAt = trustedSensorTime(raw, "bioz")
        val age = System.currentTimeMillis() - measuredAt
        val savedWaveform = SensorSuiteCsvParser.parseFloatsWithGapMarkers(
            raw.getString("bioz_display_waveform", "").orEmpty()
        )
        val waveformPreviewPointCount = savedWaveform.count(Float::isFinite)
        val hasSavedWaveform = waveformPreviewPointCount > 0
        val recomputed = savedWaveform.takeIf { it.isNotEmpty() }
            ?.let { BiomedicalCalibration.calibrateBioz(emptyList(), it) }
        val cacheVersionMatches = raw.getString("bioz_algorithm_version", null) == BiomedicalCalibration.BIOZ_RESEARCH_VERSION
        val cachedWaveformPosition = raw.getInt("bioz_waveform_median_position_index", -1)
            .takeIf { cacheVersionMatches && it in 0..100 }
        val cachedRelativeChange = raw.getFloat("bioz_relative_change_percent", Float.NaN)
            .takeIf { cacheVersionMatches && it.isFinite() }
        val cachedStability = raw.getInt("bioz_stability_score", -1)
            .takeIf { cacheVersionMatches && it in 0..100 }
        val cachedCompleteness = raw.getInt("bioz_confidence_score", -1)
            .takeIf { cacheVersionMatches && it in 1..100 }
        val waveformPosition = cachedWaveformPosition ?: recomputed?.waveformMedianPositionIndex
        val relativeChange = cachedRelativeChange ?: recomputed?.relativeChangePercent
        val stability = cachedStability ?: recomputed?.stabilityScore
        val completeness = cachedCompleteness ?: recomputed?.confidenceScore
        val recomputedMetricShown = cachedWaveformPosition == null && recomputed?.waveformMedianPositionIndex != null ||
            cachedRelativeChange == null && recomputed?.relativeChangePercent != null ||
            cachedStability == null && recomputed?.stabilityScore != null ||
            cachedCompleteness == null && recomputed?.confidenceScore != null
        val researchMetrics = buildList {
            waveformPosition?.let { add("視窗內相對中位位置 $it/100") }
            relativeChange?.let { add("首尾區段相對振幅差 %+.1f%%（非跨次趨勢）".format(it)) }
            stability?.let { add("波形相對離散度 $it%（標準差／波形全幅；非時間穩定度／信心）") }
            completeness?.let { add("波形資料完整度 ${ResearchScoreDisplay.format(it)}") }
        }.joinToString(" · ")
        if ((samples <= 0 && !hasSavedWaveform) || age !in 0..TEN_MINUTES) {
            if (samples > 0 || hasSavedWaveform) {
                val timeState = when {
                    measuredAt <= 0L -> "時間未確認的原始資料"
                    age < 0L -> "時間戳異常的原始資料"
                    else -> "歷史原始資料"
                }
                val retainedEvidence = buildList {
                    if (samples > 0) add("$samples 點$timeState")
                    if (hasSavedWaveform) add("保存波形預覽 $waveformPreviewPointCount 點")
                    if (researchMetrics.isNotBlank()) add(researchMetrics)
                }.joinToString(" · ")
                return Item(
                    "bioz", "體成分／BioZ", retainedEvidence,
                    "時間軸：NORMALIZED_INDEX · 量測時間未確認或不在即時視窗；" + ClinicalReferenceTables.SEGMENTAL_WRIST_Z_OHM.compare(null),
                    "BioZ 研究指標如有顯示，皆為相對振幅特徵並使用索引時間軸；" +
                        when {
                            measuredAt <= 0L -> "量測時間未知，不表示目前讀值。"
                            age < 0L -> "時間戳異常，不表示目前讀值。"
                            else -> "資料超出即時分析窗。"
                        } + " 腕部局部阻抗缺少 OEM Ω 校準，不能換算體脂％。",
                    Level.RESEARCH, null, true
                )
            }
            return Item("bioz", "體成分／BioZ", "$samples 點原始資料 · 無法判定", ClinicalReferenceTables.SEGMENTAL_WRIST_Z_OHM.compare(null),
                if (samples <= 0) "尚未量測 BioZ；Samsung BIA 體脂於此裝置無 OEM Ω 公式。" else "BioZ 原始波形已超出健康結論 10 分鐘分析輸入窗；需重新量測。腕部局部阻抗不能換算體脂％。",
                Level.UNAVAILABLE, null, false)
        }
        val evidenceSummary = buildList {
            add(if (samples > 0) "$samples 點原始" else "原始樣本計數 0")
            if (hasSavedWaveform) add("保存波形預覽 $waveformPreviewPointCount 點")
            if (researchMetrics.isNotBlank()) add(researchMetrics)
        }.joinToString(" · ")
        return Item(
            "bioz", "體成分／BioZ", evidenceSummary,
            "研究指標採視窗內相對振幅特徵與 NORMALIZED_INDEX 時間軸；腕部局部阻抗尚無 OEM Ω 校準。",
            "已取得 BioZ 研究波形${if (researchMetrics.isNotBlank()) "；$researchMetrics" else "，目前沒有足夠波形可計算衍生研究指標"}。${if (recomputedMetricShown) "缺少的摘要快取值由手機保存波形依 ${BiomedicalCalibration.BIOZ_RESEARCH_VERSION} 重算。" else ""}這些是相對訊號指標，不代表 Ω、體脂或體水。",
            Level.RESEARCH, null, true
        )
    }

    internal fun ppgConclusion(raw: android.content.SharedPreferences): Item {
        val cachedPeriod = raw.getInt("ppg_dominant_period_samples", 0).takeIf { it > 0 }
        val cachedCorrelation = raw.getString("ppg_period_correlation", null)?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in -1.0..1.0 }
        val recomputedMetrics = if (cachedPeriod == null || cachedCorrelation == null) {
            recomputePpgIndexMetrics(raw)
        } else null
        return ppgConclusion(
            status = raw.getString("ppg_status", "").orEmpty(),
            reason = raw.getString("ppg_reason", "").orEmpty(),
            samples = raw.getInt("ppg_raw_value_count", 0),
            eventCount = raw.getInt("ppg_event_count", 0),
            qualityScore = raw.getInt("ppg_quality_score", 0),
            sampleRateHz = raw.getFloat("ppg_sample_rate", 0f),
            channelMappingVerified = raw.getBoolean("ppg_channel_mapping_verified", false),
            wearQualified = if (raw.contains("ppg_wear_qualified")) raw.getBoolean("ppg_wear_qualified", false) else null,
            measuredAt = trustedSensorTime(raw, "ppg"),
            piPercent = raw.getFloat("ppg_formula_pi_percent", Float.NaN),
            pulseBpm = raw.getFloat("ppg_formula_pulse_bpm", Float.NaN),
            now = System.currentTimeMillis(),
            indexPeriodSamples = cachedPeriod ?: recomputedMetrics?.first,
            periodCorrelation = cachedCorrelation ?: recomputedMetrics?.second,
            confidenceScore = raw.getInt("ppg_confidence_score", 0).takeIf { it in 1..100 },
            source = raw.getString("ppg_source_sensor", "MAX86178 HAL 原始光學通道").orEmpty(),
            timestampSource = raw.getString("ppg_timestamp_source", "UNKNOWN").orEmpty()
        )
    }

    private fun recomputePpgIndexMetrics(raw: android.content.SharedPreferences): Pair<Int?, Double?>? {
        val waveform = SensorSuiteCsvParser.parseFloatsWithGapMarkers(
            raw.getString("ppg_display_waveform", "").orEmpty()
        )
        if (waveform.isEmpty()) return null
        val result = BiomedicalCalibration.refinePpg(
            raw = waveform,
            sampleRateHz = 0f,
            timestampSource = raw.getString("ppg_timestamp_source", "UNKNOWN").orEmpty(),
            channelMappingVerified = false,
            wearQualified = false
        )
        return result.dominantPeriodSamples to result.periodCorrelation
    }

    internal fun ppgConclusion(
        status: String,
        reason: String,
        samples: Int,
        eventCount: Int,
        qualityScore: Int,
        sampleRateHz: Float,
        channelMappingVerified: Boolean = false,
        measuredAt: Long,
        piPercent: Float,
        pulseBpm: Float,
        now: Long,
        wearQualified: Boolean? = null,
        indexPeriodSamples: Int? = null,
        periodCorrelation: Double? = null,
        confidenceScore: Int? = null,
        source: String = "MAX86178 HAL 原始光學通道",
        timestampSource: String = "UNKNOWN"
    ): Item {
        val age = now - measuredAt
        val timestampTrusted = HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(timestampSource)
        if (samples <= 0 || measuredAt <= 0L || !timestampTrusted || age !in 0..TEN_MINUTES) {
            val detail = when {
                samples <= 0 -> "尚未取得 PPG 波形。"
                measuredAt <= 0L -> "PPG 量測時間未確認；保留樣本索引與相對週期，不換算實際時間或 bpm。"
                !timestampTrusted -> "PPG 時間來源未驗證；保留樣本索引與相對週期，不輸出目前生理數值。"
                age < 0L -> "PPG 時間戳在未來；保留原始索引資料，不作目前量測判定。"
                else -> "PPG 原始訊號已超出健康結論 10 分鐘分析輸入窗；請重新量測後查看。"
            }
            if (samples > 0 || eventCount > 0) {
                val value = buildString {
                    if (samples > 0) append(when {
                        measuredAt <= 0L -> "$samples 點原始值 · 時間未確認"
                        !timestampTrusted -> "$samples 點原始值 · 時間來源未驗證"
                        age < 0L -> "$samples 點原始值 · 時間戳異常"
                        else -> "$samples 筆歷史原始值"
                    })
                    if (eventCount > 0) { if (isNotEmpty()) append(" · "); append("$eventCount 事件") }
                    indexPeriodSamples?.takeIf { it > 0 }?.let { append(" · 索引週期 $it 點／週期") }
                }
                val timeAndIndex = buildList {
                    add("時間軸：NORMALIZED_INDEX（非秒）")
                    add("來源：$source · 時間來源：$timestampSource · 校準：未驗證")
                    periodCorrelation?.takeIf { it.isFinite() && it in -1.0..1.0 }
                        ?.let { add("最大自相關 %.2f".format(it)) }
                    add("品質 ${ResearchScoreDisplay.format(qualityScore)} · 資料完整度 ${ResearchScoreDisplay.format(confidenceScore)}")
                }.joinToString(" · ")
                return Item("ppg", "PPG 光學", value, "${ClinicalReferenceTables.PPG_RATE_HZ.compare(null)} · $timeAndIndex",
                    detail + " 原始證據仍保留，不等同可換算脈率。", Level.RESEARCH, null, true)
            }
            return Item("ppg", "PPG 光學", "0 點／0 事件 · 無法判定", ClinicalReferenceTables.PPG_RATE_HZ.compare(null),
                detail, Level.UNAVAILABLE, null, false)
        }

        val legacyUnknownTimebase = sampleRateHz <= 0f && status == "invalid_optical" &&
            (reason.contains("取樣") || reason.contains("時鐘") || reason.contains("通道映射"))
        val wearUnconfirmed = wearQualified != true
        val rawOnly = !channelMappingVerified || wearUnconfirmed || !timestampTrusted || status in setOf(
            "research_signal_unscaled", "research_optical", "unscaled_optical", "uncalibrated", "needs_oem"
        ) || legacyUnknownTimebase
        val trustedPulse = pulseBpm.takeIf {
            !rawOnly && wearQualified == true && status == "valid_signal" && it.isFinite() && it in 30f..220f &&
                qualityScore >= 60 && samples >= 64 && sampleRateHz >= 25f
        }?.toDouble()
        val pi = piPercent.takeIf {
            !rawOnly && wearQualified == true && status == "valid_signal" && it.isFinite() && qualityScore >= 60 &&
                samples >= 64 && sampleRateHz >= 25f
        }
        val flattenedValues = samples
        val researchEvidence = buildList {
            indexPeriodSamples?.takeIf { it > 0 }?.let { period ->
                add("索引域週期 $period 點／週期（非時間／bpm）")
            }
            periodCorrelation?.takeIf { it.isFinite() && it in -1.0..1.0 }?.let {
                add("最大自相關 %.2f".format(it))
            }
            add("品質 ${ResearchScoreDisplay.format(qualityScore)}")
            add("資料完整度 ${ResearchScoreDisplay.format(confidenceScore)}")
            add("來源：$source · 時間：$timestampSource · 校準：未驗證")
        }.joinToString(" · ")
        val compare = when {
            rawOnly -> buildString {
                append("原始封包 $eventCount · 展平值 $flattenedValues · $researchEvidence")
                if (wearUnconfirmed) append(" · 佩戴狀態未確認")
                if (sampleRateHz <= 0f || !channelMappingVerified || !timestampTrusted) append(" · 時間來源／取樣時基／通道映射未驗證")
                append(" · 不計算脈率、HRV 或灌注指數")
            }
            else -> buildString {
                append("品質指標 ${ResearchScoreDisplay.format(qualityScore)} · 原始值 $flattenedValues")
                pi?.let { append(" · PI %.2f%%".format(it)) }
                trustedPulse?.let { append(" · 研究脈率 %.0f bpm；未證明靜息，不套用靜息參考帶".format(it)) }
                append(" · $researchEvidence")
            }
        }
        if (rawOnly) {
            return Item(
                "ppg", "PPG 光學", buildString {
                    append("研究原始訊號")
                    indexPeriodSamples?.takeIf { it > 0 }?.let { append(" · $samples 點 · 索引週期 $it 點") }
                },
                compare,
                if (wearUnconfirmed) {
                    "已收到 PPG 原始資料，但手錶佩戴狀態未知或判定離腕；不輸出脈率、HRV 或灌注指數。請戴妥手錶後重新量測。"
                } else if (!timestampTrusted) {
                    "已收到 PPG 原始資料，但時間來源未驗證；保留索引域研究輸出，不輸出目前脈率、HRV 或灌注指數。"
                } else {
                    "手錶有傳來 PPG 原始資料，但目前沒有可信取樣時基／通道映射；這不是量測失敗，也不能換算 bpm、HRV、SpO₂ 或疾病風險。請使用手錶／Health Services 的心率讀值。"
                },
                Level.RESEARCH, null, true
            )
        }

        val (level, conclusion, score) = when {
            trustedPulse != null && qualityScore >= 70 ->
                Triple(Level.RESEARCH, "已取得研究脈率；未證明靜息狀態，不進行靜息心率分級或健康評分。", null)
            status == "valid_signal" && qualityScore >= 60 ->
                Triple(Level.RESEARCH, "訊號品質啟發式檢查通過；取樣與裝置校準限制仍適用，不作診斷。", qualityScore)
            else ->
                Triple(Level.RESEARCH, "已收到訊號，但品質／取樣條件未通過；請貼合錶帶並保持靜止後重測。訊號問題不是身體異常判定。", null)
        }
        return Item("ppg", "PPG 光學", trustedPulse?.let { "%.0f bpm".format(it) } ?: "$flattenedValues 筆原始值",
            compare, conclusion, level, score?.takeIf { it > 0 }, true)
    }

    internal fun sleepConclusion(summary: android.content.SharedPreferences): Item {
        val hours = summary.getString("sleep_session_fallback_hours", "")?.toDoubleOrNull()
        val records = summary.getInt("sleep_complete_history_records", 0)
        if (hours != null && hours.isFinite() && hours > 0.0 && records > 0) {
            return Item(
                "sleep", "睡眠", "近 7 日 %.1f 小時紀錄區間".format(hours),
                "近 7 日共 $records 筆 Health Connect 睡眠 session；重疊區間已合併。",
                "這是睡眠紀錄區間，不等同實睡時長或睡眠分期；保留數值供歷史觀察。",
                Level.RESEARCH, null, true
            )
        }
        return Item(
            "sleep", "睡眠", "—",
            "Apple／Samsung／Fitbit 提供分期；本裝置無經驗證 REM／深睡 API。",
            "尚未取得可核實的睡眠紀錄；不推估睡眠分期或品質分數。",
            Level.UNAVAILABLE, null, false
        )
    }

    private fun bloodPressureConclusion(context: Context, raw: android.content.SharedPreferences): Item {
        val cuffPrefs = context.getSharedPreferences(OpticalBpActivity.CUFF_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val hcPrefs = context.getSharedPreferences(BloodPressureEvidencePolicy.HC_PREFS, Context.MODE_PRIVATE)
        val evidence = BloodPressureEvidencePolicy.newestValid(listOf(
            BloodPressureEvidencePolicy.Evidence(
                cuffPrefs.getFloat("manual_cuff_sbp", Float.NaN).toDouble(),
                cuffPrefs.getFloat("manual_cuff_dbp", Float.NaN).toDouble(),
                cuffPrefs.getLong("manual_cuff_time", 0L), "手動袖帶",
                BloodPressureEvidencePolicy.Origin.MANUAL_CUFF, "cuff"
            ),
            BloodPressureEvidencePolicy.Evidence(
                hcPrefs.getString("systolic", null)?.toDoubleOrNull() ?: Double.NaN,
                hcPrefs.getString("diastolic", null)?.toDoubleOrNull() ?: Double.NaN,
                hcPrefs.getLong("measured_at", 0L), hcPrefs.getString("source", "").orEmpty(),
                BloodPressureEvidencePolicy.Origin.HEALTH_CONNECT,
                hcPrefs.getString("measurement_method", null)
            )
        ), now)
        if (evidence == null) {
            return Item(
                "bp", "血壓（袖帶）", "—",
                ClinicalReferenceTables.AHA_SBP_NORMAL_MMHG.compare(null),
                "沒有 7 天內有效的袖帶或 Health Connect 讀值；不以手錶心率／PPG 推算血壓。",
                Level.UNAVAILABLE, null, false
            )
        }
        val sbp = evidence.systolic
        val dbp = evidence.diastolic
        val provenance = "${evidence.source} · ${DateFormat.getDateTimeInstance().format(java.util.Date(evidence.measuredAt))}"
        if (evidence.researchOnly) {
            return Item(
                "bp", "血壓（Health Connect／研究）", "%.0f / %.0f mmHg".format(sbp, dbp),
                "$provenance · 量測方法：${evidence.measurementMethod ?: "未知"}",
                "研究資料；Health Connect 來源不代表袖帶或臨床驗證，不作醫療診斷。",
                Level.RESEARCH, null, true
            )
        }
        val category = OpticalBloodPressureApi.categorize(sbp.toDouble(), dbp.toDouble())
        val level = when (category) {
            OpticalBloodPressureApi.Category.NORMAL -> Level.GOOD
            OpticalBloodPressureApi.Category.ELEVATED, OpticalBloodPressureApi.Category.LOW -> Level.WATCH
            OpticalBloodPressureApi.Category.STAGE1 -> Level.WATCH
            OpticalBloodPressureApi.Category.STAGE2, OpticalBloodPressureApi.Category.HYPERTENSIVE_CRISIS -> Level.ATTENTION
            OpticalBloodPressureApi.Category.UNKNOWN -> Level.RESEARCH
        }
        val score = when (category) {
            OpticalBloodPressureApi.Category.NORMAL -> 80
            OpticalBloodPressureApi.Category.ELEVATED -> 60
            OpticalBloodPressureApi.Category.STAGE1 -> 45
            OpticalBloodPressureApi.Category.STAGE2 -> 30
            OpticalBloodPressureApi.Category.HYPERTENSIVE_CRISIS -> 15
            OpticalBloodPressureApi.Category.LOW -> 50
            OpticalBloodPressureApi.Category.UNKNOWN -> 40
        }
        return Item(
            "bp", "血壓（袖帶）",
            "%.0f / %.0f mmHg".format(sbp, dbp),
            "$provenance · 手動袖帶單次讀值 · ACC/AHA 分類參考：${OpticalBloodPressureApi.categoryLabel(category)} · " +
                ClinicalReferenceTables.AHA_SBP_NORMAL_MMHG.compare(sbp.toDouble()),
            "單次讀值不能診斷高血壓；需按臨床建議跨日重複測量。若有嚴重症狀請立即求助。",
            level, score, true, EvidenceCategory.RECORDED_PHYSIOLOGICAL
        )
    }

    private fun glucoseSlot() = Item(
        "glucose", "血糖", "不可用",
        "無手錶內建 CGM 公開 API。",
        "結論：無法計算血糖／糖化結論。",
                Level.UNAVAILABLE, null, false
    )

    private const val TEN_MINUTES = HealthAnalysisFreshnessPolicy.RECENT_SENSOR_INPUT_MILLIS
    private const val MIN_ECG_SAMPLES = 32
    private const val ECG_RESEARCH_SAFETY_NOTE = "工程品質分數只表示訊號品質，不是診斷準確率；此研究通道未經 AF 分類性能驗證，不能據此判定正常／異常。若反覆心悸或感到心跳不規則，保存記錄並聯絡醫療人員評估；胸痛／胸悶、呼吸困難或昏厥請立即尋求急救。不要依本研究結果自行停藥或調藥。"
    private const val SEVEN_DAYS = 7 * 24 * 60 * 60 * 1000L
}

internal object HealthConclusionSummaryPolicy {
    fun shouldShowScore(item: HealthConclusionEngine.Item): Boolean =
        item.score?.let { it > 0 } == true && item.level in setOf(
            HealthConclusionEngine.Level.GOOD,
            HealthConclusionEngine.Level.WATCH,
            HealthConclusionEngine.Level.ATTENTION
        )

    fun summarize(items: List<HealthConclusionEngine.Item>, slotCount: Int): Pair<String, String> {
        val comparable = items.filter {
            it.available && it.level in setOf(
                HealthConclusionEngine.Level.GOOD,
                HealthConclusionEngine.Level.WATCH,
                HealthConclusionEngine.Level.ATTENTION
            )
        }
        val recorded = items.count {
            it.available && it.level == HealthConclusionEngine.Level.RESEARCH &&
                it.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL
        }
        val research = items.count {
            it.available && it.level == HealthConclusionEngine.Level.RESEARCH &&
                it.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RESEARCH_SIGNAL
        }
        val attention = comparable.count { it.level == HealthConclusionEngine.Level.ATTENTION }
        val watch = comparable.count { it.level == HealthConclusionEngine.Level.WATCH }
        val good = comparable.count { it.level == HealthConclusionEngine.Level.GOOD }
        val headline = when {
            comparable.isEmpty() && recorded > 0 -> "已有 $recorded 項生理數值紀錄；缺少近期可對照量測"
            comparable.isEmpty() && research > 0 -> "目前只有研究級原始訊號，沒有足夠可對照的生理數值"
            comparable.isEmpty() -> "尚無足夠感測資料可下結論"
            attention > 0 -> "有 $attention 項指標偏離公開臨床參考帶，請留意並視情況就醫"
            watch > 0 -> "多數指標在參考帶內，另有 $watch 項需持續觀察"
            good > 0 -> "已量測指標大致落在公開臨床參考帶內（研究參考）"
            else -> "目前沒有可對照的生理數值"
        }
        val detail = "可對照生理數值 ${comparable.size}/$slotCount 項；歷史／未分級生理紀錄 $recorded 項；研究級原始訊號 $research 項。良好 $good · 觀察 $watch · 需留意 $attention。非醫療診斷。"
        return headline to detail
    }
}
