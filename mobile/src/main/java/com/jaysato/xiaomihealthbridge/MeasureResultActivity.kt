package com.jaysato.xiaomihealthbridge

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.Wearable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Apple Watch / Samsung Monitor–style phone result page after a watch measure finishes.
 * Big number + short conclusion + Done — not a table dump.
 */
class MeasureResultActivity : ComponentActivity() {
    private fun receivedDataCount(raw: android.content.SharedPreferences, kind: String): Int =
        RawSensorPayloadPolicy.displayPresenceCount(
            raw.getInt("${kind}_count_contract_version", 0),
            raw.getInt("${kind}_raw_value_count", -1).takeIf { it >= 0 },
            raw.getInt("${kind}_event_count", 0),
            raw.getInt("${kind}_preview_point_count", 0),
            raw.getInt("${kind}_samples", 0)
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: latestKind(this) ?: "ppg"
        if (kind == "optical_bp") {
            startActivity(Intent(this, OpticalBpActivity::class.java))
            finish()
            return
        }
        val raw = RawSensorStore.open(this@MeasureResultActivity)
        val feature = getSharedPreferences(WearDataProcessor.FEATURE_PREFS, MODE_PRIVATE)
        val title = titleFor(kind)
        val accent = accentFor(kind)
        val isHealthServicesHeartRate = kind == "whs_heart_rate"
        val healthServicesBpm = feature.getFloat("whs_heart_rate", Float.NaN)
        val storedStatus = if (isHealthServicesHeartRate) {
            if (healthServicesBpm.isFinite()) "has_data" else "not_measured"
        } else raw.getString("${kind}_status", null)
        val externalCalibrationVerified = kind == "ecg" &&
            EcgResearchProfileStore(this).readActive(raw) != null
        val status = if (kind == "ecg" && storedStatus == "calibrated_external_reference" &&
            !externalCalibrationVerified) "research_signal_unscaled" else storedStatus
        val samples = if (isHealthServicesHeartRate) {
            if (healthServicesBpm.isFinite()) 1 else 0
        } else rawValueCount(raw, kind)?.takeIf { it > 0 }
            ?: raw.getInt("${kind}_event_count", 0).takeIf {
                RawSensorPayloadPolicy.isBiomedicalKind(kind) && raw.getInt("${kind}_event_count_known", 0) == 1 && it > 0
            }
        val hasReceivedData = if (isHealthServicesHeartRate) (samples ?: 0) > 0
            else receivedDataCount(raw, kind) > 0
        val feedback = MeasurementFeedbackFactory.create(status, samples ?: 0, hasReceivedData)
        val time = (if (isHealthServicesHeartRate) feature.getLong("whs_heart_rate_timestamp", 0L)
            else raw.getLong("${kind}_time", 0L)).takeIf { it > 0 }
            ?: raw.getLong("hrv_research_time", 0L).takeIf { kind == "hrv_research" && it > 0 }
            ?: 0L
        val timestampSource = if (isHealthServicesHeartRate) {
            feature.getString("whs_heart_rate_timestamp_source", null)
        } else if (kind == "hrv_research") {
            raw.getString("hrv_research_timestamp_source", null)
        } else {
            raw.getString("${kind}_timestamp_source", null)
        }
        val opticalEstimate = OpticalPulseDisplayPolicy.researchEstimateBpm(
            kind = kind,
            estimateBpm = raw.getFloat("${kind}_formula_pulse_bpm", Float.NaN).takeIf { it.isFinite() },
            qualityScore = raw.getInt("${kind}_quality_score", 0),
            status = status,
            measuredAtMillis = time,
            nowMillis = System.currentTimeMillis(),
            sampleRateHz = raw.getFloat("${kind}_sample_rate", 0f),
            channelMappingVerified = raw.getBoolean("${kind}_channel_mapping_verified", false),
            timestampSource = timestampSource,
            wearQualified = if (raw.contains("${kind}_wear_qualified")) raw.getBoolean("${kind}_wear_qualified", false) else null
        )
        val (value, unit) = primaryValue(kind, raw, feature, opticalEstimate)
        val resultPresentation = MeasureResultValuePolicy.present(
            kind = kind,
            value = value,
            unit = unit,
            samples = samples ?: 0,
            status = status,
            measuredAtMillis = time,
            nowMillis = System.currentTimeMillis(),
            timestampSource = timestampSource
        )
        val displayValue = resultPresentation.value
        val detail = detailLine(kind, raw)
        val resultConclusion = when {
            !feedback.receivedData -> "本次結論：未收到可用的 $title 量測資料；請重新量測。"
            resultPresentation.unit == "原始資料點／事件" && displayValue != "—" ->
                "本次結論：已收到 $displayValue 個 $title 原始資料點／事件；目前只能確認資料擷取，尚不能換算成經驗證的生理值。"
            displayValue == "—" ->
                "本次結論：已收到 $title 資料，但目前無法提供可信的生理數值；請查看下方原因與量測時間。"
            else -> "本次結論：$title $displayValue ${resultPresentation.unit}；請連同量測時間與來源查看。"
        }
        val note = if (kind == "ecg") {
            "研究用途估算，非經裝置校準的 ECG／心率量測，不可用於診斷或醫療決策。原始波形與公式結果不代表臨床有效性。"
        } else if (OpticalPulseDisplayPolicy.isOpticalKind(kind)) {
            "MAX86178／LifeQ 光學原始封包不是心率。只有品質合格、未過期的研究估算才顯示脈率；不作疾病診斷。"
        } else when (kind) {
            else -> raw.getString("${kind}_formula_note", null)
                ?: raw.getString("hrv_research_formula_note", null)
                ?: raw.getString("${kind}_summary", null)
                ?: "已同步手錶量測"
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(48))
            setBackgroundColor(Color.rgb(242, 242, 247))
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        root.addView(TextView(this).apply {
            text = resultPresentation.headline ?: feedback.headline; textSize = 15f
            setTextColor(if (resultPresentation.isCurrent && kind != "ecg") Color.rgb(40, 120, 90) else Color.rgb(180, 80, 50))
            gravity = android.view.Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            this.text = title; textSize = 28f; setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = displayValue; textSize = 56f; setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = resultPresentation.unit; textSize = 16f; setTextColor(Color.rgb(90, 90, 100))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(card(detail, Color.WHITE))
        root.addView(card(resultConclusion, Color.rgb(232, 244, 252)))
        if (kind == "ecg" || OpticalPulseDisplayPolicy.isOpticalKind(kind)) {
            root.addView(card(
                "注意：本次研究判讀可能不準確，不能作為疾病診斷或排除疾病的依據。若有胸痛、暈厥、呼吸困難或持續不適，請就醫；健康疑慮以醫師評估為準。",
                Color.rgb(255, 242, 225)
            ))
        }
        root.addView(card(resultPresentation.guidance ?: feedback.guidance, Color.WHITE))
        root.addView(card(note.take(220), Color.WHITE))
        if (!resultPresentation.isCurrent) {
            root.addView(Button(this).apply {
                text = "要求手錶重新量測"
                isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat(); setColor(accent)
                }
                setOnClickListener { requestFreshMeasurement(kind) }
                layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) }
            })
        }
        MeasureResultTimestampPresentation.label(time, timestampSource, System.currentTimeMillis())?.let { timeLabel ->
            root.addView(TextView(this).apply {
                text = timeLabel
                textSize = 13f; setTextColor(Color.rgb(140, 140, 150))
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(10), 0, dp(16))
            })
        }
        root.addView(Button(this).apply {
            text = "完成"
            isAllCaps = false; textSize = 17f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat(); setColor(accent)
            }
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(54))
        })
        root.addView(Button(this).apply {
            text = "查看詳細／歷史"
            isAllCaps = false
            setOnClickListener {
                when (kind) {
                    "ecg" -> startActivity(Intent(this@MeasureResultActivity, IrregularRhythmActivity::class.java))
                    "optical_bp" -> startActivity(Intent(this@MeasureResultActivity, OpticalBpActivity::class.java))
                    "ppg", "max_hr", "lifeq_ott" -> startActivity(Intent(this@MeasureResultActivity, RawSensorDetailActivity::class.java)
                        .putExtra("kind", kind))
                    "hrv_research", "heart_rate_raw", "whs_heart_rate" ->
                        startActivity(Intent(this@MeasureResultActivity, MetricDetailActivity::class.java)
                            .putExtra("metric", "heart_rate"))
                    else -> startActivity(Intent(this@MeasureResultActivity, RawSensorDetailActivity::class.java)
                        .putExtra("kind", kind))
                }
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }
        })
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247))
            addView(root)
        })
    }

    private fun card(body: String, bg: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat(); setColor(bg)
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        addView(TextView(context).apply {
            text = body; textSize = 14f; setTextColor(Color.rgb(40, 40, 45))
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun requestFreshMeasurement(kind: String) {
        val path = when (kind) {
            "ecg" -> "/control/ecg-now"
            "bioz" -> "/control/bioz-now"
            "spo2", "spo2_raw", "oxygen_saturation" -> "/control/oxygen-now"
            "body_temperature", "body_temperature_raw", "temperature" -> "/control/temperature-now"
            "off_body", "off_wrist" -> "/control/off-body-now"
            "ppg", "hrv_research", "max_hr", "heart_rate_raw", "lifeq_ott" -> "/control/ppg-now"
            else -> null
        }
        if (path == null) {
            Toast.makeText(this, "此項目沒有可直接啟動的手錶量測通道", Toast.LENGTH_LONG).show()
            return
        }
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Toast.makeText(this, WearCommandTargetPolicy.failureMessage(nodes.size), Toast.LENGTH_LONG).show()
                } else {
                    val node = WearCommandTargetPolicy.uniqueTarget(nodes)
                    if (node == null) {
                        Toast.makeText(this, WearCommandTargetPolicy.failureMessage(nodes.size), Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    Wearable.getMessageClient(this).sendMessage(node.id, path, byteArrayOf())
                    Toast.makeText(this, "已要求手錶量測；完成後重新開啟此頁查看新讀值", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "無法要求手錶量測：${error.localizedMessage ?: "連線錯誤"}", Toast.LENGTH_LONG).show()
            }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        private const val LEGACY_CHANNEL = "measure_results"

        private fun rawValueCount(raw: android.content.SharedPreferences, kind: String): Int? =
            if (RawSensorPayloadPolicy.isBiomedicalKind(kind)) {
                raw.getInt("${kind}_raw_value_count", -1).takeIf { it >= 0 }
            } else {
                raw.getInt("${kind}_samples", -1).takeIf { it >= 0 }
            }

        fun latestKind(context: Context): String? {
            val raw = RawSensorStore.open(context)
            val feature = context.getSharedPreferences(WearDataProcessor.FEATURE_PREFS, Context.MODE_PRIVATE)
            return (listOf("optical_bp", "ppg", "max_hr", "heart_rate_raw", "ecg", "hrv_research", "spo2_raw", "body_temperature_raw", "bioz")
                .map { it to raw.getLong("${it}_time", 0L) }
                + listOf("whs_heart_rate" to feature.getLong("whs_heart_rate_timestamp", 0L)))
                .maxByOrNull { it.second }
                ?.takeIf { it.second > 0 }
                ?.first
        }

        fun cleanupLegacyNotifications(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.deleteNotificationChannel(LEGACY_CHANNEL)
            manager.deleteNotificationChannel("measure_results_silent")
            manager.activeNotifications
                .filter { it.id in 8200..8455 }
                .forEach { manager.cancel(it.id) }
        }

        private fun titleFor(kind: String) = when (kind) {
            "ppg", "max_hr", "heart_rate_raw", "lifeq_ott", "whs_heart_rate" -> "心率"
            "ecg" -> "ECG"
            "hrv_research" -> "HRV"
            "spo2_raw", "spo2" -> "血氧"
            "body_temperature_raw", "body_temperature" -> "溫度原始值"
            "bioz" -> "BioZ"
            "pressure" -> "氣壓"
            "optical_bp" -> "血壓"
            else -> kind
        }

        private fun accentFor(kind: String) = when (kind) {
            "ecg" -> Color.rgb(52, 199, 89)
            "hrv_research" -> Color.rgb(175, 82, 222)
            "spo2_raw", "spo2" -> Color.rgb(10, 132, 255)
            "body_temperature_raw" -> Color.rgb(255, 149, 0)
            "optical_bp" -> Color.rgb(255, 45, 85)
            else -> Color.rgb(255, 59, 48)
        }

        private fun primaryValue(
            kind: String,
            raw: android.content.SharedPreferences,
            feature: android.content.SharedPreferences,
            opticalEstimate: Float?
        ): Pair<String, String> {
            fun f(key: String) = raw.getFloat(key, Float.NaN).takeIf { it.isFinite() }
            return when (kind) {
                "optical_bp" -> "—" to ""
                "ppg", "max_hr", "heart_rate_raw", "lifeq_ott" -> {
                    if (OpticalPulseDisplayPolicy.isOpticalKind(kind)) {
                        val count = rawValueCount(raw, kind)?.takeIf { it > 0 }
                            ?: raw.getInt("${kind}_event_count", 0).takeIf {
                                raw.getInt("${kind}_event_count_known", 0) == 1 && it > 0
                            }
                        if (opticalEstimate != null) "%.0f".format(opticalEstimate) to "研究估算 bpm"
                        else (count?.toString() ?: "—") to "原始資料點／事件"
                    } else {
                        val bpm = f("${kind}_formula_pulse_bpm") ?: f("${kind}_average")
                        (bpm?.let { "%.0f".format(it) } ?: "—") to "bpm"
                    }
                }
                "whs_heart_rate" -> {
                    val bpm = feature.getFloat("whs_heart_rate", Float.NaN).takeIf { it.isFinite() }
                    (bpm?.let { "%.0f".format(it) } ?: "—") to "Health Services bpm"
                }
                "ecg" -> {
                    val bpm = f("ecg_rhythm_bpm") ?: f("ecg_formula_pulse_bpm")
                    val estimateUsable = ClinicalVerdict.isEcgPulseDisplayable(
                        qualityScore = raw.getInt("ecg_quality_score", 0),
                        sampleCount = rawValueCount(raw, "ecg") ?: 0,
                        sampleRate = raw.getFloat("ecg_sample_rate", 0f),
                        status = raw.getString("ecg_status", "").orEmpty(),
                        rrCount = raw.getInt("ecg_rhythm_rr_count", 0),
                        wearQualified = if (raw.contains("ecg_wear_qualified")) raw.getBoolean("ecg_wear_qualified", false) else null,
                        heartRateBpm = bpm?.toDouble(),
                        waveformEncoding = raw.getString("ecg_waveform_encoding", ""),
                        timelineSource = raw.getString("ecg_research_timeline_source", "NORMALIZED_INDEX").orEmpty()
                    )
                    val displayedBpm = bpm.takeIf { estimateUsable }
                    val count = rawValueCount(raw, "ecg")?.takeIf { it > 0 }
                        ?: raw.getInt("ecg_event_count", 0).takeIf {
                            raw.getInt("ecg_event_count_known", 0) == 1 && it > 0
                        }
                    (displayedBpm?.let { "%.0f".format(it) } ?: count?.toString() ?: "—") to
                        if (displayedBpm != null) "bpm" else "原始資料點／事件"
                }
                "hrv_research" -> {
                    val sourceKind = raw.getString("hrv_research_source_kind", "").orEmpty()
                    val hrvValid = HrvSourceValidity.isFreshAndQualified(
                        sourceKind = sourceKind,
                        measuredAt = raw.getLong("hrv_research_time", 0L),
                        now = System.currentTimeMillis(),
                        status = raw.getString("${sourceKind}_status", "").orEmpty(),
                        qualityScore = raw.getInt("${sourceKind}_quality_score", 0),
                        sampleRateHz = raw.getFloat("${sourceKind}_sample_rate", 0f),
                        sampleCount = raw.getInt("hrv_research_samples", 0),
                        rrCount = raw.getInt("hrv_research_rr_count", 0),
                        channelMappingVerified = raw.getBoolean("${sourceKind}_channel_mapping_verified", false),
                        wearQualified = if (raw.contains("${sourceKind}_wear_qualified")) raw.getBoolean("${sourceKind}_wear_qualified", false) else null,
                        waveformEncoding = raw.getString("${sourceKind}_waveform_encoding", null)
                    )
                    val rmssd = f("hrv_research_formula_rmssd_ms")?.takeIf { hrvValid }
                    (rmssd?.let { "%.0f".format(it) } ?: "—") to "RMSSD ms"
                }
                "spo2_raw" -> (rawValueCount(raw, "spo2_raw")?.takeIf { it > 0 }?.toString() ?: "—") to "有限原始點"
                "body_temperature_raw" -> {
                    val t = f("body_temperature_raw_average")?.takeIf {
                        HealthAnalysisFreshnessPolicy.isFreshSensor(
                            raw.getLong("body_temperature_raw_time", 0L),
                            raw.getString("body_temperature_raw_timestamp_source", null),
                            System.currentTimeMillis(), 24 * 60 * 60 * 1000L
                        )
                    } ?: feature.getFloat("temperature", Float.NaN).takeIf {
                        it.isFinite() && HealthAnalysisFreshnessPolicy.isFreshSensor(
                            feature.getLong("temperature_timestamp", 0L),
                            feature.getString("temperature_timestamp_source", null),
                            System.currentTimeMillis(), 24 * 60 * 60 * 1000L
                        )
                    }
                    (t?.let { "%.1f".format(it) } ?: "—") to "單位未驗證"
                }
                else -> {
                    val avg = f("${kind}_average")
                    (avg?.let { "%.1f".format(it) } ?: raw.getInt("${kind}_samples", 0).toString()) to "讀值"
                }
            }
        }

        private fun detailLine(kind: String, raw: android.content.SharedPreferences): String {
            if (kind == "optical_bp") {
                return "此手錶沒有經驗證的血壓量測；請使用外部袖帶讀值。"
            }
            val samples = rawValueCount(raw, kind)
            val status = raw.getString("${kind}_status", null)
            if (OpticalPulseDisplayPolicy.isOpticalKind(kind)) {
                return "有限原始值 ${SensorCountLabels.rawValueCount(samples)} · 預覽點 ${SensorCountLabels.count(raw.getInt("${kind}_preview_point_count", -1).takeIf { it >= 0 })} · 光學原始封包不是 bpm · 脈率僅顯示通過品質與新鮮度檢查的研究估算"
            }
            val stress = raw.getInt("${kind}_formula_stress_score", -1).takeIf { it >= 0 }
                ?: raw.getInt("hrv_research_formula_stress_score", -1).takeIf { it >= 0 }
            return buildString {
                if (RawSensorPayloadPolicy.isBiomedicalKind(kind)) {
                    append("有限原始值 ${SensorCountLabels.rawValueCount(samples)} · 預覽點 ${SensorCountLabels.count(raw.getInt("${kind}_preview_point_count", -1).takeIf { it >= 0 })} · 事件 ${SensorCountLabels.eventCount(raw.getInt("${kind}_event_count", -1).takeIf { raw.getInt("${kind}_event_count_known", 0) == 1 })}")
                } else {
                    val contractVersion = raw.getInt("${kind}_count_contract_version", 0)
                    val eventCount = raw.getInt("${kind}_event_count", -1)
                        .takeIf { raw.getInt("${kind}_event_count_known", 0) == 1 }
                    if (contractVersion >= 2) {
                        append("事件 ${SensorCountLabels.eventCount(eventCount)}")
                    } else {
                        append("舊版回報數 ${SensorCountLabels.count(samples)}")
                    }
                }
                status?.let { append(" · $it") }
                stress?.let { append(" · 研究壓力分數 $it（非原廠）") }
                append(" · 已同步手機")
            }
        }
    }
}

internal object MeasureResultTimestampPresentation {
    fun label(timestampMillis: Long, source: String?, nowMillis: Long): String? {
        if (timestampMillis <= 0L) return null
        if (source != HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE) {
            return "量測時間未確認（來源未提供或非日曆時間）"
        }
        if (timestampMillis > nowMillis) return "量測時間異常（時間戳在未來）"
        return SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
    }
}

internal object MeasureResultValuePolicy {
    internal data class Presentation(
        val value: String,
        val unit: String,
        val isCurrent: Boolean,
        val headline: String? = null,
        val guidance: String? = null
    )

    private val currentStates = setOf(
        SensorDisplayState.AVAILABLE,
        SensorDisplayState.SIGNAL_VALID_RESEARCH,
        SensorDisplayState.CALIBRATION_MISSING,
        SensorDisplayState.RAW_ONLY,
        SensorDisplayState.DERIVED_ESTIMATE
    )

    fun present(
        kind: String,
        value: String,
        unit: String,
        samples: Int,
        status: String?,
        measuredAtMillis: Long,
        nowMillis: Long,
        timestampSource: String? = null
    ): Presentation {
        if (kind == "optical_bp") {
            return Presentation(
                value = "—",
                unit = "",
                isCurrent = false,
                headline = "手錶血壓量測不可用",
                guidance = "舊版光學估計不具備裝置級驗證；請在血壓頁輸入外部袖帶讀值。"
            )
        }
        val state = SensorDisplayState.from(
            samples = samples,
            status = status.orEmpty(),
            measuredAt = measuredAtMillis,
            now = nowMillis,
            freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
            kind = kind,
            timestampSource = timestampSource
        )
        val current = state in currentStates
        if (!current) {
            val message = when (state) {
                SensorDisplayState.STALE -> "資料已過期，不能代表目前量測"
                SensorDisplayState.TIMESTAMP_UNKNOWN, SensorDisplayState.FUTURE_TIMESTAMP ->
                    "量測時間未知或無效，不能確認為目前值"
                else -> null
            }
            return Presentation(
                value = if (unit == "原始資料點／事件" && samples > 0 && value != "—") value else "—",
                unit = if (kind == "ecg" && unit == "bpm") "研究估算 bpm" else unit,
                isCurrent = false,
                headline = message,
                guidance = message
            )
        }
        return Presentation(
            value = value,
            unit = if (kind == "ecg" && unit == "bpm") "研究估算 bpm" else unit,
            isCurrent = true,
            headline = if (kind == "ecg") "研究估算 · 非校準量測" else null,
            guidance = if (kind == "ecg") "僅供研究參考；不是經校準的心率或 ECG 判讀，不可用於診斷或醫療決策。" else null
        )
    }
}
