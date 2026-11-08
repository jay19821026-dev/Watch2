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
        val time = (if (isHealthServicesHeartRate) feature.getLong("whs_heart_rate_timestamp", 0L)
            else raw.getLong("${kind}_time", 0L)).takeIf { it > 0 }
            ?: raw.getLong("hrv_research_time", 0L).takeIf { kind == "hrv_research" && it > 0 }
            ?: 0L
        val supplementalHeartRate = if (kind == "ppg") PpgSupplementalHeartRatePolicy.match(
            ppgMeasuredAt = time,
            ppgEventCount = samples ?: 0,
            ppgTimestampSource = raw.getString("ppg_timestamp_source", null),
            sessionStartedAt = raw.getLong("ppg_session_started_at_ms", 0L),
            publishedAt = raw.getLong("ppg_published_at", 0L),
            bpm = feature.getFloat("whs_heart_rate", Float.NaN),
            heartRateMeasuredAt = feature.getLong("whs_heart_rate_timestamp", 0L),
            timestampSource = feature.getString("whs_heart_rate_timestamp_source", null),
            now = System.currentTimeMillis()
        ) else null
        val hasReceivedData = if (isHealthServicesHeartRate) (samples ?: 0) > 0
            else receivedDataCount(raw, kind) > 0 || supplementalHeartRate != null
        val feedback = MeasurementFeedbackFactory.create(status, samples ?: 0, hasReceivedData)
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
        val (value, unit) = primaryValue(kind, raw, feature, opticalEstimate, supplementalHeartRate)
        val resultPresentation = MeasureResultValuePolicy.present(
            kind = if (supplementalHeartRate != null) "whs_heart_rate" else kind,
            value = value,
            unit = unit,
            samples = if (supplementalHeartRate != null) 1 else samples ?: 0,
            status = if (supplementalHeartRate != null) "has_data" else status,
            measuredAtMillis = supplementalHeartRate?.measuredAt ?: time,
            nowMillis = System.currentTimeMillis(),
            timestampSource = if (supplementalHeartRate != null)
                HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE else timestampSource
        )
        val displayValue = resultPresentation.value
        val detail = detailLine(kind, raw) + (supplementalHeartRate?.let {
            "\n手錶心率：${"%.0f".format(it.bpm)} bpm；來源為手錶系統量測。"
        } ?: "")
        val hasReadableValue = displayValue != "—"
        val resultConclusion = when {
            !feedback.receivedData && !hasReadableValue -> "$title 量測未完成 · 請確認佩戴與連線後重測"
            supplementalHeartRate != null && displayValue != "—" ->
                "心率 $displayValue bpm · ${ConsumerVitalsSummary.heartRateLabel(displayValue.toDoubleOrNull() ?: supplementalHeartRate.bpm.toDouble())}"
            kind == "ecg" && !hasReadableValue ->
                "已收到心電訊號 · 尚無心律結果"
            kind == "ppg" && !hasReadableValue ->
                "已收到脈搏訊號 · 尚無脈率讀值"
            kind in setOf("spo2_raw", "spo2") && !hasReadableValue ->
                "已收到血氧訊號 · 尚無血氧百分比"
            kind in setOf("body_temperature_raw", "body_temperature") && !hasReadableValue ->
                "已收到溫度訊號 · 尚無可用體溫讀值"
            kind == "bioz" && !hasReadableValue ->
                "已收到體成分訊號 · 尚無體成分讀值"
            !hasReadableValue -> "這次沒有$title 讀值 · 請重新量測"
            kind == "whs_heart_rate" ->
                "心率 $displayValue bpm · ${ConsumerVitalsSummary.heartRateLabel(displayValue.toDoubleOrNull() ?: 0.0)}"
            kind == "ecg" -> "估計心率 $displayValue bpm · 尚無心律分類"
            kind in setOf("ppg", "max_hr", "lifeq_ott") && resultPresentation.unit.contains("研究估算") ->
                "估計脈率 $displayValue bpm · 可與手錶心率紀錄比較"
            else -> "$title $displayValue ${resultPresentation.unit}"
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(48))
            setBackgroundColor(Color.rgb(242, 242, 247))
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        root.addView(TextView(this).apply {
            text = if (supplementalHeartRate != null && resultPresentation.isCurrent) "已取得手錶心率"
                else resultPresentation.headline ?: feedback.headline
            textSize = 15f
            setTextColor(if (resultPresentation.isCurrent && kind != "ecg") Color.rgb(40, 120, 90) else Color.rgb(180, 80, 50))
            gravity = android.view.Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            this.text = title; textSize = 28f; setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        })
        root.addView(card(resultConclusion, Color.rgb(232, 244, 252)))
        val typedHeartRate = supplementalHeartRate?.bpm
            ?: healthServicesBpm.takeIf { isHealthServicesHeartRate && it.isFinite() }
        if (resultPresentation.isCurrent) {
            typedHeartRate?.let { root.addView(card(HeartRateMeaningPolicy.explain(it), Color.WHITE)) }
        }
        root.addView(TextView(this).apply {
            text = if (hasReadableValue) displayValue else "—"
            textSize = if (hasReadableValue) 56f else 36f; setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = if (hasReadableValue) resultPresentation.unit else ""
            textSize = 16f; setTextColor(Color.rgb(90, 90, 100))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(Button(this).apply {
            text = "查看結果說明"; isAllCaps = false
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MeasureResultActivity)
                    .setTitle("$title · 量測詳情")
                    .setMessage(listOfNotNull(detail, resultPresentation.guidance ?: feedback.guidance).joinToString("\n\n"))
                    .setPositiveButton("知道了", null).show()
            }
        })
        val shouldOfferRetry = !hasReadableValue || !resultPresentation.isCurrent || status in setOf(
            "invalid_optical", "invalid_sensor_values", "rejected_quality", "no_events", "error",
            "needs_contact", "needs_oem_mode", "off_wrist"
        )
        if (shouldOfferRetry) {
            root.addView(Button(this).apply {
                text = "重新量測"
                isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat(); setColor(accent)
                }
                setOnClickListener { requestFreshMeasurement(kind) }
                layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) }
            })
        }
        if (kind == "ecg" || OpticalPulseDisplayPolicy.isOpticalKind(kind)) {
            root.addView(card(
                "此為消費型手錶健康參考，結果可能有誤差。若有胸痛、暈厥、呼吸困難或持續不適，請就醫評估。",
                Color.rgb(255, 242, 225)
            ))
        }
        MeasureResultTimestampPresentation.label(
            supplementalHeartRate?.measuredAt ?: time,
            if (supplementalHeartRate != null) HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE else timestampSource,
            System.currentTimeMillis()
        )?.let { timeLabel ->
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
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247))
            clipToPadding = false
            addView(root)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
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
                Toast.makeText(this, "目前無法連接手錶；請檢查連線後重試。", Toast.LENGTH_LONG).show()
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
            "ecg" -> "心電圖"
            "hrv_research" -> "HRV"
            "spo2_raw", "spo2" -> "血氧"
            "body_temperature_raw", "body_temperature" -> "腕部溫度"
            "bioz" -> "體成分"
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
            opticalEstimate: Float?,
            supplementalHeartRate: PpgSupplementalHeartRatePolicy.Reading?
        ): Pair<String, String> {
            fun f(key: String) = raw.getFloat(key, Float.NaN).takeIf { it.isFinite() }
            return when (kind) {
                "optical_bp" -> "—" to ""
                "ppg", "max_hr", "heart_rate_raw", "lifeq_ott" -> {
                    if (OpticalPulseDisplayPolicy.isOpticalKind(kind)) {
                        if (supplementalHeartRate != null) "%.0f".format(supplementalHeartRate.bpm) to "bpm"
                        else if (opticalEstimate != null) "%.0f".format(opticalEstimate) to "研究估算 bpm"
                        else "—" to ""
                    } else {
                        val bpm = f("${kind}_formula_pulse_bpm") ?: f("${kind}_average")
                        (bpm?.let { "%.0f".format(it) } ?: "—") to "bpm"
                    }
                }
                "whs_heart_rate" -> {
                    val bpm = feature.getFloat("whs_heart_rate", Float.NaN).takeIf { it.isFinite() }
                    (bpm?.let { "%.0f".format(it) } ?: "—") to "bpm"
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
                    (displayedBpm?.let { "%.0f".format(it) } ?: "—") to
                        if (displayedBpm != null) "bpm" else ""
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
                "spo2_raw", "spo2" -> "—" to ""
                "body_temperature_raw" -> {
                    "—" to ""
                }
                "bioz", "body_temperature" -> "—" to ""
                "pressure" -> (f("pressure_average")?.takeIf { it in 300f..1100f }
                    ?.let { "%.0f".format(it) } ?: "—") to "hPa"
                else -> {
                    "—" to ""
                }
            }
        }

        private fun detailLine(kind: String, raw: android.content.SharedPreferences): String = when {
            kind == "optical_bp" -> "此手錶沒有可用的血壓讀值；可輸入外部血壓計的結果。"
            kind == "ecg" -> "這次沒有可判讀的心律結果；請確認電極接觸後重新量測。"
            OpticalPulseDisplayPolicy.isOpticalKind(kind) -> "這次沒有可靠的脈搏讀值；請戴好手錶並保持靜止。"
            raw.getInt("${kind}_event_count", 0) > 0 -> "手錶已完成擷取，但目前沒有可顯示的健康讀值。"
            else -> "這次沒有收到可用讀值。"
        }
    }
}

internal object MeasureResultTimestampPresentation {
    fun label(timestampMillis: Long, source: String?, nowMillis: Long): String? {
        if (timestampMillis <= 0L) return null
        if (source != HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE) {
            return "量測時間未確認"
        }
        if (timestampMillis > nowMillis) return "量測時間未確認"
        return SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
    }
}

internal object PpgSupplementalHeartRatePolicy {
    data class Reading(val bpm: Float, val measuredAt: Long)

    fun match(
        ppgMeasuredAt: Long,
        ppgEventCount: Int,
        ppgTimestampSource: String?,
        bpm: Float,
        heartRateMeasuredAt: Long,
        timestampSource: String?,
        now: Long,
        sessionStartedAt: Long = 0L,
        publishedAt: Long = 0L
    ): Reading? {
        if (ppgEventCount <= 0 ||
            timestampSource != HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE ||
            !bpm.isFinite() || bpm !in 30f..220f ||
            heartRateMeasuredAt !in 1L..now || now - heartRateMeasuredAt > 600_000L) return null
        val trustedPpgTime = ppgMeasuredAt.takeIf {
            HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(ppgTimestampSource) && it in 1L..now
        }
        val sessionStart = sessionStartedAt.takeIf { it in 1L..now }
        val sessionEnd = publishedAt.takeIf { it in 1L..now && (sessionStart == null || it >= sessionStart) }
        val nearSession = if (sessionStart != null && sessionEnd != null) {
            heartRateMeasuredAt >= sessionStart - 120_000L && heartRateMeasuredAt <= sessionEnd + 120_000L
        } else trustedPpgTime?.let { kotlin.math.abs(it - heartRateMeasuredAt) <= 120_000L } ?: false
        return Reading(bpm, heartRateMeasuredAt).takeIf { nearSession }
    }
}

internal object HeartRateMeaningPolicy {
    /** Context for a single typed BPM measurement; rest/activity state is unknown. */
    fun explain(bpm: Float): String = when {
        !bpm.isFinite() || bpm !in 30f..220f -> "這次心率讀值不在可解釋範圍；請重新量測。"
        bpm < 60f -> "偏低：成人靜息參考 60–100 bpm。若休息時反覆出現或感到頭暈，請就醫。"
        bpm <= 100f -> "在成人靜息參考範圍 60–100 bpm 內；活動時的範圍不同。"
        else -> "偏高：成人靜息參考 60–100 bpm。休息後再測；若持續偏高或心悸，請就醫。"
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
        if (unit == "原始資料點／事件") {
            return Presentation("—", "", false, "這次沒有可顯示的健康讀值", "請戴好手錶後重新量測。")
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
                    "量測時間未確認"
                else -> null
            }
            return Presentation(
                value = "—",
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
