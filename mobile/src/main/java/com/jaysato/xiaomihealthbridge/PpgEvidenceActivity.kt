package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.jaysato.xiaomihealthbridge.engine.IndexDomainPpgFilter
import com.jaysato.xiaomihealthbridge.engine.PpgCompositionQualityEngine
import com.jaysato.xiaomihealthbridge.engine.EcgDecoderReadiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class PpgEvidenceActivity : ComponentActivity() {
    private lateinit var body: LinearLayout
    private var loadJob: Job? = null
    private var selectedKind = "ppg"

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(12, 16, 24)); addView(body) })
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        loadJob?.cancel()
        body.removeAllViews()
        body.addView(Button(this).apply { text = "返回"; setOnClickListener { finish() } })
        label("AILISA · 感測資料整理", 25f)
        for ((kind, title) in listOf("ppg" to "PPG", "ecg" to "ECG", "bioz" to "BioZ",
                "spo2_raw" to "SpO₂ 原始", "mi_spo2" to "小米 SpO₂ 原始",
                "thermometer" to "溫度", "body_temperature_raw" to "體表原始通道")) {
            body.addView(Button(this).apply {
                text = if (selectedKind == kind) "$title · 已選取" else title
                setOnClickListener { selectedKind = kind; load() }
            })
        }
        label(when (selectedKind) {
            "thermometer", "body_temperature_raw" -> "溫度原始讀值按收到順序檢視；不保證是連續波形。單位及整機校準未驗證。"
            "spo2_raw", "mi_spo2" -> "SpO₂ 原始光學預覽按索引呈現；不代表完整 ADC 串流或血氧百分比。"
            else -> "收到的預覽不等於完整 ADC 串流。曲線橫軸是預覽索引；縱軸為未校準原始編碼。"
        })
        body.addView(Button(this).apply {
            text = "開始 ${selectedKind} 量測"
            setOnClickListener { startActivity(Intent(this@PpgEvidenceActivity, ClinicalLabActivity::class.java)
                .putExtra("preferred_spo2_kind", selectedKind.takeIf { it in setOf("spo2_raw", "mi_spo2") })
                .putExtra("auto", when (selectedKind) {
                    "thermometer", "body_temperature_raw" -> "temperature"
                    "spo2_raw", "mi_spo2" -> "oxygen"
                    else -> selectedKind
                })) }
        })
        val status = label("正在讀取已保存資料…")
        loadJob = lifecycleScope.launch {
            try {
                val dao = SignalEvidenceDatabase.get(this@PpgEvidenceActivity).evidence()
                val source = "wear-data-layer-$selectedKind-preview"
                val recentSessions = dao.recentSessions(source)
                val latestEvidence = dao.latestSessionWithRawEvidence(source)
                val sessions = (recentSessions + listOfNotNull(latestEvidence)).distinctBy { it.sessionId }
                status.text = if (sessions.isEmpty()) "尚無 Room 保存預覽；請啟動新量測並重新整理。舊偏好資料不會冒充新保存資料。" else "最近量測 ${recentSessions.size} 筆；另保留最近一次有原始資料的歷史紀錄"
                for (session in sessions) {
                    val rows = dao.readPreview(session.sessionId)
                    val values = rows.map { it.rawEncoding.toDoubleOrNull() }
                    val metadata = JSONObject(session.metadataJson)
                    if (session.sessionId == latestEvidence?.sessionId && recentSessions.none { it.sessionId == session.sessionId }) {
                        label("以下為先前有原始資料的歷史紀錄，不代表本次量測或目前健康狀態。", 16f)
                    }
                    if (selectedKind in setOf("ppg", "bioz", "spo2_raw", "mi_spo2")) {
                        val packetFields = withContext(Dispatchers.Default) {
                            RawPacketChannelDecoder.decode(metadata.optString("event_raw_values_rows"))
                        }
                        label("封包結構解析：${packetFields.status} · 收到 ${packetFields.receivedEventCount} 個事件／成功解碼 ${packetFields.eventCount} 個。欄位不是已驗證的生理通道。")
                        packetFields.channels.forEachIndexed { index, channel ->
                            val finite = channel.filter(Double::isFinite)
                            val stats = if (finite.isEmpty()) "無有限值" else
                                "均值 ${"%.3f".format(finite.average())} · 範圍 ${"%.3f".format(finite.min())}–${"%.3f".format(finite.max())}"
                            label("原始欄位 $index · 有限值 ${finite.size}/${channel.size} · $stats（原始編碼；可能含狀態／計數欄位）", 14f)
                        }
                    }
                    val decoded = if (selectedKind == "ecg") withContext(Dispatchers.Default) {
                        val times = metadata.optJSONArray("event_timestamps_ns")
                        EcgHalEventResearchDecoder.decode(
                            metadata.optString("event_raw_values_rows"),
                            if (times == null) emptyList() else List(times.length()) { index -> times.optLong(index) }
                        )
                    } else null
                    if (decoded != null) {
                        val sourceTimes = metadata.optJSONArray("event_timestamps_ns")?.let { array ->
                            List(array.length()) { array.optLong(it) }
                        }.orEmpty()
                        val producerContractVerified = EcgPayloadInterpretationPolicy.VERIFIED_ECG_EVENT_CONTRACT_AVAILABLE
                        val readiness = EcgDecoderReadiness.assess(
                            decodedSampleCount = decoded.decodedSampleCount,
                            mappingVerified = decoded.layoutComparison?.mappingVerified == true,
                            sourceClockVerified = producerContractVerified && sourceTimes.size >= 2 &&
                                sourceTimes.all { it > 0L } && sourceTimes.zipWithNext().all { (before, after) -> after > before },
                            sampleRateVerified = producerContractVerified && metadata.optBoolean("reported_rate_available")
                        )
                        label("ECG_STATUS = ${readiness.status} · 阻塞：${readiness.blockers.joinToString("、")}。原始事件仍保留；不影響其他健康功能。", 14f)
                    }
                    val biozResearch = if (selectedKind == "bioz") withContext(Dispatchers.Default) {
                        BiozRawFieldResearch.analyze(metadata.optString("event_raw_values_rows"), session.source)
                    } else null
                    if (biozResearch != null) {
                        dao.appendOrVerifyDerived(BiozRawFieldResearch.toDerivedRecord(
                            sessionId = session.sessionId,
                            source = session.source,
                            timestampSource = metadata.optString("timestamp_source", "UNKNOWN"),
                            sourceTimestampMillis = metadata.optLong("source_reported_timestamp_ms").takeIf { it > 0L },
                            publishedAtMillis = metadata.optLong("published_at_ms").takeIf { it > 0L },
                            result = biozResearch
                        ))
                    }
                    val chartValues = decoded?.waveform?.takeIf { it.isNotEmpty() } ?: values
                    val ecgFieldSummaries = decoded?.layoutComparison?.fieldSummaries.orEmpty()
                    val ecgFieldResearch = decoded?.let(EcgEventFieldResearch::summarize)
                    if (selectedKind == "ecg" && decoded != null && ecgFieldResearch != null) {
                        EcgEventFieldResearch.toDerivedRecord(
                            sessionId = session.sessionId,
                            source = session.source,
                            sourceTimestampSource = metadata.optString("timestamp_source", "UNKNOWN"),
                            sourceTimestampMillis = metadata.optLong("source_reported_timestamp_ms").takeIf { it > 0L },
                            publishedAtMillis = metadata.optLong("published_at_ms").takeIf { it > 0L },
                            sourceSnapshotRows = metadata.optString("event_raw_values_rows"),
                            summary = ecgFieldResearch
                        )?.let { dao.appendOrVerifyDerived(it) }
                    }
                    if (decoded != null && decoded.waveform.isNotEmpty()) {
                        dao.appendOrVerifyDerived(DerivedSignalEntity(
                            "${session.sessionId}:ecg-hal-research-decoding-v1", session.sessionId,
                            "ecg-hal-research-decoding", EcgHalEventResearchDecoder.ENCODING, null,
                            JSONObject().put("source_session_id", session.sessionId)
                                .put("origin", "RESEARCH_DECODED")
                                .put("calibration_status", "UNVERIFIED")
                                .put("timestamp_source", metadata.optString("timestamp_source", "UNKNOWN")).toString(),
                            JSONObject().put("decoded_event_count", decoded.eventCount)
                                .put("decoded_value_count", decoded.waveform.size)
                                .put("waveform", org.json.JSONArray(decoded.waveform))
                                .put("status", decoded.status).toString(), System.currentTimeMillis()
                        ))
                    }
                    val quality = if (selectedKind == "ppg" && values.isNotEmpty())
                        PpgCompositionQualityEngine.assess(values) else null
                    val motionResearch = if (selectedKind == "ppg") {
                        fun longValues(key: String): List<Long> = metadata.optJSONArray(key)?.let { array ->
                            List(array.length()) { array.optLong(it) }
                        }.orEmpty()
                        fun doubleValues(key: String): List<Double> = metadata.optJSONArray(key)?.let { array ->
                            List(array.length()) { array.optDouble(it, Double.NaN) }
                        }.orEmpty()
                        PpgMotionPreviewResearch.analyze(
                            sessionId = session.sessionId,
                            ppgRows = rows,
                            previewPointEventTimestampsNanos = longValues("preview_point_event_timestamps_ns"),
                            previewPointTimestampValidity = metadata.optJSONArray("preview_point_event_timestamp_validity")?.let { array ->
                                List(array.length()) { array.optInt(it, 0) }
                            }.orEmpty(),
                            ppgClockDomain = metadata.optString("ppg_preview_clock_domain", "UNKNOWN"),
                            motionClockDomain = metadata.optString("motion_clock_domain", "UNKNOWN"),
                            sameSession = metadata.optBoolean("motion_same_session", false),
                            sameMonotonicClock = metadata.optBoolean("motion_same_monotonic_clock", false),
                            maximumPairSkewNanos = metadata.optLong("motion_maximum_pair_skew_ns", 0L),
                            captureStatus = metadata.optString("motion_capture_status", "not_recorded"),
                            arrayAlignmentValid = metadata.optBoolean("motion_array_alignment_valid", false),
                            motionTimestampsNanos = longValues("motion_event_timestamps_ns"),
                            motionX = doubleValues("motion_x_meters_per_second_squared"),
                            motionY = doubleValues("motion_y_meters_per_second_squared"),
                            motionZ = doubleValues("motion_z_meters_per_second_squared")
                        )
                    } else null
                    if (motionResearch != null) {
                        val evidence = motionResearch.evidence
                        dao.appendOrVerifyDerived(DerivedSignalEntity(
                            "${session.sessionId}:ppg-motion-activity-v1",
                            session.sessionId,
                            "PPG_MOTION_ACTIVITY_EVIDENCE",
                            evidence?.algorithmVersion ?: "ppg-motion-evidence-v3",
                            null,
                            JSONObject()
                                .put("source_session_id", session.sessionId)
                                .put("origin", "DERIVED_FROM_MEASURED_SENSOR_EVENTS")
                                .put("ppg_clock_domain", metadata.optString("ppg_preview_clock_domain", "UNKNOWN"))
                                .put("motion_clock_domain", metadata.optString("motion_clock_domain", "UNKNOWN"))
                                .put("timestamp_source", "SENSOR_EVENT_UNVERIFIED_CLOCK")
                                .put("calibration_status", "NOT_APPLICABLE_ACTIVITY_INDEX_ONLY")
                                .put("clinical_use", false)
                                .put("not_a_ppg_artifact_score", true)
                                .toString(),
                            JSONObject()
                                .put("status", motionResearch.status)
                                .put("ppg_observation_count", motionResearch.ppgObservationCount)
                                .put("motion_event_count", motionResearch.motionEventCount)
                                .put("paired_observation_count", evidence?.pairedObservationCount ?: 0)
                                .put("matched_coverage", evidence?.matchedCoverage ?: JSONObject.NULL)
                                .put("overlap_of_ppg_window", evidence?.overlapOfPpgWindow ?: JSONObject.NULL)
                                .put("dynamic_acceleration_rms_mps2", evidence?.dynamicAccelerationRmsMetersPerSecondSquared ?: JSONObject.NULL)
                                .put("motion_activity_index", evidence?.motionActivityIndex ?: JSONObject.NULL)
                                .put("ppg_motion_change_correlation", evidence?.ppgMotionChangeCorrelation ?: JSONObject.NULL)
                                .put("ppg_motion_change_correlation_pair_count", evidence?.ppgMotionChangeCorrelationPairCount ?: 0)
                                .put("ppg_motion_change_correlation_scope", "exploratory_abs_first_difference_vs_dynamic_acceleration_magnitude")
                                .toString(),
                            System.currentTimeMillis()
                        ))
                        val activityLabel = evidence?.motionActivityIndex?.let {
                            val correlation = evidence.ppgMotionChangeCorrelation?.let { value ->
                                " · PPG 變化／動作共變相關 ${"%.2f".format(value)}（${evidence.ppgMotionChangeCorrelationPairCount} 組）"
                            } ?: " · PPG 動作共變相關未評估"
                            "同步腕部動作活動指數 ${"%.3f".format(it)}$correlation（研究探索值；非偽影分數／診斷）"
                        } ?: "同步腕部動作指標不可用：${motionResearch.status}"
                        label(activityLabel, 14f)
                    }
                    val ppgFilter = if (selectedKind == "ppg" && values.isNotEmpty()) {
                        IndexDomainPpgFilter.apply(values)
                    } else null
                    val opticalGapSentinel = if (selectedKind in setOf("ppg", "spo2_raw", "mi_spo2")) {
                        PpgCompositionQualityEngine.GAP_SENTINEL
                    } else null
                    val finite = values.filterNotNull().filter { it.isFinite() && it != opticalGapSentinel }
                    val analysisFinite = chartValues.filterNotNull().filter { it.isFinite() && it != opticalGapSentinel }
                    val researchSummary = ResearchSignalSummary.analyze(
                        chartValues,
                        mappedOrderedWaveform = false,
                        preserveInputOrder = selectedKind in setOf("spo2_raw", "mi_spo2", "thermometer", "body_temperature_raw"),
                        missingSentinels = if (selectedKind == "ppg" || selectedKind == "spo2_raw" || selectedKind == "mi_spo2") {
                            setOf(PpgCompositionQualityEngine.GAP_SENTINEL)
                        } else emptySet()
                    )
                    if (selectedKind in setOf("spo2_raw", "mi_spo2", "thermometer", "body_temperature_raw") && rows.isNotEmpty()) {
                        val previewResearch = RawPreviewResearch.summarize(
                            selectedKind, session.source, researchSummary
                        )
                        dao.appendOrVerifyDerived(RawPreviewResearch.toDerivedRecord(
                            sessionId = session.sessionId,
                            source = session.source,
                            timestampSource = metadata.optString("timestamp_source", "UNKNOWN"),
                            sourceMeasurementTimestampMillis = metadata.optLong("source_reported_timestamp_ms")
                                .takeIf { it > 0L },
                            publishedAtMillis = metadata.optLong("published_at_ms").takeIf { it > 0L },
                            result = previewResearch
                        ))
                    }
                    val filteredPpgSummary = ppgFilter?.let { filtered ->
                        ResearchSignalSummary.analyze(
                            filtered.values,
                            mappedOrderedWaveform = false,
                            missingSentinels = setOf(PpgCompositionQualityEngine.GAP_SENTINEL)
                        )
                    }
                    if (ppgFilter != null && filteredPpgSummary != null) {
                        dao.appendOrVerifyDerived(
                            DerivedSignalEntity(
                                "${session.sessionId}:${ppgFilter.algorithmVersion}",
                                session.sessionId,
                                "ppg-index-smoothed-preview",
                                ppgFilter.algorithmVersion,
                                null,
                                JSONObject()
                                    .put("source_session_id", session.sessionId)
                                    .put("source", session.source)
                                    .put("origin", "FILTERED")
                                    .put("method", "3-point median over preview index; incomplete windows unchanged")
                                    .put("timestamp_source", metadata.optString("timestamp_source", "UNKNOWN"))
                                    .put("sample_rate_hz", JSONObject.NULL)
                                    .put("calibration_status", "UNVERIFIED")
                                    .put("clinical_use", false)
                                    .toString(),
                                JSONObject()
                                    .put("values", org.json.JSONArray(ppgFilter.values.map { it ?: JSONObject.NULL }))
                                    .put("origins", org.json.JSONArray(ppgFilter.origins.map { it.name }))
                                    .put("filtered_point_count", ppgFilter.filteredPointCount)
                                    .put("relative_amplitude", org.json.JSONArray(filteredPpgSummary.relativeAmplitude.map { it ?: JSONObject.NULL }))
                                    .toString(),
                                System.currentTimeMillis()
                            )
                        )
                    }
                    val sourceTime = metadata.optLong("source_reported_timestamp_ms")
                    val publicationTime = metadata.optLong("published_at_ms")
                    fun timeLabel(time: Long) = if (time > 0L)
                        DateFormat.getDateTimeInstance().format(Date(time)) else "未知"
                    val timeline = SignalTimelineLabel.evidence(sourceTime, metadata.optString("timestamp_source"))
                    val trustedSourceTime = timeline.source in setOf("SENSOR_TIME", "MEASUREMENT_TIME")
                    label("分析時間軸：${timeline.source} · estimated_timeline=${timeline.estimated} · " +
                        if (trustedSourceTime) "由已標記來源時間提供；仍依通道校準狀態限制結論。"
                        else "依保存順序呈現；發布時間只代表封包發布時間，不代替感測時間。")
                    label("量測時間：${if (trustedSourceTime) timeLabel(sourceTime) else "未知"}\n" +
                        "來源回報時間：${timeLabel(sourceTime)} · timestamp_source=${metadata.optString("timestamp_source", "UNKNOWN")}\n發布時間：${timeLabel(publicationTime)}\n" +
                        "原始資料：${metadata.optBoolean("raw_data_available", rows.isNotEmpty())} · " +
                        "來源時間可用：${metadata.optBoolean("source_time_available")}\n" +
                        "發布時間可用：${metadata.optBoolean("publication_time_available")} · " +
                        "回報取樣率可用：${metadata.optBoolean("reported_rate_available")}\n" +
                        "旗標：${metadata.optString("evidence_flags", "舊紀錄未提供")}")
                    if (finite.isNotEmpty() && selectedKind != "bioz") label("原始編碼均值 ${"%.3f".format(finite.average())} · " +
                        "範圍 ${"%.3f".format(finite.min())}–${"%.3f".format(finite.max())}（未校準單位）")
                    if (analysisFinite.isNotEmpty() && selectedKind != "bioz") label(
                        "研究描述統計 · 有限值 ${"%.1f".format(researchSummary.measuredRatio * 100)}% · " +
                            "索引波形平均 ${"%.3f".format(researchSummary.mean)} · 標準差 ${"%.3f".format(researchSummary.standardDeviation)}；" +
                            "相對幅值範圍 0–1 · 描述統計支持度 ${ResearchSignalSummary.supportLabel(researchSummary.descriptiveSupport)}；" +
                            "只表示本次預覽的有限值比例與數量，不代表生理準確度。"
                    )
                    label("保存時間：${DateFormat.getDateTimeInstance().format(Date(session.createdAt))}（非量測時間）", 16f)
                    val archivedEventCount = metadata.opt("event_count")
                        ?.takeUnless { it == JSONObject.NULL }
                        ?.toString()?.toIntOrNull()
                    val eventCountLabel = PpgEvidencePresentationPolicy.eventCountLabel(
                        archivedEventCount,
                        metadata.optBoolean("event_count_known").takeIf { metadata.has("event_count_known") }
                    )
                    label("來源：${session.source}\n事件 $eventCountLabel · 原始值 ${metadata.opt("source_raw_value_count")} · 回報預覽點 ${metadata.optInt("preview_point_count")}\n顯示前 ${rows.size} 個預覽編碼；品質只評估此子集。")
                    if (decoded != null) label(if (decoded.waveform.isNotEmpty())
                        "近期保存子集：${decoded.eventCount} 個研究解碼事件／${decoded.waveform.size} 點。橫軸為索引，非醫療心電圖；不代表全部事件皆已解碼。"
                        else "保存事件解碼狀態：${decoded.status}；不生成替代波形。")
                    if (selectedKind == "ecg" && ecgFieldSummaries.isNotEmpty()) {
                        label("ECG HAL 原始欄位 × 事件索引預覽：${decoded?.eventCount ?: 0} 個事件、${ecgFieldSummaries.size} 欄。每欄獨立 min–max 正規化；橫軸不是秒，欄位映射／ECG 波形／取樣率未驗證。", 14f)
                        body.addView(IndexPreview(ecgFieldSummaries.map { it.normalizedEventIndexPreview }), LinearLayout.LayoutParams(-1, dp(190)))
                        label("圖例：${ecgFieldSummaries.joinToString(" · ") { "欄位 ${it.fieldIndex + 1}" }}（色彩循環；相對幅值 0–1，不代表 mV 或心電圖）", 12f)
                        label("候選值 ${decoded?.candidateValueCount ?: 0} · 檢視原始欄位值 ${decoded?.inspectedFieldValueCount ?: 0} · mapping=unverified。此圖只協助檢查欄位連續性，不輸出 R 峰、RR、bpm、HRV 或診斷。", 12f)
                    }
                    if (selectedKind == "bioz") {
                        if (biozResearch == null) {
                            label("BioZ 原始封包欄位無法一致解讀；保留事件資料，不把扁平預覽拼成時間波形。相對指標：未評分。")
                        } else {
                            label("BioZ 原始欄位 × 事件序列 · NORMALIZED_INDEX（非秒）· 每欄獨立正規化，不代表 Ω")
                            body.addView(IndexPreview(biozResearch.fields.map { it.relativeAmplitude }), LinearLayout.LayoutParams(-1, dp(170)))
                            label("圖例：${biozResearch.fields.joinToString(" · ") { "欄位 ${it.index}" }}（色彩循環；欄位身分未驗證）", 12f)
                            for (field in biozResearch.fields) {
                                label("欄位 ${field.index} · 有限值 ${field.finiteCount}/${field.eventCount} · " +
                                    "原始碼均值 ${field.mean?.let { "%.3f".format(it) } ?: "未評分"} · " +
                                    "原始碼標準差 ${field.standardDeviation?.let { "%.3f".format(it) } ?: "未評分"} · " +
                                    "相對離散 ${field.relativeDispersionPercent?.let { "%.1f%%".format(it) } ?: "未評分"} · " +
                                    "首尾區段相對差 ${field.firstToLastQuartileChangePercent?.let { "%+.1f%%".format(it) } ?: "未評分"}（研究描述值，非阻抗）", 13f)
                            }
                            label("封包資料完整度 ${ResearchScoreDisplay.format(biozResearch.evidenceCompleteness.score)} · " +
                                "有限原始值 ${biozResearch.finiteValueCount}/${biozResearch.expectedValueCount} · " +
                                "演算法 ${ResearchQualityIndexCalculator.VERSION}；不是訊號準確度或分析信心。\n" +
                                "校準：未驗證 · 來源：${session.source} · 分析信心：未評分（欄位映射／單位未驗證）。")
                        }
                    } else {
                        label("波形縱軸：本段 min–max 相對幅值 0–1；橫軸：保存順序索引（非秒）", 12f)
                        val plottedTraces = if (filteredPpgSummary != null) {
                            listOf(researchSummary.relativeAmplitude, filteredPpgSummary.relativeAmplitude)
                        } else listOf(researchSummary.relativeAmplitude)
                        body.addView(IndexPreview(plottedTraces), LinearLayout.LayoutParams(-1, dp(150)))
                        if (filteredPpgSummary != null) {
                            label("PPG 比較：原始相對波形／3 點中值平滑 · ${ppgFilter?.filteredPointCount ?: 0} 點標記為 FILTERED · 依樣本索引處理，未假設取樣率，不換算 bpm", 12f)
                        }
                        label((if (analysisFinite.isEmpty()) "無保存或可解碼預覽值；有限值比例與預覽品質不可評估。"
                            else "${if (decoded?.waveform?.isNotEmpty() == true) "研究解碼子集" else "實測保存預覽"} · 未補值\n有限值比例 ${"%.1f".format(100.0 * researchSummary.measuredRatio)}%") +
                            if (quality != null) " · 波形品質 ${ResearchScoreDisplay.format(quality.qualityScore)} · 綜合品質 ${quality.combinedQualityScore?.let(ResearchScoreDisplay::format) ?: "未評分（缺動作／ADC 證據）"}\n動作干擾／ADC 範圍：未知；分數不等於生理準確率。"
                            else if (selectedKind == "ppg") "\n工程品質：未知（無保存預覽）。"
                            else "\n此通道尚未套用經驗證的專用品質評分。")
                    }
                    val chartFinite = chartValues.filterNotNull().filter(Double::isFinite)
                    val waveformAvailable = if (selectedKind == "bioz") {
                        biozResearch?.fields?.any { it.eventCount >= 2 && it.finiteCount >= 2 } == true
                    } else chartFinite.isNotEmpty() && (chartFinite.max() - chartFinite.min()).isFinite() &&
                        chartValues.zipWithNext().any { (before, after) ->
                        before?.isFinite() == true && after?.isFinite() == true
                    }
                    label("Full Coverage：停用（通道與索引順序尚未驗證）\n波形：${if (waveformAvailable) "LIMITED" else "FAIL"} · 趨勢／脈率／HRV／生理換算：FAIL\n不會將補值或原始編碼當成血氧、心率或阻抗。")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status.text = "證據讀取失敗；既有原始資料未刪除。請重新整理或查看工程診斷。"
            }
        }
        body.addView(Button(this).apply { text = "重新整理"; setOnClickListener { load() } })
    }

    private fun label(value: String, size: Float = 14f): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE)
        setPadding(0, dp(10), 0, dp(10))
        body.addView(this)
    }

    private inner class IndexPreview(private val traces: List<List<Double?>>) : View(this@PpgEvidenceActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(92, 214, 177); strokeWidth = dp(2).toFloat() }
        private val traceColors = listOf(
            Color.rgb(92, 214, 177), Color.rgb(102, 168, 255), Color.rgb(255, 181, 92), Color.rgb(204, 132, 255),
            Color.rgb(255, 105, 130), Color.rgb(99, 220, 204), Color.rgb(232, 218, 105), Color.rgb(154, 191, 255)
        )
        private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 112, 130)
            strokeWidth = dp(1).toFloat()
            textSize = dp(10).toFloat()
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (traces.none { trace -> trace.count { it?.isFinite() == true } >= 2 }) return
            val top = dp(14).toFloat()
            val bottom = height - dp(14).toFloat()
            fun y(value: Double) = bottom - (bottom - top) * value.coerceIn(0.0, 1.0).toFloat()
            canvas.drawText("1.0", 0f, top, axisPaint)
            canvas.drawText("0.0", 0f, bottom, axisPaint)
            canvas.drawLine(dp(26).toFloat(), top, width.toFloat(), top, axisPaint)
            canvas.drawLine(dp(26).toFloat(), bottom, width.toFloat(), bottom, axisPaint)
            traces.forEachIndexed { traceIndex, values ->
                paint.color = traceColors[traceIndex % traceColors.size]
                for (index in 1 until values.size) {
                    val before = values[index - 1]
                    val after = values[index]
                    if (before?.isFinite() == true && after?.isFinite() == true) {
                        val left = dp(30) + (width - dp(30)) * (index - 1).toFloat() / (values.size - 1)
                        val right = dp(30) + (width - dp(30)) * index.toFloat() / (values.size - 1)
                        canvas.drawLine(left, y(before), right, y(after), paint)
                    }
                }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
