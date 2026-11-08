package com.jaysato.xiaomihealthbridge

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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Consumer-health UX home (Apple Vitals / Samsung Health summary style).
 */
class VitalsHomeActivity : ComponentActivity() {
    override fun onRestart() {
        super.onRestart()
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = RawSensorStore.open(this@VitalsHomeActivity)
        val feature = getSharedPreferences(WearDataProcessor.FEATURE_PREFS, MODE_PRIVATE)
        val syncDiagnostics = getSharedPreferences(HeartRateDataListenerService.PREFS, MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(48))
            setBackgroundColor(Color.rgb(242, 242, 247))
        }
        root.addView(label("Vitals", 34f, Color.BLACK, true))
        root.addView(label(
            "查看主要健康指標與資料時間，也可前往手錶啟動量測。",
            14f, Color.rgb(90, 90, 100), false
        ).also { it.setPadding(0, dp(6), 0, dp(12)) })

        val report = HealthConclusionEngine.build(this)
        val conclusionHeadline = label(report.overallHeadline, 17f, Color.WHITE, true).also {
            it.setPadding(0, dp(4), 0, 0)
        }
        val conclusionDetail = label(report.overallDetail + "\n點此查看全部計算與臨床對照 →", 12f, Color.rgb(220, 235, 255), false).also {
            it.setPadding(0, dp(6), 0, 0)
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.rgb(0, 122, 255))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@VitalsHomeActivity, HealthConclusionActivity::class.java))
            }
            addView(label("健康結論", 13f, Color.rgb(200, 220, 255), true))
            addView(conclusionHeadline)
            addView(conclusionDetail)
        })

        val now = System.currentTimeMillis()
        val healthServicesHrTime = feature.getLong("whs_heart_rate_timestamp", 0L)
        val healthServicesHr = HeartRatePresentationPolicy.visibleHealthServicesBpm(
            feature.getFloat("whs_heart_rate", Float.NaN), healthServicesHrTime,
            feature.getString("whs_heart_rate_timestamp_source", null), now,
            HeartRatePresentationPolicy.HOME_HISTORY_WINDOW_MILLIS
        )
        val rawHrTime = raw.getLong("heart_rate_raw_time", 0L)
        val rawHr = HeartRatePresentationPolicy.visibleRawSensorBpm(
            raw.getFloat("heart_rate_raw_formula_pulse_bpm", Float.NaN)
                .takeIf { it.isFinite() } ?: raw.getFloat("heart_rate_raw_average", Float.NaN),
            raw.getString("heart_rate_raw_status", "").orEmpty(),
            rawHrTime,
            raw.getString("heart_rate_raw_timestamp_source", null),
            now
        )
        val latestHr = listOfNotNull(
            healthServicesHr?.let { it to healthServicesHrTime },
            rawHr?.let { it to rawHrTime }
        ).maxByOrNull { it.second }
        val latestHrUsesHealthServices = healthServicesHr != null && latestHr?.second == healthServicesHrTime
        val hr = latestHr?.first
        val hrAgeMinutes = latestHr?.second?.let { ((now - it).coerceAtLeast(0L) / 60_000L) }
        val hrEvidence = latestHr?.second?.let { measuredAt ->
            val sourceKind = if (latestHrUsesHealthServices) "health_services" else "raw_sensor"
            val timestampSource = if (latestHrUsesHealthServices) {
                feature.getString("whs_heart_rate_timestamp_source", null)
            } else {
                raw.getString("heart_rate_raw_timestamp_source", null)
            }
            HeartRateHomeEvidencePolicy.describe(sourceKind, measuredAt, timestampSource)
        }
        val hrConclusion = report.items.firstOrNull { it.id == "hr" && it.available }
        val spo2 = report.items.firstOrNull { it.id == "spo2" && it.available }
        val tempItem = report.items.firstOrNull { it.id == "temp" && it.available }
        val deviceTemperature = DeviceMetricFallback.temperatureC(feature, now)
        val hrvTime = raw.getLong("hrv_research_time", 0L)
        val ppgFresh = raw.getString("ppg_status", "") == "valid_signal" &&
            raw.getBoolean("ppg_wear_qualified", false) && raw.getBoolean("ppg_channel_mapping_verified", false) && raw.getInt("ppg_raw_value_count", 0) >= 32 &&
            raw.getInt("ppg_quality_score", 0) >= 70 && raw.getFloat("ppg_sample_rate", 0f) > 64f &&
            HealthAnalysisFreshnessPolicy.isFreshSensor(
                raw.getLong("ppg_time", 0L), raw.getString("ppg_timestamp_source", null), now
            )
        val hrvSource = raw.getString("hrv_research_source_kind", "").orEmpty()
        val researchFresh = hrvSource != "ecg" && HrvSourceValidity.isFreshAndQualified(
            sourceKind = hrvSource,
            measuredAt = hrvTime,
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
        val rmssd = raw.getFloat("hrv_research_formula_rmssd_ms", Float.NaN).takeIf { it.isFinite() && researchFresh }
            ?: raw.getFloat("ppg_formula_rmssd_ms", Float.NaN).takeIf { it.isFinite() && ppgFresh }
            ?: raw.getFloat("max_hr_formula_rmssd_ms", Float.NaN).takeIf {
                it.isFinite() && raw.getBoolean("max_hr_channel_mapping_verified", false) &&
                    raw.getBoolean("max_hr_wear_qualified", false) &&
                    raw.getInt("max_hr_quality_score", 0) >= 70 && raw.getFloat("max_hr_sample_rate", 0f) > 64f &&
                    raw.getInt("max_hr_raw_value_count", 0) >= 32 &&
                    HealthAnalysisFreshnessPolicy.isFreshSensor(
                        raw.getLong("max_hr_time", 0L), raw.getString("max_hr_timestamp_source", null), now
                    )
            }
        val ecg = report.items.firstOrNull { it.id == "ecg" && it.available }
        val ecgRawEvidence = raw.getInt("ecg_raw_value_count", 0) > 0 ||
            raw.getInt("ecg_event_count", 0) > 0 || raw.getInt("ecg_research_decoded_value_count", 0) > 0
        val ecgStatusText = raw.getString("ecg_status", "").orEmpty()
        val lastRejectedKind = syncDiagnostics.getString(WearDataProcessor.KEY_RAW_SNAPSHOT_LAST_REJECTED_KIND, null)
        val lastRejectionTime = syncDiagnostics.getLong(WearDataProcessor.KEY_RAW_SNAPSHOT_LAST_REJECTION_TIME, 0L)
        val ecgAvailability = SensorResultAvailability.classify(
            hasRawEvidence = ecgRawEvidence,
            resultEligible = ecgResultEligible(raw),
            qualityRejected = ecgStatusText in setOf("invalid_sensor_values", "rejected_quality"),
            syncFailed = !ecgRawEvidence && (
                ecgStatusText in setOf("sync_failed", "rejected_ordering", "persistence_failed") ||
                    SensorSyncFailurePolicy.isRecentForKind("ecg", lastRejectedKind, lastRejectionTime, now)
                )
        )
        val ppgRawEvidence = raw.getInt("ppg_raw_value_count", 0) > 0 || raw.getInt("ppg_event_count", 0) > 0
        val ppgStatusText = raw.getString("ppg_status", "").orEmpty()
        val ppgAvailability = SensorResultAvailability.classify(
            hasRawEvidence = ppgRawEvidence,
            resultEligible = ppgFresh || (researchFresh && hrvSource == "ppg"),
            qualityRejected = ppgStatusText in setOf("invalid_optical", "invalid_sensor_values", "rejected_quality") ||
                (ppgRawEvidence && raw.getInt("ppg_quality_score", 0) in 1..69),
            syncFailed = !ppgRawEvidence && (
                ppgStatusText in setOf("sync_failed", "rejected_ordering", "persistence_failed") ||
                    SensorSyncFailurePolicy.isRecentForKind("ppg", lastRejectedKind, lastRejectionTime, now)
                )
        )
        val pressure = report.items.firstOrNull { it.id == "pressure" && it.available }?.valueText
        val devicePressure = DeviceMetricFallback.pressureHpa(raw, now)

        val heartRateHeadline = VitalsCardPresentationPolicy.heartRateHeadline(
            liveBpm = hr?.toDouble(),
            fallbackValue = hrConclusion?.valueText
        )
        val heartRateDetail = (if (hr == null && hrConclusion == null) {
            val evidence = rawSamplesLabel(raw, "heart_rate_raw")
            val reason = unavailableSensorReason(raw, "heart_rate_raw", now)
            "$reason · $evidence"
        } else when {
            hr == null && hrConclusion?.valueText?.contains("研究估算") == true -> "研究估算 · 非手錶直出"
            hr == null && hrConclusion?.valueText?.contains("歷史紀錄") == true -> "歷史讀值 · 非即時"
            hr == null -> unavailableSensorReason(raw, "heart_rate_raw", now)
            hrAgeMinutes != null && hrAgeMinutes < 10 -> {
                val wearContext = if (healthServicesHr != null && latestHr?.second == healthServicesHrTime) {
                    val wearTime = feature.getLong("worn_timestamp", 0L)
                    HeartRatePresentationPolicy.wearContext(
                        feature.contains("worn"), feature.getBoolean("worn", false), wearTime, now,
                        feature.getString("worn_timestamp_source", null)
                    )
                } else ""
                "bpm · 即時手錶讀值${if (wearContext.isBlank()) "" else " · $wearContext"}"
            }
            else -> "bpm · 最近 ${hrAgeMinutes} 分鐘 · 非即時"
        }) + (hr?.let { "\n${VitalsCardPresentationPolicy.heartRateReference(it.toDouble())}" } ?: "") +
            (hrEvidence?.let { "\n$it" } ?: "")
        val heartRateTile = tile("心率", heartRateHeadline, heartRateDetail, Color.rgb(255, 59, 48), measurementPath = "/control/ppg-now") {
            startActivity(Intent(this, MetricDetailActivity::class.java).putExtra("metric", "heart_rate"))
        }
        root.addView(heartRateTile)
        val heartRateDaySummary = label("今日心率摘要：讀取中…", 13f, Color.rgb(90, 90, 100), false).apply {
            setPadding(dp(14), dp(2), dp(14), dp(14))
        }
        root.addView(heartRateDaySummary)
        loadHeartRateDaySummary(
            heartRateDaySummary,
            heartRateTile.getChildAt(1) as TextView,
            heartRateTile.getChildAt(2) as TextView,
            latestHr?.second,
            conclusionHeadline,
            conclusionDetail
        )
        val spo2Kind = listOf("spo2_raw", "mi_spo2")
            .maxByOrNull { raw.getLong("${it}_time", 0L) } ?: "spo2_raw"
        val spo2Recorded = spo2?.valueText?.contains("% · 已記錄") == true
        val spo2RecordedAt = raw.getLong("spo2_trusted_time", 0L)
        val spo2Detail = when {
            spo2 == null -> unavailableSensorReason(raw, spo2Kind, now)
            spo2Recorded -> VitalsCardPresentationPolicy.oxygenInterpretation(
                raw.getFloat("spo2_trusted_percent", Float.NaN).toDouble()
            ) + "\n" + VitalsCardPresentationPolicy.recordedSpO2Detail(
                raw.getString("spo2_trusted_origin", raw.getString("spo2_trusted_source", null)),
                spo2RecordedAt.takeIf { it > 0L }?.let {
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                        .format(java.util.Date(it))
                }
            )
            spo2.valueText.contains("歷史") -> "尚無可用的血氧讀值，請重新量測"
            else -> "尚無可用的血氧讀值，請重新量測"
        }
        root.addView(tile(
            "血氧",
            if (spo2Recorded) VitalsCardPresentationPolicy.recordedSpO2Headline(spo2?.valueText)
            else rawSamplesLabel(raw, spo2Kind),
            spo2Detail,
            Color.rgb(10, 132, 255), measurementPath = "/control/oxygen-now"
        ) {
            startActivity(Intent(this, MetricDetailActivity::class.java).putExtra("metric", "oxygen"))
        })
        root.addView(tile(
            "ECG 心電圖",
            if (ecgAvailability == SensorResultAvailability.RESULT_AVAILABLE) ecgDisplayValue(raw)
            else if (ecgAvailability == null) "待量測" else "重新量測",
            EcgHomeCardPresentationPolicy.supportingText(ecg?.valueText, ecgAvailability),
            Color.rgb(52, 199, 89), measurementPath = "/control/ecg-now"
        ) {
            startActivity(Intent(this, IrregularRhythmActivity::class.java))
        })
        val hrvKind = hrvSource.takeIf { it.isNotBlank() && it != "ecg" } ?: "ppg"
        root.addView(tile(
            "HRV",
            rmssd?.let { "%.0f ms".format(it) } ?: ppgAvailability?.label ?: "待量測",
            if (rmssd == null) ppgAvailability?.detail ?: "請在手錶開始量測" else "心率變異參考值",
            Color.rgb(175, 82, 222), measurementPath = "/control/ppg-now"
        ) {
            startActivity(Intent(this, MeasureResultActivity::class.java).putExtra("kind", "hrv_research"))
        })
        val stress = raw.getInt("hrv_research_formula_stress_score", -1).takeIf { it >= 0 && researchFresh }
            ?: raw.getInt("ppg_formula_stress_score", -1).takeIf { it >= 0 && ppgFresh }
        root.addView(tile(
            "研究壓力",
            stress?.toString() ?: ppgAvailability?.label ?: "待量測",
            if (stress == null) ppgAvailability?.detail ?: "需佩戴且取得足夠的有效光學 RR 間隔" else "本專案 HRV 啟發式；非小米原廠壓力分數",
            Color.rgb(255, 149, 0), measurementPath = "/control/ppg-now"
        ) {
            startActivity(Intent(this, MeasureResultActivity::class.java).putExtra("kind", "hrv_research"))
        })
        val temperatureDisplay = TemperatureHomeSummaryPolicy.resolve(deviceTemperature, tempItem)
        root.addView(tile(
            "體表溫度",
            temperatureDisplay.value,
            temperatureDisplay.detail,
            Color.rgb(255, 149, 0), measurementPath = "/control/temperature-now"
        ) {
            startActivity(Intent(this, MetricDetailActivity::class.java).putExtra("metric", "temperature"))
        })
        root.addView(tile("運動", "開始", "步行 · 暫停／結束同步", Color.rgb(52, 199, 89)) {
            startActivity(Intent(this, WorkoutAnalysisActivity::class.java))
        })
        root.addView(tile("健康摘要", "查看", "個別觀測與訓練紀錄；無未驗證複合分數", Color.rgb(48, 209, 200)) {
            startActivity(Intent(this, AiHealthAnalysisActivity::class.java))
        })
        root.addView(tile("睡眠", "查看", "總時長 · 分期槽位", Color.rgb(94, 92, 230)) {
            startActivity(Intent(this, SleepSummaryActivity::class.java))
        })
        val bp = report.items.firstOrNull { it.id == "bp" && it.available }
        val bpValue = bp?.valueText ?: "袖帶"
        val bpCat = bp?.conclusion ?: "沒有 7 天內有效的袖帶讀值 · 請以袖帶血壓計量測"
        root.addView(tile(
            "血壓",
            bpValue,
            bpCat,
            Color.rgb(255, 45, 85)
        ) {
            startActivity(Intent(this, OpticalBpActivity::class.java))
        })
        root.addView(tile("氣壓", pressure ?: devicePressure?.let { "%.1f hPa".format(it) } ?: "待量測", "最近一次裝置氣壓", Color.rgb(90, 200, 250)) {
            startActivity(Intent(this, RawSensorDetailActivity::class.java).putExtra("kind", "pressure"))
        })

        root.addView(label("瀏覽與更多", 20f, Color.BLACK, true).also {
            it.setPadding(0, dp(22), 0, dp(10))
        })
        root.addView(navButton("最近量測結果（大數字 Done）", Color.rgb(255, 59, 48)) {
            startActivity(Intent(this, MeasureResultActivity::class.java))
        })
        root.addView(navButton("聽力／噪音", Color.rgb(255, 204, 0)) {
            startActivity(Intent(this, NoiseSummaryActivity::class.java))
        })
        root.addView(navButton("心律通知（不規則心律／ECG 結果）", Color.rgb(52, 199, 89)) {
            startActivity(Intent(this, IrregularRhythmActivity::class.java))
        })
        root.addView(navButton("健康結論（全感測計算＋臨床對照）", Color.rgb(255, 59, 48)) {
            startActivity(Intent(this, HealthConclusionActivity::class.java))
        })
        root.addView(navButton("瀏覽分類（心律／睡眠／活動／身體）", Color.rgb(0, 122, 255)) {
            startActivity(Intent(this, HealthBrowseActivity::class.java))
        })
        root.addView(navButton("臨床實驗室", Color.rgb(50, 173, 230)) {
            startActivity(Intent(this, ClinicalLabActivity::class.java))
        })
        root.addView(navButton("晶片與資料", Color.rgb(48, 176, 199)) {
            startActivity(Intent(this, ChipFeatureHubActivity::class.java))
        })
        root.addView(navButton("血壓（外部袖帶紀錄）", Color.rgb(255, 45, 85)) {
            startActivity(Intent(this, OpticalBpActivity::class.java))
        })
        root.addView(navButton("目前未提供的功能（血糖／睡眠分期）", Color.rgb(142, 142, 147)) {
            startActivity(Intent(this, UnavailableFeatureActivity::class.java).putExtra("feature", "glucose"))
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247))
            addView(root)
        })
    }

    private fun send(path: String, button: Button, feedback: TextView) {
        button.isEnabled = false
        button.text = "正在連線…"
        feedback.text = "正在搜尋手錶並傳送量測命令…"
        WearCommandDispatcher.send(this, path) { outcome ->
            runOnUiThread {
                button.isEnabled = true
                button.text = "在手錶立即量測"
                val message = WearCommandDispatcher.feedback(
                    outcome,
                    sentMessage = "量測命令已送達手錶；請在手錶完成量測。命令送達不代表已取得讀值。"
                )
                feedback.text = message
                if (outcome !is WearCommandDispatcher.Outcome.Sent) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadHeartRateDaySummary(
        target: TextView,
        heartValue: TextView,
        heartDetail: TextView,
        watchMeasurementTime: Long?,
        conclusionHeadline: TextView,
        conclusionDetail: TextView
    ) {
        lifecycleScope.launch {
            val client = try {
                if (HealthConnectClient.getSdkStatus(this@VitalsHomeActivity) != HealthConnectClient.SDK_AVAILABLE) {
                    target.text = "今日心率摘要：Health Connect 目前不可用；手錶即時心率仍可獨立顯示。"
                    return@launch
                }
                HealthConnectClient.getOrCreate(this@VitalsHomeActivity)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                target.text = "今日心率摘要：無法連線 Health Connect；即時心率不受影響。"
                return@launch
            }

            val start = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val end = java.time.Instant.now()
            val range = TimeRangeFilter.between(start, end)
            val granted = try {
                client.permissionController.getGrantedPermissions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                target.text = "今日心率摘要：無法查詢 Health Connect 權限狀態；即時心率不受影響。"
                return@launch
            }

            val heartPermission = HealthPermission.getReadPermission(HeartRateRecord::class)
            val restingPermission = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
            val heartText = if (heartPermission !in granted) {
                "今日最低／最高：尚未授權讀取心率紀錄"
            } else try {
                val accumulator = HeartRateDailySummaryPolicy.Accumulator()
                client.foldHealthConnectRecordPages(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = range,
                    maxRecords = 25_000,
                    initialPageSize = 1_000,
                    foldPage = { records -> records.forEach { record ->
                        record.samples.forEach { sample ->
                            if (!sample.time.isBefore(start) && !sample.time.isAfter(end)) {
                                accumulator.add(HeartRateDailySummaryPolicy.Sample(
                                    sample.time.toEpochMilli(), sample.beatsPerMinute.toDouble(), record.metadata.dataOrigin.packageName
                                ))
                            }
                        }
                    } }
                )
                val latest = accumulator.latestSample()
                if (latest != null && (watchMeasurementTime == null || latest.timeMillis > watchMeasurementTime)) {
                    heartValue.text = "%.0f bpm".format(latest.bpm)
                    heartDetail.text = "Health Connect · 最近紀錄 ${HeartRatePresentationPolicy.homeTimestampLabel(latest.timeMillis, System.currentTimeMillis())} · 非即時量測 · 來源：${latest.source} · 訊號品質：來源未提供"
                }
                if (latest != null) {
                    withContext(Dispatchers.IO) {
                        RawSensorStore.open(this@VitalsHomeActivity).edit()
                            .putFloat("health_connect_heart_rate_bpm", latest.bpm.toFloat())
                            .putLong("health_connect_heart_rate_time", latest.timeMillis)
                            .putString("health_connect_heart_rate_origin", latest.source)
                            .commit()
                    }
                    val refreshed = HealthConclusionEngine.build(this@VitalsHomeActivity)
                    conclusionHeadline.text = refreshed.overallHeadline
                    conclusionDetail.text = refreshed.overallDetail + "\n點此查看全部計算與臨床對照 →"
                }
                val summary = accumulator.result()
                if (summary == null) "今日最低／最高：本日沒有有效 Health Connect 心率樣本"
                else "今日最低／最高：${"%.0f".format(summary.minimumBpm)}–${"%.0f".format(summary.maximumBpm)} bpm · ${summary.sampleCount} 筆 · ${summary.sourceCount} 個來源"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                "今日最低／最高：Health Connect 讀取失敗（${HealthConnectReadFailurePolicy.describe("心率", error)}）"
            }

            val restingText = if (restingPermission !in granted) {
                "靜息心率：尚未授權讀取"
            } else try {
                val records = client.readAllRecords(RestingHeartRateRecord::class, range, maxRecords = 5_000)
                val latest = HeartRateDailySummaryPolicy.latestResting(records.asSequence()
                    .filter { !it.time.isBefore(start) && !it.time.isAfter(end) }
                    .map {
                        HeartRateDailySummaryPolicy.Sample(it.time.toEpochMilli(), it.beatsPerMinute.toDouble(), it.metadata.dataOrigin.packageName)
                    }
                    .toList())
                if (latest == null) "靜息心率：本日沒有有效紀錄"
                else "靜息心率：${"%.0f".format(latest.bpm)} bpm · ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(latest.timeMillis))} · 來源：Health Connect"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                "靜息心率：Health Connect 讀取失敗（${HealthConnectReadFailurePolicy.describe("靜息心率", error)}）"
            }
            target.text = "$heartText\n$restingText"
        }
    }

    private fun tile(
        title: String,
        value: String,
        unit: String,
        accent: Int,
        measurementPath: String? = null,
        onClick: () -> Unit
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Color.WHITE)
        }
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        addView(label(title, 13f, accent, true))
        addView(label(value, 36f, Color.BLACK, true).also { it.setPadding(0, dp(4), 0, 0) })
        addView(label(unit, 13f, Color.rgb(110, 110, 120), false))
        addView(Button(this@VitalsHomeActivity).apply {
            text = "查看資料與趨勢"
            isAllCaps = false
            setTextColor(accent)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), accent)
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) }
            setOnClickListener { onClick() }
        })
        if (measurementPath != null) {
            val commandFeedback = label("", 12f, Color.rgb(90, 90, 100), false).apply {
                setPadding(0, dp(6), 0, 0)
                contentDescription = "量測命令狀態"
            }
            addView(Button(this@VitalsHomeActivity).apply {
                text = "在手錶立即量測"
                isAllCaps = false
                setTextColor(accent)
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.rgb(242, 244, 248))
                }
                layoutParams = LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) }
                setOnClickListener { send(measurementPath, this, commandFeedback) }
            })
            addView(commandFeedback)
        }
    }

    private fun rawSamplesLabel(raw: android.content.SharedPreferences, kind: String): String {
        val samples = raw.getInt("${kind}_raw_value_count", raw.getInt("${kind}_samples", 0))
        val events = raw.getInt("${kind}_event_count", 0)
        return if (samples > 0 || events > 0) "尚無健康讀值，請重測" else "待量測"
    }

    private fun ecgDisplayValue(raw: android.content.SharedPreferences): String {
        val rhythmBpm = raw.getFloat("ecg_rhythm_bpm", Float.NaN).takeIf { it.isFinite() }
        val samples = raw.getInt("ecg_raw_value_count", 0)
        val displayable = ecgResultEligible(raw)
        val decodedPoints = raw.getInt("ecg_research_decoded_value_count", 0)
        val status = EcgConsumerStatus.classify(
            eligible = displayable,
            rhythmTitle = raw.getString("ecg_rhythm_title", null),
            hasEvidence = samples > 0 || raw.getInt("ecg_event_count", 0) > 0 || decodedPoints > 0,
            measurementComplete = raw.getBoolean("ecg_final", false)
        )
        return if (displayable) "${status.label} · %.0f bpm".format(rhythmBpm) else status.label
    }

    private fun ecgResultEligible(raw: android.content.SharedPreferences): Boolean {
        val rhythmBpm = raw.getFloat("ecg_rhythm_bpm", Float.NaN).takeIf { it.isFinite() } ?: return false
        val fresh = HealthAnalysisFreshnessPolicy.isFreshSensor(
            raw.getLong("ecg_time", 0L), raw.getString("ecg_timestamp_source", null), System.currentTimeMillis()
        )
        return fresh && ClinicalVerdict.isEcgPulseDisplayable(
            qualityScore = raw.getInt("ecg_quality_score", 0),
            sampleCount = raw.getInt("ecg_raw_value_count", 0),
            sampleRate = raw.getFloat("ecg_sample_rate", 0f),
            status = raw.getString("ecg_status", "").orEmpty(),
            rrCount = raw.getInt("ecg_rhythm_rr_count", 0),
            wearQualified = if (raw.contains("ecg_wear_qualified")) raw.getBoolean("ecg_wear_qualified", false) else null,
            heartRateBpm = rhythmBpm.toDouble(),
            waveformEncoding = raw.getString("ecg_waveform_encoding", ""),
            timelineSource = raw.getString("ecg_research_timeline_source", "NORMALIZED_INDEX").orEmpty()
        )
    }

    private fun unavailableSensorReason(raw: android.content.SharedPreferences, kind: String, now: Long): String =
        VitalsUnavailableReason.describe(
            status = raw.getString("${kind}_status", "not_measured").orEmpty(),
            rawSampleCount = raw.getInt("${kind}_raw_value_count", 0),
            reason = raw.getString("${kind}_reason", "").orEmpty(),
            measuredAt = raw.getLong("${kind}_time", 0L),
            now = now,
            rawEventCount = raw.getInt("${kind}_event_count", 0)
        )

    private fun navButton(text: String, color: Int, click: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(color)
        }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) }
        setOnClickListener { click() }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
