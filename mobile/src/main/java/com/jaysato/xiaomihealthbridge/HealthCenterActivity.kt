package com.jaysato.xiaomihealthbridge

import android.content.Intent
import androidx.core.content.FileProvider
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.resume
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.text.DateFormat
import java.util.Date
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class HealthCenterActivity : ComponentActivity() {
    private lateinit var body: LinearLayout
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top); bottomMargin = dp(bottom)
    }
    private fun card(title: String, subtitle: String, color: Int, action: (() -> Unit)? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
        minimumHeight = dp(74)
        background = HealthVisuals.panel(context, 22, true)
        addView(text(title, 19f, Color.WHITE, true))
        addView(text(subtitle, 13f, HealthVisuals.secondary).apply { setLineSpacing(dp(4).toFloat(), 1f) }, margin(top = 7))
        action?.let {
            setOnClickListener { it() }
            isClickable = true
            stateListAnimator = null
        }
    }

    private fun watchAction(label: String, path: String) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        background = HealthVisuals.interactive(context, 18)
        setOnClickListener { sendWatchCommand(path) }
    }

    private fun watchActionRow(vararg actions: Pair<String, String>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        actions.forEachIndexed { index, (label, path) ->
            addView(watchAction(label, path), LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(40)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(HealthVisuals.background); addView(body) })
        renderHeader()
        loadCenter()
    }

    private fun renderHeader() {
        body.addView(text("‹  返回", 16f, HealthVisuals.blue).apply { setOnClickListener { finish() } }, margin(bottom = 16))
        body.addView(text("手錶健康中心", 31f, Color.WHITE, true))
        body.addView(text("WATCH HEALTH CENTER", 11f, Color.rgb(126, 174, 255), true).apply { letterSpacing = .12f }, margin(top = 5, bottom = 20))
        body.addView(card("今天的健康脈動", "把手錶感測、Health Connect 與同步狀態放在同一個可互動入口。點擊任一指標即可進入日／週／月趨勢。", Color.rgb(115, 105, 255)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }, margin(bottom = 12))
        body.addView(card("同步總覽", "檢查手錶連線、Health Connect 與最近一筆資料", Color.rgb(99, 178, 255)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }, margin(bottom = 12))
        val raw = RawSensorStore.open(this@HealthCenterActivity)
        val now = System.currentTimeMillis()
        val suiteSession = raw.getLong(
            SensorSuiteSessionPolicy.SESSION_KEY,
            raw.getLong("sensor_suite_session_started_at_ms", 0L)
        )
        val suiteId = raw.getString(SensorSuiteSessionPolicy.SUITE_ID_KEY, "").orEmpty()
        val measured = WatchSensorCatalog.items.mapNotNull { item ->
            val samples = RawSensorPayloadPolicy.displayPresenceCount(
                raw.getInt("${item.kind}_count_contract_version", 0),
                raw.getInt("${item.kind}_raw_value_count", -1).takeIf { it >= 0 },
                raw.getInt("${item.kind}_event_count", 0),
                raw.getInt("${item.kind}_preview_point_count", 0),
                raw.getInt("${item.kind}_samples", 0)
            )
            val rowSession = raw.getLong("${item.kind}_session_started_at_ms", 0L)
            val rowSuiteId = raw.getString("${item.kind}_suite_id", "").orEmpty()
            if (samples <= 0 || !SensorSuiteSessionPolicy.belongsToLatestSuite(rowSession, suiteSession, rowSuiteId, suiteId)) return@mapNotNull null
            SensorDisplayState.from(
                samples = samples,
                status = raw.getString("${item.kind}_status", "not_measured").orEmpty(),
                measuredAt = raw.getLong("${item.kind}_time", 0L),
                now = now,
                freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
                kind = item.kind,
                timestampSource = raw.getString("${item.kind}_timestamp_source", null)
            )
        }
        val freshCount = measured.count { SensorSuiteExportPolicy.countsAsFreshRawData(true, it) }
        val staleCount = measured.count { it == SensorDisplayState.STALE }
        val timestampUnknownCount = measured.count { it == SensorDisplayState.TIMESTAMP_UNKNOWN }
        val futureTimestampCount = measured.count { it == SensorDisplayState.FUTURE_TIMESTAMP }
        val sessionStarted = suiteSession
        val runState = SensorSuiteSessionPolicy.displayRunState(
            raw.getString(SensorSuiteSessionPolicy.RUN_STATE_KEY, "unknown").orEmpty(),
            sessionStarted,
            now
        )
        val sessionText = if (sessionStarted > 0L && sessionStarted <= now) {
            "最近全掃描啟動：${DateFormat.getDateTimeInstance().format(Date(sessionStarted))}"
        } else {
            "尚未收到有效的全掃描工作階段"
        }
        body.addView(card(
            "感測器量測新鮮度",
            "$sessionText · 掃描狀態：${SensorSuiteSessionPolicy.runStateLabel(runState)}\n本輪有讀值 ${measured.size} 個通道；App 顯示窗內（≤${SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS / 60_000L} 分鐘）${freshCount}、過期 ${staleCount}、時間戳在未來 ${futureTimestampCount}、時間戳未知 ${timestampUnknownCount}。未列入本輪的既有讀值仍保留於下方並標為歷史。顯示窗只描述資料時間，不代表分析輸入合格、品質通過、已校準或醫療有效。PPG／ECG／SpO₂／BioZ 不會背景常駐讀取；充電時貼腕通道會略過。點擊僅在需要時手動重掃。",
            if (SensorFreshnessSummaryPolicy.requiresAttention(
                    freshCount, staleCount, sessionStarted, timestampUnknownCount, futureTimestampCount
                )) {
                Color.rgb(255, 190, 77)
            } else {
                Color.rgb(67, 211, 166)
            }
        ) { sendWatchCommand("/control/sensor-suite") }, margin(bottom = 12))
    }

    private fun loadCenter() {
        val start = Instant.now().minus(7, ChronoUnit.DAYS)
        val filter = TimeRangeFilter.between(start, Instant.now())
        val loadErrorHandler = CoroutineExceptionHandler { _, error ->
            runOnUiThread {
                body.addView(card("資料暫時無法讀取", "Health Connect：${error.message ?: "權限或服務未就緒"}\n請確認權限後點擊重新檢查。", Color.rgb(255, 190, 77)) {
                    recreate()
                }, margin(top = 12, bottom = 12))
            }
        }
        lifecycleScope.launch(loadErrorHandler) {
            val nodes = runCatching { withTimeoutOrNull(2500L) { connectedNodes() } ?: emptyList() }.getOrDefault(emptyList())
            body.addView(card(
                if (nodes.isNotEmpty()) "手錶已連線" else "手錶未連線",
                if (nodes.isNotEmpty()) nodes.joinToString { it.displayName } else "請確認藍牙、Wi‑Fi 或 Wear OS 配對狀態",
                if (nodes.isNotEmpty()) Color.rgb(67, 211, 166) else Color.rgb(255, 190, 77)
            ), margin(bottom = 12))
            val client = HealthConnectClient.getOrCreate(this@HealthCenterActivity)
            val loading = card("資料讀取中…", "正在讀取 Health Connect 最近 7 天紀錄", Color.rgb(99, 178, 255))
            body.addView(loading, margin(bottom = 12))
            var permissionQueryFailure: Throwable? = null
            val grantedPermissions = try {
                client.permissionController.getGrantedPermissions()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                permissionQueryFailure = error
                null
            }
            suspend fun readCount(permissions: Set<String>, query: suspend () -> Int): HealthCenterMetricRead {
                HealthCenterReadStatePolicy.permissionFailure(permissions, grantedPermissions, permissionQueryFailure)?.let { return it }
                return HealthCenterReadStatePolicy.read(permissions.firstOrNull().orEmpty(), query = query)
            }
            var oxygenLatestSummary: String? = null
            val results = listOf(
                "心率" to readCount(setOf(HealthPermission.getReadPermission(HeartRateRecord::class)), { client.readAllRecords(HeartRateRecord::class, filter).size }),
                "血氧" to readCount(setOf(HealthPermission.getReadPermission(OxygenSaturationRecord::class)), {
                    val records = client.readAllRecords(OxygenSaturationRecord::class, filter)
                    val newest = records.maxByOrNull { it.time }
                    oxygenLatestSummary = newest?.let {
                        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.time.toEpochMilli()))
                        "最新 ${"%.0f".format(it.percentage.value)}% · $time · ${it.metadata.dataOrigin.packageName}"
                    }
                    records.size
                }),
                "睡眠" to readCount(setOf(HealthPermission.getReadPermission(SleepSessionRecord::class)), { client.readAllRecords(SleepSessionRecord::class, filter).size }),
                "體溫" to readCount(setOf(HealthPermission.getReadPermission(BodyTemperatureRecord::class)), {
                    client.readAllRecords(BodyTemperatureRecord::class, filter)
                        .count { it.metadata.dataOrigin.packageName != packageName }
                }),
                "爬升" to readCount(
                    setOf(HealthPermission.getReadPermission(ElevationGainedRecord::class)),
                    { client.readAllRecords(ElevationGainedRecord::class, filter).size }
                ),
                "樓層" to readCount(
                    setOf(HealthPermission.getReadPermission(FloorsClimbedRecord::class)),
                    { client.readAllRecords(FloorsClimbedRecord::class, filter).size }
                ),
                "補水" to readCount(setOf(HealthPermission.getReadPermission(HydrationRecord::class)), { client.readAllRecords(HydrationRecord::class, filter).size })
            )
            body.removeView(loading)
            body.addView(text("最近 7 天資料", 20f, Color.WHITE, true), margin(bottom = 10))
            results.forEach { (name, result) ->
                val status = when (result) {
                    is HealthCenterMetricRead.Success -> if (name == "血氧") {
                        "${result.count} 筆紀錄 · ${oxygenLatestSummary ?: "最近 7 天無有效 SpO₂ 值"} · 歷史紀錄，非目前量測"
                    } else "${result.count} 筆紀錄，可點擊查看趨勢"
                    HealthCenterMetricRead.Empty -> "讀取成功，最近 7 天沒有紀錄"
                    HealthCenterMetricRead.PermissionDenied -> "未授權讀取此資料類型；可至資料與權限設定授權"
                    is HealthCenterMetricRead.PermissionQueryFailed -> "權限狀態查詢失敗：${result.detail}；尚未判定是否授權"
                    is HealthCenterMetricRead.ReadFailed -> "${result.detail}；不代表資料為零"
                }
                val metric = when (name) { "心率" -> "heart_rate"; "血氧" -> "oxygen"; "睡眠" -> "sleep"; "體溫" -> "temperature"; else -> null }
                val color = if (result is HealthCenterMetricRead.Success) Color.rgb(67, 211, 166) else Color.rgb(255, 190, 77)
                body.addView(card(name, status, color, metric?.let { { startActivity(Intent(this@HealthCenterActivity, MetricDetailActivity::class.java).putExtra("metric", it)) } }), margin(bottom = 10))
            }
            body.addView(text("手錶能力", 20f, Color.WHITE, true), margin(top = 18, bottom = 10))
            val feature = getSharedPreferences(WearDataProcessor.FEATURE_PREFS, MODE_PRIVATE)
            val rawEvidence = RawSensorStore.open(this@HealthCenterActivity)
            val featureNow = System.currentTimeMillis()
            fun isFreshSensorFeature(field: String, maxAge: Long = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS): Boolean =
                HealthAnalysisFreshnessPolicy.isFreshSensor(
                    feature.getLong("${field}_timestamp", 0L),
                    feature.getString("${field}_timestamp_source", null),
                    featureNow,
                    maxAge
                )
            val wornText = when {
                !feature.contains("worn") || !isFreshSensorFeature("worn", HeartRatePresentationPolicy.WEAR_STATE_FRESHNESS_MILLIS) -> "佩戴狀態尚未掃描／資料已過期或時間來源不可信"
                feature.getBoolean("worn", false) -> "離腕感測器回報已佩戴；不代表生理校準完成"
                else -> "離腕感測器回報未貼合；狀態待確認"
            }
            val featureLines = buildList {
                add(wornText)
                val healthServicesHeartRate = HeartRatePresentationPolicy.visibleHealthServicesBpm(
                    feature.getFloat("whs_heart_rate", Float.NaN),
                    feature.getLong("whs_heart_rate_timestamp", 0L),
                    feature.getString("whs_heart_rate_timestamp_source", null),
                    featureNow,
                    HealthAnalysisFreshnessPolicy.RECENT_SENSOR_INPUT_MILLIS
                )
                val ppgMeasurementTime = HealthAnalysisFreshnessPolicy.resolveStoredMeasurementTime(
                    rawEvidence.getLong("ppg_time", 0L),
                    rawEvidence.getLong("ppg_measurement_time", 0L)
                )
                val ppgTimestampSource = rawEvidence.getString("ppg_timestamp_source", null)
                val ppgResearchCandidate = HealthCenterMetricCandidatePolicy.ppgResearchCandidate(
                    Regex("pr=([0-9.]+)")
                        .find(rawEvidence.getString("ppg_calibration", "").orEmpty())
                        ?.groupValues?.getOrNull(1),
                    rawEvidence.getInt("ppg_quality_score", 0),
                    rawEvidence.getString("ppg_status", "").orEmpty(),
                    ppgMeasurementTime,
                    featureNow,
                    rawEvidence.getFloat("ppg_sample_rate", 0f),
                    rawEvidence.getBoolean("ppg_channel_mapping_verified", false),
                    ppgTimestampSource,
                    rawEvidence.getBoolean("ppg_wear_qualified", false)
                )
                val resolvedHeartRate = ResearchMetricResolver.resolve(
                    listOfNotNull(
                        healthServicesHeartRate?.let {
                            ResearchMetricCandidate(ResearchNumericAdapter.finiteDouble(it), "HEALTH_SERVICES", feature.getLong("whs_heart_rate_timestamp", 0L), feature.getString("whs_heart_rate_timestamp_source", null), null, false)
                        },
                        ppgResearchCandidate
                    ), featureNow
                )
                if (resolvedHeartRate.value != null) {
                    val wearContext = HeartRatePresentationPolicy.wearContext(
                        feature.contains("worn"), feature.getBoolean("worn", false),
                        feature.getLong("worn_timestamp", 0L), featureNow,
                        feature.getString("worn_timestamp_source", null)
                    )
                    add("心率 ${"%.0f".format(resolvedHeartRate.value)} bpm · ${resolvedHeartRate.source} · ${resolvedHeartRate.state.displayLabel} · $wearContext")
                }
                if (feature.contains("temperature")) {
                    val temperature = ResearchMetricResolver.resolve(
                        listOf(
                            ResearchMetricCandidate(
                                ResearchNumericAdapter.finiteDouble(feature.getFloat("temperature", Float.NaN)),
                                feature.getString("temperature_source", "TEMPERATURE_RAW") ?: "TEMPERATURE_RAW",
                                feature.getLong("temperature_timestamp", 0L),
                                feature.getString("temperature_timestamp_source", null),
                                null,
                                true
                            )
                        ), featureNow
                    )
                    temperature.value?.let { add("溫度原始值 ${"%.1f".format(it)}（單位未驗證）· ${temperature.source} · ${temperature.state.displayLabel}") }
                }
                if (feature.contains("heading") && isFreshSensorFeature("heading")) add("指南針 ${"%.0f".format(feature.getFloat("heading", 0f))}°")
                if (feature.contains("altitude") && isFreshSensorFeature("altitude")) add("標準大氣公式估算高度 ${"%.1f".format(feature.getFloat("altitude", 0f))} m（非實際海拔／爬升）")
                if (feature.contains("light") && isFreshSensorFeature("light")) add("環境光 ${"%.0f".format(feature.getFloat("light", 0f))} lx")
                val featureLabels = listOf(
                    "temperature" to "溫度",
                    "heading" to "指南針",
                    "altitude" to "高度",
                    "light" to "環境光"
                )
                val unavailableFeatures = featureLabels.filter { (field, _) ->
                    feature.contains(field) && !isFreshSensorFeature(field)
                }.map { it.second }
                if (unavailableFeatures.isNotEmpty()) {
                    add("${unavailableFeatures.joinToString("、")}快照已過期或時間來源不可信；未列為目前讀值（顯示窗 30 分鐘）")
                } else if (size == 1) {
                    add("功能快照尚未同步；請從裝置頁重新掃描")
                }
            }
            body.addView(card("最近功能快照", featureLines.joinToString("\n"), Color.rgb(99, 178, 255)), margin(bottom = 10))
            body.addView(text("立即量測", 20f, Color.WHITE, true), margin(top = 18, bottom = 10))
            body.addView(card(
                "手錶研究量測控制台",
                "僅在你點擊時啟動手錶前景量測；不建立背景常駐掃描。ECG／PPG／BIOZ 僅供原始訊號與品質研究，不能作醫療診斷。",
                Color.rgb(0, 180, 150)
            ), margin(bottom = 10))
            body.addView(watchActionRow(
                "體表溫度" to "/control/temperature-now",
                "佩戴狀態" to "/control/off-body-now"
            ), margin(bottom = 8))
            body.addView(watchActionRow(
                "PPG 波形" to "/control/ppg-now",
                "ECG 波形" to "/control/ecg-now",
                "BIOZ 波形" to "/control/bioz-now"
            ), margin(bottom = 8))
            body.addView(watchActionRow(
                "感測器全掃描" to "/control/sensor-suite",
                "重新檢查狀態" to "/control/status"
            ), margin(bottom = 12))
            body.addView(card(
                "已偵測到的手錶感測器",
                "公開 Sensor HAL、Wear Health Services 與 Xiaomi 演算法通道。PPG／ECG／BIOZ 只顯示訊號品質，不是醫療診斷。",
                Color.rgb(177, 143, 255)
            ), margin(bottom = 10))
            val rawPrefs = RawSensorStore.open(this@HealthCenterActivity)
            val acquisitionPrefs = getSharedPreferences("sensor_acquisition_diagnostics", MODE_PRIVATE)
            WatchSensorCatalog.items.groupBy { it.group }.forEach { (group, items) ->
                body.addView(text(group, 16f, Color.rgb(180, 190, 210), true), margin(top = 8, bottom = 8))
                items.forEach { item ->
                    val summary = rawPrefs.getString("${item.kind}_summary", null)
                    val hasSummary = !summary.isNullOrBlank()
                    val status = rawPrefs.getString("${item.kind}_status", null)
                    val reason = rawPrefs.getString("${item.kind}_reason", null)
                    val samples = RawSensorPayloadPolicy.displayPresenceCount(
                        rawPrefs.getInt("${item.kind}_count_contract_version", 0),
                        rawPrefs.getInt("${item.kind}_raw_value_count", -1).takeIf { it >= 0 },
                        rawPrefs.getInt("${item.kind}_event_count", 0),
                        rawPrefs.getInt("${item.kind}_preview_point_count", 0),
                        rawPrefs.getInt("${item.kind}_samples", 0)
                    )
                    val measuredAt = rawPrefs.getLong("${item.kind}_time", 0L)
                    val rowSession = rawPrefs.getLong("${item.kind}_session_started_at_ms", 0L)
                    val suiteSession = rawPrefs.getLong(
                        SensorSuiteSessionPolicy.SESSION_KEY,
                        rawPrefs.getLong("sensor_suite_session_started_at_ms", 0L)
                    )
                    val rowSuiteId = rawPrefs.getString("${item.kind}_suite_id", "").orEmpty()
                    val latestSuiteId = rawPrefs.getString(SensorSuiteSessionPolicy.SUITE_ID_KEY, "").orEmpty()
                    val inLatestSuite = SensorSuiteSessionPolicy.belongsToLatestSuite(
                        rowSession, suiteSession, rowSuiteId, latestSuiteId
                    )
                    val watchStatusSession = rawPrefs.getLong("${item.kind}_watch_status_session_started_at_ms", 0L)
                    val watchStatusCurrent = SensorSuiteProvenancePolicy.belongsToSession(rowSession, watchStatusSession)
                    val watchStatus = rawPrefs.getString("${item.kind}_watch_status", "unknown").orEmpty()
                    val watchSkippedCharging = watchStatusCurrent && watchStatus == "skipped_charging"
                    val watchStatusText = if (watchStatusCurrent) {
                        buildString {
                            append("手錶原始狀態 $watchStatus")
                            if (rawPrefs.contains("${item.kind}_wear_qualified")) {
                                append(" · 全掃描佩戴門檻 ${if (rawPrefs.getBoolean("${item.kind}_wear_qualified", false)) "通過" else "未通過"}")
                            } else if (!watchSkippedCharging) append(" · 全掃描佩戴門檻未知")
                            rawPrefs.getString("${item.kind}_watch_reason", null)?.takeIf(String::isNotBlank)?.let {
                                append("\n手錶原因：$it")
                            }
                        }
                    } else "手錶原始狀態／佩戴旗標：本工作階段未記錄"
                    val acquisitionState = SensorAcquisitionDiagnosticState.classify(
                        registration = acquisitionPrefs.getString("${item.kind}_watch_registration", "unknown").orEmpty(),
                        watchSessionStartedAt = acquisitionPrefs.getLong("${item.kind}_watch_session_started_at", 0L),
                        rowSessionStartedAt = rowSession,
                        watchEventCount = acquisitionPrefs.getInt("${item.kind}_watch_event_count", 0),
                        phoneReceivedCount = acquisitionPrefs.getInt("${item.kind}_phone_received_count", 0),
                        phoneSessionStartedAt = acquisitionPrefs.getLong("${item.kind}_phone_session_started_at", 0L),
                        watchSuiteId = acquisitionPrefs.getString("${item.kind}_phone_suite_id", "").orEmpty(),
                        rowSuiteId = rowSuiteId
                    )
                    val displayState = SensorDisplayState.from(
                        samples = samples,
                        status = status.orEmpty(),
                        measuredAt = measuredAt,
                        now = System.currentTimeMillis(),
                        freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
                        kind = item.kind,
                        timestampSource = rawPrefs.getString("${item.kind}_timestamp_source", null)
                    )
                    val rowDisplayState = SensorRowPresentationPolicy.displayState(summary, displayState, watchSkippedCharging)
                    val ageText = measuredAt.takeIf { it > 0L }?.let {
                        val currentTime = System.currentTimeMillis()
                        if (it > currentTime) "時間戳在未來，資料時間不可信"
                        else {
                            val ageMinutes = (currentTime - it) / 60_000L
                            if (ageMinutes < 1) "剛剛更新" else "${ageMinutes} 分鐘前更新"
                        }
                    } ?: "沒有量測時間"
                    val detailLines = when {
                        hasSummary -> listOfNotNull(
                            if (inLatestSuite) displayState.label else "歷史資料（非最新掃描） · ${displayState.label}",
                            if (inLatestSuite) "擷取診斷：${acquisitionState.label}" else null,
                            ageText, summary, status?.let { "手機呈現狀態 $it" }, reason
                        )
                        else -> listOf(
                            if (inLatestSuite) SensorRowPresentationPolicy.missingSummaryMessage(acquisitionState) else "本輪尚無資料；歷史快照保留",
                            if (inLatestSuite) "擷取診斷：${acquisitionState.label}" else "擷取診斷：未記錄於本輪",
                            "尚未量測；請到手機「裝置 → 手錶工具」啟動完整掃描"
                        )
                    }
                    val subtitle = SensorRowPresentationPolicy.orderStatusLines(
                        watchStatusText, detailLines, watchSkippedCharging
                    ).joinToString("\n")
                    val color = when (rowDisplayState) {
                        SensorDisplayState.NOT_MEASURED, SensorDisplayState.STALE, SensorDisplayState.FUTURE_TIMESTAMP,
                        SensorDisplayState.TIMESTAMP_UNKNOWN -> Color.rgb(255, 190, 77)
                        SensorDisplayState.QUALITY_REJECTED, SensorDisplayState.CALIBRATION_MISSING,
                        SensorDisplayState.CONDITION_NOT_MET, SensorDisplayState.RAW_ONLY -> Color.rgb(255, 190, 77)
                        SensorDisplayState.SIGNAL_VALID_RESEARCH -> Color.rgb(99, 178, 255)
                        SensorDisplayState.DERIVED_ESTIMATE -> Color.rgb(99, 178, 255)
                        SensorDisplayState.AVAILABLE -> when (status) {
                        "has_data", "worn" -> Color.rgb(67, 211, 166)
                        "derived_estimate" -> Color.rgb(99, 178, 255)
                        "off_wrist", "needs_contact", "needs_motion", "needs_oem_mode" -> Color.rgb(255, 190, 77)
                        else -> if (hasSummary) Color.rgb(255, 190, 77) else Color.rgb(120, 130, 155)
                        }
                    }
                    body.addView(card(item.label, subtitle, color) {
                        startActivity(Intent(this@HealthCenterActivity, RawSensorDetailActivity::class.java).putExtra("kind", item.kind))
                    }, margin(bottom = 10))
                }
            }
            val syncDiagnostics = getSharedPreferences(HeartRateDataListenerService.PREFS, MODE_PRIVATE)
            val rejectionCount = syncDiagnostics.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_REJECTION_COUNT, 0)
            val orderingRejectionCount = syncDiagnostics.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_ORDERING_REJECTION_COUNT, 0)
            val payloadRejectionCount = syncDiagnostics.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_PAYLOAD_REJECTION_COUNT, 0)
            val duplicateRejectionCount = syncDiagnostics.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_DUPLICATE_REJECTION_COUNT, 0)
            val lastRejectedKind = syncDiagnostics.getString(WearDataProcessor.KEY_RAW_SNAPSHOT_LAST_REJECTED_KIND, "").orEmpty()
            val lastRejectionReason = syncDiagnostics.getString(WearDataProcessor.KEY_RAW_SNAPSHOT_LAST_REJECTION_REASON, "").orEmpty()
            val lastRejectionTime = syncDiagnostics.getLong(WearDataProcessor.KEY_RAW_SNAPSHOT_LAST_REJECTION_TIME, 0L)
            val rejectionReasonLabel = when (lastRejectionReason) {
                "older_than_active_session" -> "舊掃描工作階段資料"
                "older_session" -> "較舊量測工作階段"
                "older_timestamp" -> "較舊封包時間戳"
                "preview_after_final" -> "完成後才到達的預覽封包"
                "duplicate_or_older_heart_rate_sample" -> "重複或較舊的心率封包"
                "oversized_waveform" -> "波形封包超出安全大小"
                "oversized_latest" -> "摘要封包超出安全大小"
                "oversized_event_timestamps" -> "事件時間戳超出安全數量"
                "oversized_event_sample_counts" -> "事件樣本計數超出安全數量"
                "mismatched_event_metadata" -> "事件中繼資料數量不一致"
                "oversized_event_metadata" -> "事件中繼資料超出安全大小"
                "oversized_serialized_data_map" -> "感測封包總大小超出傳輸上限；原始量測未截斷並已記錄拒收"
                "unsupported_payload_rejection_marker" -> "封包含有無法辨識的拒收標記，已拒絕保存"
                else -> "無"
            }
            val lastRejectionLabel = if (lastRejectionTime > 0L) {
                "最近一次：$lastRejectedKind · $rejectionReasonLabel · ${DateFormat.getDateTimeInstance().format(Date(lastRejectionTime))}"
            } else {
                "最近一次：尚無記錄"
            }
            body.addView(card(
                "同步封包拒收診斷",
                "最終封包拒收總數：$rejectionCount 次\n分類計數（此版本更新後）：排序 $orderingRejectionCount、大小／格式 $payloadRejectionCount、重複心率 $duplicateRejectionCount 次\n$lastRejectionLabel\n${if (rejectionCount == 0) "目前沒有已記錄的最終封包拒收；這不代表感測器已成功回報。" else "封包已抵達手機但未存入健康資料；分類計數不含更新前的歷史細分。"}\n不包含手錶未產生事件、權限／OEM 模式限制或訊號品質不合格。",
                if (rejectionCount == 0) Color.rgb(99, 178, 255) else Color.rgb(255, 190, 77)
            ) { exportRawSensorData() }, margin(top = 10, bottom = 10))
            body.addView(Button(this@HealthCenterActivity).apply {
                text = "匯出原始感測專業資料（JSON）"; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE); background = HealthVisuals.interactive(context, 20)
                setOnClickListener { exportRawSensorData() }
            }, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) })
            body.addView(text("手錶功能入口", 20f, Color.WHITE, true), margin(top = 18, bottom = 10))
            body.addView(card("體表溫度趨勢", "使用手錶前景量測與 Health Connect 紀錄；有資料時可查看趨勢，沒有事件時不會補造數值。", Color.rgb(255, 112, 145)) {
                startActivity(Intent(this@HealthCenterActivity, MetricDetailActivity::class.java).putExtra("metric", "temperature"))
            }, margin(bottom = 10))
            body.addView(card("氣壓與高度估算", "氣壓可顯示原始 hPa；目前高度值以標準大氣公式估算，不能代表實際海拔、樓層或運動爬升。", Color.rgb(86, 174, 255)) {
                showFeatureInfo("氣壓與高度估算", "氣壓會受天氣與室內環境影響。未建立活動起始基準並驗證來源前，不將公式高度當作實際海拔、樓層或爬升。")
            }, margin(bottom = 10))
            body.addView(card("指南針與返航導航", "手錶使用旋轉向量、陀螺儀與磁力計計算方向；返航功能只在手錶端啟動時使用定位。", Color.rgb(104, 130, 255)) {
                showFeatureInfo("指南針與返航導航", "請從手錶 App 開啟指南針或返航導航。手機端負責顯示功能狀態，不會在背景持續讀取磁力計或 GPS。")
            }, margin(bottom = 10))
            body.addView(card("跌倒風險與運動姿勢", "運動期間使用加速度計與陀螺儀偵測高衝擊與姿態變化；這是提醒功能，不是醫療診斷。", Color.rgb(255, 164, 82)) {
                showFeatureInfo("跌倒風險偵測", "只有手錶運動工作階段啟動時才會分析高衝擊事件，避免背景常駐造成耗電。若出現不適，請以醫療器材或專業人員確認。")
            }, margin(bottom = 10))
            body.addView(card("手勢控制", "可用加速度計、陀螺儀與旋轉向量辨識抬腕、翻腕與操作姿勢；設定由手錶端控制。", Color.rgb(177, 143, 255)) {
                showFeatureInfo("手勢控制", "手勢辨識不會在手機背景常駐。請從手錶的「手勢與夜間省電設定」開關，啟用後才會在需要時取樣。")
            }, margin(bottom = 10))
            body.addView(card("環境光／夜間省電", "環境光感測器會在手錶前景頁面切換 App 內亮度；夜間模式降低亮度與感測頻率，手機不會強制改系統亮度。", Color.rgb(67, 211, 166)) {
                showFeatureInfo("環境光與夜間省電", "目前採用手錶 App 內亮度控制，符合 Wear OS 權限限制。環境光與手勢不做背景常駐，因此可降低耗電。")
            }, margin(bottom = 10))
            body.addView(card("晶片功能與資料", "依 MAX86178／CT7117／BMI26x／AK0991x／ICP101xx／LTR311／LifeQ 對照他錶功能；查看已同步原始資料並可遠端啟動手錶量測。", Color.rgb(31, 111, 235)) {
                startActivity(Intent(this@HealthCenterActivity, ChipFeatureHubActivity::class.java))
            }, margin(bottom = 10))
            body.addView(card("他錶功能對照", "Apple Watch／Galaxy Watch／Pixel Watch 健康功能 vs 本 App；並顯示已同步文獻公式欄位。", Color.rgb(255, 151, 94)) {
                startActivity(Intent(this@HealthCenterActivity, CompetitorParityActivity::class.java))
            }, margin(bottom = 10))
            body.addView(card("手錶功能對照表", "對照常見智慧手錶／Apple Watch 功能體驗：可用／研究／部分／不可用。醫療 ECG／AF 明確標示不可宣稱等同。", Color.rgb(90, 160, 255)) {
                startActivity(Intent(this@HealthCenterActivity, WatchFeatureMapActivity::class.java))
            }, margin(bottom = 10))
            body.addView(card("臨床參考實驗室", "PPG／ECG／BIOZ／體溫／全掃描：醫療風格圖表、高品質偵測與最終判定（本程式提供參考，非醫療用途）。", Color.rgb(0, 180, 150)) {
                startActivity(Intent(this@HealthCenterActivity, ClinicalLabActivity::class.java))
            }, margin(bottom = 10))
            body.addView(card("感測器校準中心", "用外部參考設備建立體溫、氣壓、環境光、磁力計、加速度與陀螺儀的研究 profile；保存 slope、offset、fit RMSE 與留一驗證 RMSE。", Color.rgb(92, 148, 255)) {
                startActivity(Intent(this@HealthCenterActivity, SensorCalibrationCenterActivity::class.java))
            }, margin(bottom = 10))
            body.addView(card("健康運算中心", "本機計算：活動熱量（BMR + MET）、睡眠紀錄可用性、個人 HRV 描述性趨勢、合格運動心率資料的 TRIMP、心率區間、步數／距離去重。\n\n個別研究／訓練估算會依公式輸入條件計算；壓力、身體能量、恢復與準備度的整合總分尚未實作。活動卡路里不作訓練負荷代理。本機規則分析不是醫療診斷，也不會把缺少的 HRV、ECG 或血壓推算成真實量測。", Color.rgb(255, 151, 94)) {
                startActivity(Intent(this@HealthCenterActivity, AiHealthAnalysisActivity::class.java))
            }, margin(bottom = 10))
            body.addView(card("目前未提供的醫療級資料", "1. ECG 缺少導聯與臨床校準，不能診斷房顫、缺血或心律疾病。\n2. BIOZ 私有通道沒有原廠阻抗換算公式，不能輸出體脂或水分。\n3. PPG 只做波形與品質分析，不能直接診斷疾病。\n4. 房顫等判定仍需足夠 RR 間期與臨床驗證；本 App 只顯示研究用節律指標。\n5. 血壓、連續血糖、PSG 睡眠分期與小米私有 PAI 沒有第三方 API。\n\n這些限制會保留在整合中心，不會偽造醫療單位。", Color.rgb(120, 130, 155)))
            body.addView(card("ECG 型號相容性稽核", "目前裝置為 Xiaomi Watch 2 Pro。網路找到的 Xiaomi ECG-V1 文件標示相容裝置為 Xiaomi Watch 5 或更新型號，並非 Watch 2 Pro；其中的 24-bit、500 SPS 與臨床房顫數據不可套用本手錶。\n\n本 App 可保留公開 Sensor HAL 原始資料與研究級品質分析，但不會把 ECG-V1 的係數或臨床結果冒充 Watch 2 Pro 校準。", Color.rgb(255, 170, 90)))
            body.addView(text("資料診斷", 20f, Color.WHITE, true), margin(top = 18, bottom = 10))
            body.addView(card(
                "為什麼有些項目是空白？（對齊 DATA_GAPS／SUPPORT_MATRIX）",
                "心率：WHS／本 App 可寫入 HeartRateRecord；原生 Xiaomi 心率頁讀值不會自動進 HC。\n\nHRV：無公開逐拍 R-R；ECG／PPG 僅估研究用 RR，不由單一 BPM 偽算。\n\nECG／BIOZ／PPG：公開原始通道有波形與品質，但無導聯校準／阻抗公式／疾病診斷。\n\n血壓／血糖／PAI：無公開 API，不破解原廠 HAL。\n\n睡眠階段：只有總時長，無驗證 REM／深睡。\n\nSpO₂／體溫／氣壓／樓層／GPS：需前景量測或運動事件；MAX86178 採依序獨占；無事件顯示空白，不造 0。",
                Color.rgb(99, 178, 255)
            ))
            body.addView(text("分析工具", 20f, Color.WHITE, true), margin(top = 18, bottom = 10))
            body.addView(Button(this@HealthCenterActivity).apply {
                text = "重新檢查同步與權限"; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE); background = HealthVisuals.interactive(context, 20)
                setOnClickListener { recreate() }
            }, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) })
            body.addView(Button(this@HealthCenterActivity).apply {
                text = "開啟健康趨勢分析"; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE); background = HealthVisuals.interactive(context, 20)
                setOnClickListener { startActivity(Intent(this@HealthCenterActivity, AiHealthAnalysisActivity::class.java)) }
            }, LinearLayout.LayoutParams(-1, dp(58)))
            body.addView(text("健康趨勢僅供參考；本頁不提供醫療診斷。", 12f, HealthVisuals.secondary), margin(top = 12))
        }
    }

    private fun showFeatureInfo(title: String, message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun sendWatchCommand(path: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                val node = WearCommandTargetPolicy.uniqueTarget(nodes)
                if (node == null) {
                    android.widget.Toast.makeText(this, WearCommandTargetPolicy.failureMessage(nodes.size), android.widget.Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                Wearable.getMessageClient(this).sendMessage(node.id, path, byteArrayOf())
                    .addOnSuccessListener {
                        val message = if (path == "/control/sensor-suite") {
                            "掃描命令已送至 Data Layer；不代表手錶已啟動，請查看手錶回報"
                        } else {
                            "命令已送至 Data Layer：$path"
                        }
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { error ->
                        android.widget.Toast.makeText(this, "控制失敗：${error.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { error ->
                android.widget.Toast.makeText(this, "無法取得手錶：${error.message}", android.widget.Toast.LENGTH_LONG).show()
            }
    }

    private fun exportRawSensorData() {
        val prefs = RawSensorStore.open(this@HealthCenterActivity)
        val preview = RawDiagnosticExportPreview.summarize(WatchSensorCatalog.items.map { item ->
            RawDiagnosticExportPreview.Channel(
                label = item.label,
                measuredAt = prefs.getLong("${item.kind}_time", 0L),
                sampleCount = prefs.getInt("${item.kind}_samples", 0),
                latestPointCount = SensorSuiteCsvParser.parse(
                    prefs.getString("${item.kind}_latest", "").orEmpty(), 512
                ).size,
                waveformPointCount = SensorSuiteCsvParser.parse(
                    prefs.getString("${item.kind}_waveform", "").orEmpty()
                ).size,
                sourceWaveformPointCount = SensorSuiteCsvParser.parse(
                    prefs.getString("${item.kind}_source_waveform", "").orEmpty()
                ).size
            )
        })
        val timeRange = if (preview.earliestMeasurementAt != null && preview.latestMeasurementAt != null) {
            "量測時間範圍：${DateFormat.getDateTimeInstance().format(Date(preview.earliestMeasurementAt))} 至 ${DateFormat.getDateTimeInstance().format(Date(preview.latestMeasurementAt))}"
        } else {
            "量測時間範圍：沒有可用時間戳"
        }
        val previewText = """
            即將匯出的本機資料預覽
            通道數：${preview.measuredChannelCount}（${preview.channels.take(8).joinToString("、")}${if (preview.channels.size > 8) " 等 ${preview.channels.size} 類" else ""}）
            已存樣本：${preview.storedSampleCount}；最新值預覽點：${preview.previewPointCount}
            波形點：${preview.waveformPointCount}；來源波形點：${preview.sourceWaveformPointCount}
            $timeRange

            內容另含裝置／通道狀態與診斷欄位，不含完整健康歷史。下一步會交由 Android 分享選單，只有你選定接收 App 才會傳出；接收端會收到明文 JSON。本 App 不會自動上傳。暫存於 App 快取，預計 15 分鐘後清理（程序結束時可能延後；系統也可能先行清除）。
            原始波形未經裝置校準，不是醫療診斷。請檢查接收對象後再分享。
        """.trimIndent()
        android.app.AlertDialog.Builder(this)
            .setTitle("分享原始感測資料？")
            .setMessage(previewText)
            .setNegativeButton("取消", null)
            .setPositiveButton("繼續至分享選單") { _, _ -> createRawSensorDataExport() }
            .show()
    }

    private fun createRawSensorDataExport() {
        val prefs = RawSensorStore.open(this@HealthCenterActivity)
        val root = JSONObject().apply {
            put("schema", "xiaomi-health-bridge.raw-sensors.v3")
            put("exported_at_epoch_ms", System.currentTimeMillis())
            put("device", "Xiaomi Watch 2 Pro")
            put("medical_status", "Raw sensor research data only; not a medical diagnosis")
            put("access_assessment", JSONObject().apply {
                put("public_sensor_hal", "available")
                put("health_services", "available_for_supported_metrics")
                put("oem_private_channels", "registered_but_not_guaranteed_to_emit_events")
                put("selinux_or_vendor_router", "may_block_private_diagnostic_transport")
                put("private_mode_override", "not_attempted")
                put("calibration_policy", "raw hardware events are kept separate from derived estimates; no fabricated medical conversion")
            })
            put("blocked_private_channels", JSONArray(listOf(
                "MAX86178 PPG / SpO2 private diagnostic transport",
                "BIOZ impedance conversion coefficients",
                "ECG lead and clinical calibration metadata"
            )))
            put("limitations", JSONArray(listOf(
                "ECG lacks lead and clinical calibration",
                "BIOZ has no OEM impedance conversion formula",
                "PPG quality analysis is not disease diagnosis",
                "AF screening needs validated RR intervals"
            )))
            put("ecg_calibration", JSONObject().apply {
                put("status", "unavailable_from_public_oem_documents")
                put("lead_configuration", JSONObject.NULL)
                put("lead_equivalent", JSONObject.NULL)
                put("adc_lsb_to_mv", JSONObject.NULL)
                put("gain", JSONObject.NULL)
                put("sample_clock_hz", JSONObject.NULL)
                put("clinical_validation", false)
                put("chip_reference", JSONObject().apply {
                    put("part", "Analog Devices MAX86178")
                    put("ecg_architecture", "single_lead_capable")
                    put("ecg_input_range_typical_mv", 1000)
                    put("ecg_ac_dynamic_range_mv_pp", 200)
                    put("ecg_bandwidth_reference_hz", "0.05-40")
                    put("reference_adc_enob", 15.3)
                    put("reference_noise_uv_rms", 0.72)
                    put("source", "https://www.analog.com/media/en/technical-documentation/data-sheets/max86178.pdf")
                })
                put("interpretation", "保留 Android Sensor API 原始事件值；不套用未驗證的電壓或臨床節律換算。")
                put("official_sources", JSONArray(listOf(
                    "https://www.mi.com/latin-es/product/xiaomi-watch-2-pro/specs/",
                    "https://www.mi.com/es/support/user-guide/"
                )))
            })
            val feature = getSharedPreferences(WearDataProcessor.FEATURE_PREFS, MODE_PRIVATE)
            put("feature_snapshot", JSONObject().apply {
                put("timestamp_epoch_ms", feature.getLong("timestamp", 0L))
                if (feature.contains("worn")) {
                    val timestamp = feature.getLong("worn_timestamp", 0L)
                    val source = feature.getString("worn_timestamp_source", null)
                    val state = FeatureSnapshotExportPolicy.freshnessState(
                        timestamp, source, System.currentTimeMillis(), HeartRatePresentationPolicy.WEAR_STATE_FRESHNESS_MILLIS
                    )
                    put("worn_state", state)
                    put("worn", if (state == "fresh") feature.getBoolean("worn", false) else JSONObject.NULL)
                    put("worn_timestamp_epoch_ms", timestamp.takeIf { it > 0L } ?: JSONObject.NULL)
                    put("worn_timestamp_source", source ?: "timestamp_source_unreported")
                    put("worn_age_ms", if (timestamp > 0L) System.currentTimeMillis() - timestamp else JSONObject.NULL)
                }
                if (feature.contains("whs_heart_rate")) {
                    val timestamp = feature.getLong("whs_heart_rate_timestamp", 0L)
                    val source = feature.getString("whs_heart_rate_timestamp_source", null)
                    val state = FeatureSnapshotExportPolicy.measurementFreshnessState(
                        timestamp, source, System.currentTimeMillis(), HealthAnalysisFreshnessPolicy.RECENT_SENSOR_INPUT_MILLIS
                    )
                    put("whs_heart_rate_state", state)
                    put("whs_heart_rate_bpm", if (state == "fresh") feature.getFloat("whs_heart_rate", 0f).toDouble() else JSONObject.NULL)
                    put("whs_heart_rate_timestamp_epoch_ms", timestamp.takeIf { it > 0L } ?: JSONObject.NULL)
                    put("whs_heart_rate_timestamp_source", source ?: "timestamp_source_unreported")
                    put("whs_heart_rate_age_ms", if (timestamp > 0L) System.currentTimeMillis() - timestamp else JSONObject.NULL)
                }
                if (feature.contains("temperature")) {
                    val temperatureTimestamp = feature.getLong("temperature_timestamp", 0L)
                    val temperatureSource = feature.getString("temperature_timestamp_source", null)
                    val temperatureState = FeatureSnapshotExportPolicy.freshnessState(
                        temperatureTimestamp, temperatureSource, System.currentTimeMillis()
                    )
                    put("temperature_state", temperatureState)
                    put("temperature_age_ms", if (temperatureTimestamp > 0L) System.currentTimeMillis() - temperatureTimestamp else JSONObject.NULL)
                    put("temperature_timestamp_epoch_ms", temperatureTimestamp.takeIf { it > 0L } ?: JSONObject.NULL)
                    put("temperature_timestamp_source", temperatureSource ?: "timestamp_source_unreported")
                    if (temperatureState == "fresh") {
                        put("temperature_device_raw_value", feature.getFloat("temperature", 0f).toDouble())
                        put("temperature_unit_status", "unverified")
                        put("temperature_source", feature.getString("temperature_source", "來源未標示"))
                    } else {
                        put("temperature_device_raw_value", JSONObject.NULL)
                        put("temperature_unit_status", "unverified")
                    }
                }
                val now = System.currentTimeMillis()
                listOf(
                    Triple("heading", "heading_deg") { value: Float -> value.toDouble() },
                    Triple("altitude", "altitude_m") { value: Float -> value.toDouble() },
                    Triple("light", "ambient_light_lx") { value: Float -> value.toDouble() }
                ).forEach { (field, exportKey, convert) ->
                    if (!feature.contains(field)) return@forEach
                    val timestamp = feature.getLong("${field}_timestamp", 0L)
                    val source = feature.getString("${field}_timestamp_source", null)
                    val state = FeatureSnapshotExportPolicy.freshnessState(timestamp, source, now)
                    put("${field}_state", state)
                    put("${field}_timestamp_epoch_ms", timestamp.takeIf { it > 0L } ?: JSONObject.NULL)
                    put("${field}_timestamp_source", source ?: "timestamp_source_unreported")
                    put("${field}_age_ms", if (timestamp > 0L) now - timestamp else JSONObject.NULL)
                    put(exportKey, if (state == "fresh") convert(feature.getFloat(field, 0f)) else JSONObject.NULL)
                }
            })
        }
        val sensors = JSONArray()
        WatchSensorCatalog.items.forEach { item ->
            val waveform = SensorSuiteCsvParser.parse(prefs.getString("${item.kind}_waveform", "").orEmpty())
            val sourceWaveform = SensorSuiteCsvParser.parse(prefs.getString("${item.kind}_source_waveform", "").orEmpty())
            val latest = SensorSuiteCsvParser.parse(prefs.getString("${item.kind}_latest", "").orEmpty(), 512)
            val measuredAt = prefs.getLong("${item.kind}_time", 0L)
            val measurementSession = prefs.getLong("${item.kind}_session_started_at_ms", 0L)
            val watchStatusSession = prefs.getLong("${item.kind}_watch_status_session_started_at_ms", 0L)
            val watchStatusCurrent = SensorSuiteProvenancePolicy.belongsToSession(measurementSession, watchStatusSession)
            val sampleCount = prefs.getInt("${item.kind}_samples", 0)
            val countContractVersion = prefs.getInt("${item.kind}_count_contract_version", 0)
            val rawValueCount = prefs.getInt("${item.kind}_raw_value_count", -1).takeIf { it >= 0 }
            val exportedSampleCount = RawSensorPayloadPolicy.exportSampleCount(
                RawSensorPayloadPolicy.isBiomedicalKind(item.kind),
                countContractVersion,
                rawValueCount,
                sampleCount
            )
            val presenceCount = RawSensorPayloadPolicy.displayPresenceCount(
                prefs.getInt("${item.kind}_count_contract_version", 0),
                prefs.getInt("${item.kind}_raw_value_count", -1).takeIf { it >= 0 },
                prefs.getInt("${item.kind}_event_count", 0),
                prefs.getInt("${item.kind}_preview_point_count", 0),
                prefs.getInt("${item.kind}_samples", 0)
            )
            val exportedAt = System.currentTimeMillis()
            val freshnessState = SensorDisplayState.from(
                samples = presenceCount,
                status = prefs.getString("${item.kind}_status", "not_measured").orEmpty(),
                measuredAt = measuredAt,
                now = exportedAt,
                freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
                kind = item.kind,
                timestampSource = prefs.getString("${item.kind}_timestamp_source", null)
            )
            val sampleRateEvidence = SensorSuiteExportPolicy.sampleRateExport(
                prefs.getFloat("${item.kind}_sample_rate", 0f).toDouble(),
                prefs.getFloat("${item.kind}_hal_sample_period_s", Float.NaN).toDouble()
                    .takeIf { prefs.contains("${item.kind}_hal_sample_period_s") },
                prefs.getString("${item.kind}_reporting_mode", "").orEmpty()
            )
            sensors.put(JSONObject().apply {
                put("kind", item.kind)
                put("label", prefs.getString("${item.kind}_label", item.label))
                put("group", item.group)
                put("sensor_name", prefs.getString("${item.kind}_sensor", ""))
                put("sensor_candidates_count", prefs.getInt("${item.kind}_candidate_count", 0))
                put("sensor_candidates", prefs.getString("${item.kind}_candidate_summary", ""))
                put("vendor", prefs.getString("${item.kind}_vendor", ""))
                put("reporting_mode", prefs.getString("${item.kind}_reporting_mode", ""))
                put("status", prefs.getString("${item.kind}_status", "not_measured"))
                put("reason", prefs.getString("${item.kind}_reason", ""))
                put("timestamp_epoch_ms", measuredAt)
                put("age_ms", if (measuredAt > 0L) exportedAt - measuredAt else JSONObject.NULL)
                put("is_stale", freshnessState == SensorDisplayState.STALE)
                put("sample_rate_hz", sampleRateEvidence.sampleRateHz ?: JSONObject.NULL)
                put("sample_rate_source", sampleRateEvidence.source)
                put("sample_count", exportedSampleCount ?: JSONObject.NULL)
                put("reported_sample_count", sampleCount)
                put("raw_value_count", rawValueCount ?: JSONObject.NULL)
                put("preview_point_count", prefs.getInt("${item.kind}_preview_point_count", 0))
                put("source_preview_point_count", prefs.getInt("${item.kind}_source_preview_point_count", 0))
                put("count_contract_version", countContractVersion)
                put("event_count", prefs.getInt("${item.kind}_event_count", 0))
                put("event_count_is_known", prefs.getInt("${item.kind}_event_count_known", 0) == 1)
                put("watch_event_count", prefs.getInt("${item.kind}_watch_event_count", 0))
                put("measurement_state", freshnessState.exportValue)
                put("channels", prefs.getInt("${item.kind}_channels", 0))
                put("accuracy", prefs.getInt("${item.kind}_accuracy", 0))
                listOf("minimum", "maximum", "average").forEach { statistic ->
                    val key = "${item.kind}_$statistic"
                    val reading = prefs.getFloat(key, Float.NaN).toDouble()
                    val rawCount = prefs.getInt("${item.kind}_raw_value_count", -1).takeIf { it >= 0 }
                    val hasSamples = RawSensorPayloadPolicy.hasStatisticSampleCount(
                        RawSensorPayloadPolicy.isBiomedicalKind(item.kind), rawCount, sampleCount
                    )
                    put(statistic, if (hasSamples && reading.isFinite()) reading else JSONObject.NULL)
                }
                put("watch_measurement", JSONObject().apply {
                    if (watchStatusCurrent) {
                        put("status", prefs.getString("${item.kind}_watch_status", "unknown"))
                        put("reason", prefs.getString("${item.kind}_watch_reason", ""))
                        put("session_started_at_epoch_ms", watchStatusSession)
                        put("suite_wear_gate_qualified", if (prefs.contains("${item.kind}_wear_qualified")) {
                            prefs.getBoolean("${item.kind}_wear_qualified", false)
                        } else JSONObject.NULL)
                        put("sensor_on_body", if (prefs.contains("${item.kind}_worn")) {
                            prefs.getBoolean("${item.kind}_worn", false)
                        } else JSONObject.NULL)
                    } else {
                        put("status", JSONObject.NULL)
                        put("reason", JSONObject.NULL)
                        put("session_started_at_epoch_ms", JSONObject.NULL)
                        put("suite_wear_gate_qualified", JSONObject.NULL)
                        put("sensor_on_body", JSONObject.NULL)
                    }
                })
                put("summary", prefs.getString("${item.kind}_summary", "not measured"))
                put("data_provenance", when (prefs.getString("${item.kind}_status", "not_measured")) {
                    "has_data", "worn", "off_wrist" -> "hardware_event"
                    "derived_estimate" -> "derived_estimate"
                    else -> "not_available"
                })
                put("latest", JSONArray(latest))
                put("waveform_unpacked", JSONArray(waveform))
                put("waveform_encoding", prefs.getString("${item.kind}_waveform_encoding", "legacy_unverified"))
                put("source_waveform_preview", JSONArray(sourceWaveform.take(32)))
                put("source_waveform_points", sourceWaveform.size)
                put("source_waveform_encoding", prefs.getString("${item.kind}_source_waveform_encoding", "not_preserved"))
            })
        }
        root.put("sensors", sensors)
        val directory = File(cacheDir, "exports").apply { mkdirs() }
        val now = System.currentTimeMillis()
        EngineeringExportRetention.deletePartialFiles(directory, ".$RAW_EXPORT_PREFIX")
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(RAW_EXPORT_PREFIX) && RawDiagnosticExportRetention.isExpired(it.lastModified(), now) }
            ?.forEach { it.delete() }
        val file = File(directory, "$RAW_EXPORT_PREFIX$now.json")
        EngineeringExportRetention.writeAtomically(file, root.toString(2))
        EngineeringExportRetention.scheduleDelete(this, file, RawDiagnosticExportRetention.MAX_AGE_MILLIS)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Xiaomi Watch 2 Pro 原始感測資料")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "匯出原始感測資料"))
    }

    private companion object {
        const val RAW_EXPORT_PREFIX = "watch-raw-sensors-"
    }

    private suspend fun connectedNodes() = suspendCancellableCoroutine<List<com.google.android.gms.wearable.Node>> { continuation ->
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            .addOnFailureListener { if (continuation.isActive) continuation.resume(emptyList()) }
    }
}

internal sealed interface HealthCenterMetricRead {
    data class Success(val count: Int) : HealthCenterMetricRead
    data object Empty : HealthCenterMetricRead
    data object PermissionDenied : HealthCenterMetricRead
    data class PermissionQueryFailed(val detail: String) : HealthCenterMetricRead
    data class ReadFailed(val detail: String) : HealthCenterMetricRead
}

internal object HealthCenterReadStatePolicy {
    suspend fun read(
        label: String,
        timeoutMillis: Long = 10_000L,
        query: suspend () -> Int
    ): HealthCenterMetricRead = try {
        val count = kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) { query() }
        if (count == null) HealthCenterMetricRead.ReadFailed("讀取逾時；可重新檢查，其他項目繼續讀取")
        else fromCount(count)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        readFailure(label, error)
    }

    fun permissionFailure(
        permissions: Set<String>,
        granted: Set<String>?,
        queryFailure: Throwable? = null
    ): HealthCenterMetricRead? {
        val states = permissions.map { HealthConnectPermissionPolicy.state(it, granted) }
        return when {
            states.any { it == HealthConnectPermissionPolicy.State.QUERY_FAILED } -> HealthCenterMetricRead.PermissionQueryFailed(
                queryFailure?.let { HealthConnectReadFailurePolicy.describe("Health Connect 權限狀態", it) }
                    ?: "Health Connect 權限狀態查詢失敗"
            )
            states.any { it == HealthConnectPermissionPolicy.State.DENIED } -> HealthCenterMetricRead.PermissionDenied
            else -> null
        }
    }

    fun fromCount(count: Int): HealthCenterMetricRead =
        if (count < 0) HealthCenterMetricRead.ReadFailed("Health Connect 讀取失敗")
        else if (count == 0) HealthCenterMetricRead.Empty
        else HealthCenterMetricRead.Success(count)

    fun readFailure(label: String, error: Throwable): HealthCenterMetricRead.ReadFailed =
        HealthCenterMetricRead.ReadFailed(HealthConnectReadFailurePolicy.describe(label, error))
}
