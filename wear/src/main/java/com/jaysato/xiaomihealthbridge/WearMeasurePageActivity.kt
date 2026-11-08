package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.HealthAlgorithmEngine as ClinicalSensorFormulas

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import java.time.Instant
import kotlin.math.abs

/**
 * Dedicated measure page for PPG / BIOZ / temperature / SpO₂ raw / suite / heart rate.
 * Completes when usable data arrives, with a bounded raw-sensor session.
 */
class WearMeasurePageActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var probe: RawVitalsProbe? = null
    private var measureCallback: MeasureCallback? = null
    private val measureClient by lazy { HealthServices.getClient(this).measureClient }
    private var whsBpm: Double? = null
    private var whsBpmTimestamp = 0L
    private lateinit var chart: WearWaveformView
    private lateinit var headline: TextView
    private lateinit var resultValue: TextView
    private lateinit var detail: TextView
    private lateinit var startButton: Button
    private lateinit var doneButton: Button
    private var active = false
    private lateinit var mode: Mode
    private var permissionSettingsOpened = false
    private var suiteStatusPrefs: SharedPreferences? = null
    private var suitePageVisible = false
    private val suiteStatusRefresh = Runnable {
        if (suitePageVisible && mode == Mode.SUITE) renderSuiteStatus()
    }
    private val suiteStatusListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (mode == Mode.SUITE && key in setOf(
                "message", "run_state", "timestamp", "suite_session_ended_at_ms", "success"
            )) {
            handler.removeCallbacks(suiteStatusRefresh)
            handler.postDelayed(suiteStatusRefresh, 80L)
        }
    }
    private val heartRatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && mode == Mode.HEART_RATE) {
            startWhsThenFallback()
        } else if (mode == Mode.HEART_RATE) {
            showHeartRatePermissionRequired()
        }
    }
    private val suitePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val mayStartHealthService = WearMeasurementPermissionPolicy.canRunSensorSuite(Build.VERSION.SDK_INT) { permission ->
            grants[permission] == true || checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (mayStartHealthService) {
            startMeasure()
        } else {
            headline.text = "需要感測服務啟動權限"
            detail.text = "Android 尚未授予健康前景服務所需權限；掃描未啟動，也沒有新資料。"
            startButton.isEnabled = true
            startButton.text = "授權後重試"
        }
    }

    enum class Mode(
        val title: String,
        val subtitle: String,
        val accent: String,
        val kind: RawVitalsProbe.Kind?,
        val serviceAction: String?,
        val minSamples: Int,
        val tip: String
    ) {
        PPG(
            "脈搏", "脈搏量測", "#FF6478",
            RawVitalsProbe.Kind.PPG, WearControlService.ACTION_MEASURE_PPG, 1024,
            "戴好手錶，保持手腕靜止。量測完成後查看結果。"
        ),
        BIOZ(
            "體成分", "體成分量測", "#8B7CFF",
            RawVitalsProbe.Kind.BIOZ, WearControlService.ACTION_MEASURE_BIOZ, 40,
            "戴好手錶並依畫面指示接觸按鍵。"
        ),
        TEMPERATURE(
            "腕部溫度", "腕部溫度", "#FFB347",
            RawVitalsProbe.Kind.BODY_TEMPERATURE, WearControlService.ACTION_MEASURE_TEMPERATURE, 1,
            "佩戴至少 10 分鐘，保持手腕乾燥後量測。"
        ),
        SPO2(
            "SpO₂", "血氧量測", "#56AEFF",
            RawVitalsProbe.Kind.SPO2, null, 8,
            "戴好手錶並保持靜止，完成後查看血氧結果。"
        ),
        SUITE(
            "感測器檢查", "全感測器掃描", "#56AEFF",
            null, WearControlService.ACTION_SENSOR_SUITE, 0,
            "會依序掃描公開通道並同步手機。掃描期間請佩戴並適度活動手腕。"
        ),
        HEART_RATE(
            "心率", "心率量測", "#FF5F73",
            RawVitalsProbe.Kind.HEART_RATE, null, 3,
            "戴好手錶並保持靜止，完成後查看心率結果。"
        ),
        RESPIRATION(
            "呼吸", "呼吸量測", "#7EC8E3",
            RawVitalsProbe.Kind.BIOZ_RESPIRATION, WearControlService.ACTION_MEASURE_BIOZ, 40,
            "戴好手錶並保持靜止，完成後查看結果。"
        ),
        OFF_BODY(
            "佩戴狀態", "離腕／佩戴偵測", "#C4A35A",
            RawVitalsProbe.Kind.OFF_BODY, null, 1,
            "確認手錶是否貼合手腕。"
        ),
        PRESSURE(
            "氣壓", "氣壓／標準高度估算", "#6EB5FF",
            RawVitalsProbe.Kind.PRESSURE, null, 3,
            "量測周圍氣壓；高度是依氣壓估算，並非實際海拔。"
        ),
        LIGHT(
            "環境光", "環境光", "#E8D48A",
            RawVitalsProbe.Kind.LIGHT, null, 2,
            "量測周圍光線亮度。"
        ),
        ACCEL(
            "活動感測", "加速度／活動", "#9ADBC5",
            RawVitalsProbe.Kind.ACCELEROMETER, null, 20,
            "移動手腕以檢查活動感測器。"
        ),
        GYRO(
            "轉動感測", "陀螺儀", "#9ADBC5",
            RawVitalsProbe.Kind.GYROSCOPE, null, 20,
            "輕輕轉動手腕以檢查轉動感測器。"
        ),
        MAGNET(
            "方向感測", "磁力計校準", "#64D2FF",
            RawVitalsProbe.Kind.MAGNETOMETER, null, 20,
            "先讓手錶靜止，再用手腕緩慢畫八字。"
        ),
        STEPS(
            "步數", "計步通道", "#56AEFF",
            RawVitalsProbe.Kind.STEP_COUNTER, null, 1,
            "走動幾步，檢查手錶是否記錄步數。"
        ),
        BOARD_TEMP(
            "手錶內部溫度", "主板溫度", "#FF9F6B",
            RawVitalsProbe.Kind.BOARD_TEMPERATURE, null, 1,
            "檢查手錶內部溫度感測器；這不是體溫。"
        ),
        LIFE_Q_OTT(
            "光學訊號", "光學訊號", "#B48BFF",
            RawVitalsProbe.Kind.LIFE_Q_OTT, null, 32,
            "戴好手錶並保持靜止，檢查光學訊號。"
        )
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        mode = runCatching {
            Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.PPG.name)
        }.getOrDefault(Mode.PPG)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = mode.title; textSize = 10f; letterSpacing = .14f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor(mode.accent))
            })
            addView(TextView(context).apply {
                text = mode.subtitle; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setPadding(0, dp(5), 0, dp(3))
            })
            addView(TextView(context).apply {
                text = if (mode == Mode.SUITE) "喚醒並解鎖手錶後手動開始" else "專屬量測頁 · 有數據才結束"
                textSize = 11f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(150, 158, 178)); setPadding(0, 0, 0, dp(12))
            })
            addView(TextView(context).apply {
                text = "尚未開始"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            }.also { headline = it })
            addView(TextView(context).apply {
                text = ""; textSize = 40f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                visibility = View.GONE
            }.also { resultValue = it })
            addView(TextView(context).apply {
                text = if (mode == Mode.SUITE) "掃描結果會同步到手機；充電時會略過腕部量測。" else mode.tip
                textSize = 12f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(172, 182, 203)); setPadding(0, dp(6), 0, dp(12))
            }.also { detail = it })
            addView(Button(context).apply {
                text = if (mode == Mode.SUITE) "開始全掃描" else "開始量測"
                isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
                background = pill(mode.accent, "#1A1A1E")
                setOnClickListener { startMeasure() }
            }.also { startButton = it }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
            addView(Button(context).apply {
                text = "完成"; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
                background = pill("#2C2C2E", "#1C1C1E")
                visibility = View.GONE
                setOnClickListener { finish() }
            }.also { doneButton = it }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
            addView(WearWaveformView(context, Color.parseColor(mode.accent)).also { chart = it },
                LinearLayout.LayoutParams(-1, dp(120)))
            if (mode.kind?.needsOemMode == true || mode == Mode.BIOZ || mode == Mode.SPO2) {
                addView(Button(context).apply {
                    text = "開啟原廠相關量測頁"; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
                    background = pill("#2A3857", "#182238")
                    setOnClickListener {
                        val kind = mode.kind ?: RawVitalsProbe.Kind.SPO2
                        headline.text = OemChipEnabler.enable(this@WearMeasurePageActivity, kind)
                            ?: "無法開啟原廠頁"
                    }
                }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
            }
            addView(TextView(context).apply {
                text = if (mode == Mode.PPG) {
                    "本次 PPG 研究判讀可能不準確，不能診斷或排除疾病；身體不適請就醫，以醫師評估為準。"
                } else "本程式提供參考，非醫療用途。"
                textSize = 11f; setTextColor(Color.rgb(255, 190, 120)); setPadding(0, dp(14), 0, 0)
            })
        }
        if (mode == Mode.SUITE) {
            content.removeView(startButton)
            WearUi.installScrollableWithPinnedAction(this, content, startButton)
            suiteStatusPrefs = getSharedPreferences(WearControlService.SENSOR_SUITE_STATUS_PREFS, MODE_PRIVATE)
                .also { it.registerOnSharedPreferenceChangeListener(suiteStatusListener) }
            content.post { renderSuiteStatus() }
        } else {
            WearUi.installScrollable(this, content)
        }
        if (intent.getBooleanExtra(EXTRA_AUTO_START, false)) {
            content.post { startMeasure() }
        }
    }

    override fun onStop() {
        super.onStop()
        suitePageVisible = false
        handler.removeCallbacks(suiteStatusRefresh)
        suiteStatusPrefs?.unregisterOnSharedPreferenceChangeListener(suiteStatusListener)
        // Screen-off / ADB overlays call onStop; keep until-data measure running.
        if (isFinishing) stopMeasure(publish = true, stopReason = "user_left_measurement")
    }

    override fun onDestroy() {
        suiteStatusPrefs?.unregisterOnSharedPreferenceChangeListener(suiteStatusListener)
        stopMeasure(publish = true, stopReason = "measurement_activity_destroyed")
        super.onDestroy()
    }

    private fun startMeasure() {
        stopMeasure(publish = false)
        if (mode == Mode.SUITE &&
            !WearMeasurementPermissionPolicy.canRunSensorSuite(Build.VERSION.SDK_INT) { permission ->
                checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        ) {
            headline.text = "需要感測服務啟動權限"
            detail.text = "需授權心率或活動辨識等 Android 健康前景服務權限；未授權前不啟動。"
            startButton.isEnabled = false
            startButton.text = "等待權限選擇"
            suitePermissionLauncher.launch(WearMeasurementPermissionPolicy.healthForegroundRuntimePermissions(Build.VERSION.SDK_INT).toTypedArray())
            return
        }
        val isCharging = getSystemService(BatteryManager::class.java)?.isCharging == true
        if (WearMeasurementPolicy.shouldBlockChargingMeasurement(isCharging, mode == Mode.SUITE) &&
            mode in WRIST_CONTACT_MODES
        ) {
            active = false
            headline.text = "請先移除充電器"
            detail.text = "手錶目前正在充電，腕部光學／阻抗／溫度讀值可能無效。拔除充電器、戴緊手錶後再按一次；目前不會啟動耗電掃描。"
            startButton.text = "拔除充電器後重試"
            return
        }
        if (mode == Mode.SUITE) {
            suiteStatusPrefs = getSharedPreferences(WearControlService.SENSOR_SUITE_STATUS_PREFS, MODE_PRIVATE)
                .also { it.registerOnSharedPreferenceChangeListener(suiteStatusListener) }
            headline.text = "正在啟動掃描…"
            detail.text = if (isCharging) {
                "充電期間僅執行短時、低耗電的非接觸感測器盤點；腕部生理量測會標記為充電略過。"
            } else {
                "請保持佩戴並依提示活動。結果會顯示在此頁與手機健康中心。"
            }
            startButton.isEnabled = false
            runCatching {
                startForegroundService(
                    WearControlCommandAuth.createIntent(this, WearControlService.ACTION_SENSOR_SUITE)
                )
            }.onSuccess {
                startButton.text = "掃描進行中"
            }.onFailure {
                headline.text = "掃描啟動失敗"
                detail.text = "請確認權限、電量與手錶狀態後重試。"
                startButton.text = "重試掃描"
                startButton.isEnabled = true
            }
            return
        }
        if (mode == Mode.HEART_RATE) {
            val permission = WearMeasurementPermissionPolicy.heartRatePermission(Build.VERSION.SDK_INT)
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                active = false
                headline.text = "需要心率權限"
                detail.text = "允許「讀取心率」後才能啟動 Health Services 與心率感測器；不授權時不會開始採集。"
                startButton.text = "授權並量測"
                doneButton.visibility = View.VISIBLE
                heartRatePermissionLauncher.launch(permission)
                return
            }
            startWhsThenFallback()
            return
        }
        startProbeCascade(probeKindsFor(mode))
        if (mode == Mode.PPG && active) startSupplementalWhsHeartRate()
    }

    private fun renderSuiteStatus() {
        handler.removeCallbacks(suiteStatusRefresh)
        val prefs = suiteStatusPrefs ?: return
        val message = prefs.getString("message", null).orEmpty()
        val timestamp = prefs.getLong("timestamp", 0L)
        if (message.isBlank() || !WearMeasurementPolicy.hasRecentSensorSuiteStatus(
                timestamp, System.currentTimeMillis(), SUITE_STATUS_FRESHNESS_MILLIS
            )) {
            headline.text = "尚無近期掃描"
            detail.text = "開始掃描以取得最新感測結果。"
            startButton.text = "開始全掃描"
            startButton.isEnabled = true
            doneButton.visibility = View.GONE
            return
        }
            val runState = prefs.getString("run_state", "unknown").orEmpty()
            headline.text = WearMeasurementPolicy.sensorSuiteStatusHeadline(runState)
            val summary = WearMeasurementPolicy.compactSensorSuiteStatus(message)
            val reportedAt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
            detail.text = "$summary\n更新時間 $reportedAt"
        val running = WearMeasurementPolicy.hasLiveSuiteWindow(
            runState, prefs.getLong("suite_session_started_at_ms", 0L),
            System.currentTimeMillis(), WearSensorSuiteTiming.WAKELOCK_TIMEOUT_MILLIS
        )
        if (runState == "running" && !running) {
            headline.text = "掃描狀態待確認"
            detail.text = "上次掃描沒有確認完成；可重新開始。"
        }
        startButton.isEnabled = !running
        startButton.text = if (running) "收集資料中…" else "再次掃描"
        doneButton.visibility = if (running) View.GONE else View.VISIBLE
        if (running && suitePageVisible) handler.postDelayed(suiteStatusRefresh, 1_000L)
    }

    override fun onResume() {
        super.onResume()
        if (::mode.isInitialized && mode == Mode.SUITE) {
            suitePageVisible = true
                suiteStatusPrefs = getSharedPreferences(WearControlService.SENSOR_SUITE_STATUS_PREFS, MODE_PRIVATE)
                .also { it.registerOnSharedPreferenceChangeListener(suiteStatusListener) }
            handler.removeCallbacks(suiteStatusRefresh)
            handler.post(suiteStatusRefresh)
        }
        if (permissionSettingsOpened && ::startButton.isInitialized &&
            checkSelfPermission(WearMeasurementPermissionPolicy.heartRatePermission(Build.VERSION.SDK_INT)) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionSettingsOpened = false
            startButton.text = "重新量測"
            startButton.setOnClickListener { startMeasure() }
            detail.text = "心率權限已授權，按「重新量測」開始一次前景採集。"
        }
    }

    private fun showHeartRatePermissionRequired() {
        active = false
        headline.text = "心率量測未啟動"
        detail.text = "讀取心率權限尚未授權，目前沒有取得或保存新的心率資料。請到 App 權限設定允許讀取心率後返回。"
        startButton.text = "開啟權限設定"
        startButton.setOnClickListener {
            permissionSettingsOpened = true
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        }
        doneButton.visibility = View.VISIBLE
    }

    private fun startSupplementalWhsHeartRate() {
        whsBpm = null
        whsBpmTimestamp = 0L
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) = Unit

            override fun onDataReceived(data: DataPointContainer) {
                val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
                val point = data.getData(DataType.HEART_RATE_BPM).maxByOrNull { it.getTimeInstant(bootInstant) }
                val pointTime = point?.getTimeInstant(bootInstant)?.toEpochMilli() ?: return
                val bpm = WearMeasurementPolicy.validSupplementalBpm(
                    point?.value?.toDouble()
                ) ?: return
                runOnUiThread {
                    if (active && whsBpm == null) {
                        whsBpm = bpm
                        whsBpmTimestamp = pointTime
                        detail.text = "已取得心率 ${"%.0f".format(bpm)} bpm，請等待量測完成。"
                    }
                }
            }
        }
        measureCallback = callback
        runCatching { measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback) }
            .onFailure { if (measureCallback === callback) measureCallback = null }
    }

    /** Prefer Wear Health Services BPM (high success) before optical HAL fallbacks. */
    private fun startWhsThenFallback() {
        active = true
        whsBpm = null
        whsBpmTimestamp = 0L
        startButton.text = "量測中…"
        resultValue.visibility = View.GONE
        doneButton.visibility = View.GONE
        headline.text = "正在量測心率"
        detail.text = "請保持手錶貼合、手腕靜止。"
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                if (availability is DataTypeAvailability &&
                    availability != DataTypeAvailability.AVAILABLE
                ) {
                    runOnUiThread {
                        if (!active || whsBpm != null) return@runOnUiThread
                        detail.text = "正在改用手錶感測器量測…"
                    }
                }
            }

            override fun onDataReceived(data: DataPointContainer) {
                val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
                val point = data.getData(DataType.HEART_RATE_BPM).maxByOrNull { it.getTimeInstant(bootInstant) }
                val pointTime = point?.getTimeInstant(bootInstant)?.toEpochMilli() ?: return
                val bpm = point?.value?.toDouble()
                    ?.takeIf { it in 30.0..220.0 } ?: return
                runOnUiThread {
                    if (!active) return@runOnUiThread
                    whsBpm = bpm
                    whsBpmTimestamp = pointTime
                    stopMeasure(publish = true)
                }
            }
        }
        measureCallback = callback
        runCatching {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        }.onFailure {
            unregisterWhs()
            startProbeCascade(probeKindsFor(Mode.HEART_RATE))
            return
        }
        handler.postDelayed({
            if (active && whsBpm == null && probe == null) {
                unregisterWhs()
                detail.text = "正在改用手錶感測器量測…"
                startProbeCascade(probeKindsFor(Mode.HEART_RATE))
            }
        }, 12_000L)
        // Until-data only: no fixed-second success gate; SAFETY_MAX is hang prevention only.
        handler.postDelayed({ if (active) stopMeasure(publish = true, stopReason = "time_limit_reached") }, SAFETY_MAX_MILLIS)
    }

    private fun unregisterWhs() {
        measureCallback?.let {
            runCatching {
                measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, it)
            }
        }
        measureCallback = null
    }

    private fun startProbeCascade(kinds: List<RawVitalsProbe.Kind>) {
        var candidateTimeout: Runnable? = null
        fun tryKind(index: Int) {
            candidateTimeout?.let(handler::removeCallbacks)
            candidateTimeout = null
            if (index >= kinds.size) {
                stopMeasure(publish = false)
                headline.text = "通道無法啟動"
                detail.text = "這次沒有取得${mode.subtitle}結果；請確認佩戴後重新量測。"
                startButton.text = "重新量測"
                doneButton.visibility = View.VISIBLE
                return
            }
            val kind = kinds[index]
            val session = SensorRegistrationGuard.create(factory = { RawVitalsProbe(this, kind) { snapshot ->
                runOnUiThread {
                    if (!active) return@runOnUiThread
                    chart.setSamples(snapshot.waveform.takeLast(280).toList())
                    headline.text = "正在量測${mode.subtitle}"
                    val raw = snapshot.average ?: snapshot.latest.firstOrNull()
                    val calibrated = raw?.takeIf {
                        kind in setOf(
                            RawVitalsProbe.Kind.BODY_TEMPERATURE,
                            RawVitalsProbe.Kind.PRESSURE,
                            RawVitalsProbe.Kind.LIGHT
                        )
                    }?.let { WearCalibrationStore.apply(this@WearMeasurePageActivity, kind, it) }
                    detail.text = "請保持手錶貼合、手腕靜止。"
                    if (hasUsableData(snapshot)) {
                        stopMeasure(publish = true)
                    }
                }
            } }, onFailure = { error ->
                val reason = if (error is SecurityException) "permission_denied" else "probe_creation_exception"
                publishFailedCandidate(RawVitalsProbe.derived(
                    kind, emptyList(), "probe-creation", "HealthBridge", reason
                ).copy(status = reason, registrationResult = reason, acquisitionStopReason = reason, sampleRateHz = 0.0))
            })
            if (session == null) {
                tryKind(index + 1)
                return
            }
            if (!session.start()) {
                val failed = session.stop()
                publishFailedCandidate(failed)
                tryKind(index + 1)
                return
            }
            probe = session
            active = true
            headline.text = "正在量測${mode.subtitle}"
            resultValue.visibility = View.GONE
            startButton.text = "量測中…"
            doneButton.visibility = View.GONE
            handler.postDelayed({ if (active && probe === session) stopMeasure(publish = true, stopReason = "time_limit_reached") }, if (mode == Mode.PPG) EcgCapturePolicy.MAX_CAPTURE_MILLIS else SAFETY_MAX_MILLIS)
            val timeout = Runnable {
                if (!active || probe !== session) return@Runnable
                if (index + 1 < kinds.size) {
                    val candidateSnapshot = session.stop("candidate_timeout")
                    if (WearMeasurementPolicy.shouldPublishCandidateCapture(candidateSnapshot)) {
                        runCatching {
                            WearRawSensorPublisher.publish(
                                this,
                                candidateSnapshot,
                                final = true,
                                chipPart = WearRawSensorPublisher.chipFor(kind),
                                featurePage = "${mode.name}_CANDIDATE_TIMEOUT"
                            )
                        }
                    }
                    probe = null
                    tryKind(index + 1)
                } else {
                    stopMeasure(publish = true, stopReason = "candidate_timeout")
                }
            }
            if (index + 1 < kinds.size || mode in setOf(Mode.HEART_RATE, Mode.PPG, Mode.LIFE_Q_OTT)) {
                candidateTimeout = timeout
                handler.postDelayed(timeout, if (mode == Mode.PPG) EcgCapturePolicy.MAX_CAPTURE_MILLIS else SENSOR_CANDIDATE_TIMEOUT_MILLIS)
            }
        }
        tryKind(0)
    }

    private fun publishFailedCandidate(snapshot: RawVitalsProbe.Snapshot) {
        runCatching {
            WearRawSensorPublisher.publish(
                this, snapshot, final = true,
                chipPart = WearRawSensorPublisher.chipFor(snapshot.kind),
                featurePage = "${mode.name}_FAILED"
            )
        }.onFailure { error ->
            android.util.Log.w("WearMeasurePage", "Failed candidate publication failed", error)
        }
    }
    private fun probeKindsFor(mode: Mode): List<RawVitalsProbe.Kind> {
        val primary = mode.kind ?: return emptyList()
        return when (mode) {
            Mode.PPG -> listOf(primary, RawVitalsProbe.Kind.MAX_HEART_RATE).distinct()
            Mode.LIFE_Q_OTT -> listOf(primary)
            Mode.HEART_RATE -> listOf(
                RawVitalsProbe.Kind.HEART_RATE,
                RawVitalsProbe.Kind.MAX_HEART_RATE,
                RawVitalsProbe.Kind.PPG
            )
            Mode.SPO2 -> listOf(primary, RawVitalsProbe.Kind.PPG, RawVitalsProbe.Kind.MAX_HEART_RATE)
            else -> listOf(primary)
        }
    }

    private fun hasUsableData(snapshot: RawVitalsProbe.Snapshot): Boolean {
        if (snapshot.samples < mode.minSamples) return false
        return when (mode) {
            Mode.PPG, Mode.LIFE_Q_OTT -> WearMeasurementPolicy.hasOpticalRawCapture(snapshot, mode.minSamples)
            Mode.SPO2 -> snapshot.worn == true && snapshot.samples >= mode.minSamples &&
                snapshot.latest.any { it.isFinite() && it != ClinicalSensorFormulas.OPTICAL_FILL.toDouble() }
            Mode.HEART_RATE -> WearMeasurementPolicy.directHeartRateBpm(snapshot) != null
            Mode.TEMPERATURE -> snapshot.worn == true &&
                (snapshot.average ?: snapshot.latest.firstOrNull())?.isFinite() == true
            Mode.BOARD_TEMP ->
                (snapshot.average ?: snapshot.latest.firstOrNull())?.let { it.isFinite() } == true
            Mode.OFF_BODY -> snapshot.worn != null || snapshot.samples >= 1
            Mode.PRESSURE -> (snapshot.average ?: snapshot.latest.firstOrNull())?.let { it in 800.0..1100.0 } == true
            Mode.LIGHT -> snapshot.latest.any { it.isFinite() }
            Mode.BIOZ, Mode.RESPIRATION -> WearMeasurementPolicy.hasWornBiozRawCapture(
                snapshot.worn, snapshot.samples, mode.minSamples
            )
            Mode.ACCEL, Mode.GYRO, Mode.MAGNET, Mode.STEPS -> snapshot.samples >= mode.minSamples
            Mode.SUITE -> false
        }
    }

    private fun stopMeasure(publish: Boolean, stopReason: String? = null) {
        handler.removeCallbacksAndMessages(null)
        unregisterWhs()
        val whs = whsBpm
        val whsTimestamp = whsBpmTimestamp
        whsBpm = null
        whsBpmTimestamp = 0L
        if (whs != null && probe == null) {
            active = false
            headline.text = if (whsTimestamp > 0L) "心率量測完成" else "心率已取得，請重測確認時間"
            resultValue.visibility = View.VISIBLE
            resultValue.text = "%.0f\nbpm".format(whs)
            resultValue.textSize = 36f
            detail.text = "本次心率 ${"%.0f".format(whs)} bpm"
            startButton.text = "再次量測"
            doneButton.visibility = View.VISIBLE
            if (publish && whsTimestamp > 0L) {
                val measuredAt = whsTimestamp
                WearHealthStore.recordHeartRate(this, whs.toLong(), measuredAt)
                val snap = RawVitalsProbe.derived(
                    kind = RawVitalsProbe.Kind.HEART_RATE,
                    values = listOf(whs),
                    sensorName = "Wear Health Services",
                    vendor = "Google",
                    reason = "Health Services on-demand BPM",
                    measurementTimestampEpochMs = measuredAt,
                    timestampSource = "health_services_datapoint_epoch"
                )
                WearRawSensorPublisher.publish(
                    this,
                    snap,
                    final = true,
                    chipPart = "WHS",
                    featurePage = mode.name,
                    formulas = WearRawSensorPublisher.FormulaExtras(
                        pulseBpm = whs,
                        note = "來源：Wear Health Services MeasureClient"
                    )
                )
                publishHeartRateBpm(whs, measuredAt)
            } else if (publish) {
                detail.text = "心率 ${"%.0f".format(whs)} bpm；這次未確認量測時間，請重測。"
            }
            return
        }
        val session = probe ?: run {
            active = false
            return
        }
        probe = null
        active = false
        val snapshot = session.stop(stopReason)
        chart.setSamples(snapshot.waveform.takeLast(280).toList())
        val optical = when (mode) {
            Mode.PPG, Mode.SPO2, Mode.HEART_RATE, Mode.LIFE_Q_OTT -> {
                val pairs = if (mode == Mode.SPO2 && snapshot.latest.size >= 2) {
                    val a = snapshot.latest[0]
                    val b = snapshot.latest[1]
                    if (a.isFinite() && b.isFinite()) listOf(a to b) else emptyList()
                } else emptyList()
                val dual = if (mode == Mode.SPO2 && snapshot.waveform.size >= 16) {
                    snapshot.waveform.toList().chunked(2).mapNotNull { chunk ->
                        if (chunk.size == 2 && chunk[0].isFinite() && chunk[1].isFinite() &&
                            chunk[0] != ClinicalSensorFormulas.OPTICAL_FILL &&
                            chunk[1] != ClinicalSensorFormulas.OPTICAL_FILL
                        ) chunk[0].toDouble() to chunk[1].toDouble() else null
                    }
                } else pairs
                ClinicalSensorFormulas.opticalFromWaveform(
                    snapshot.waveform.toList(),
                    snapshot.sampleRateHz,
                    dual
                )
            }
            else -> null
        }
        val hrv = optical?.let { ClinicalSensorFormulas.hrvFromRr(it.rrIntervalsMs) }
        val opticalCaptureUsable = mode in setOf(Mode.PPG, Mode.LIFE_Q_OTT) &&
            ChipCalibrationProfiles.assessOpticalWaveform(snapshot.waveform.toList()).valid
        val pressure = when (mode) {
            Mode.PRESSURE -> ClinicalSensorFormulas.pressureAltitude(
                snapshot.average ?: snapshot.latest.firstOrNull()
            )
            else -> null
        }
        val stressScore = if (WearMeasurementPolicy.canPublishResearchStressScore(
                snapshot.worn, opticalCaptureUsable, hrv?.rrCount ?: 0
            )) ClinicalSensorFormulas.researchStressScore(hrv?.nRmssd, hrv?.bpm ?: optical?.pulseRateBpm)
            else null
        val formulaLine = ClinicalSensorFormulas.summaryLine(optical, hrv, pressure, stressScore)
        val supplementalHeartRate = if (mode == Mode.PPG) whs else null
        val conclusion = when (mode) {
            Mode.HEART_RATE -> WearHealthConclusion.forHeartRateBpm(
                WearMeasurementPolicy.directHeartRateBpm(snapshot) ?: optical?.pulseRateBpm
            )
            Mode.PPG -> supplementalHeartRate?.let {
                "結論：已取得手錶心率 ${"%.0f".format(it)} bpm（Wear Health Services）；光學波形另作研究分析。"
            } ?: WearHealthConclusion.forPpg(snapshot.samples, optical?.pulseRateBpm ?: hrv?.bpm)
            Mode.LIFE_Q_OTT -> WearHealthConclusion.forPpg(snapshot.samples, optical?.pulseRateBpm ?: hrv?.bpm)
            Mode.TEMPERATURE -> when (snapshot.worn) {
                false -> "結論：離腕時取得的原始溫度值不作為量測完成證據"
                null -> "結論：佩戴狀態未確認；原始溫度值僅供研究"
                true -> WearHealthConclusion.forTemperatureRawValue(snapshot.average ?: snapshot.latest.firstOrNull())
            }
            Mode.BOARD_TEMP -> WearHealthConclusion.forTemperatureRawValue(snapshot.average ?: snapshot.latest.firstOrNull())
            Mode.PRESSURE ->
                WearHealthConclusion.forPressure(snapshot.average, pressure?.altitudeM)
            Mode.BIOZ, Mode.RESPIRATION ->
                WearHealthConclusion.forBioz(snapshot.samples, snapshot.worn)
            Mode.SPO2 -> when {
                snapshot.samples <= 0 -> "這次沒有量到血氧；請重新量測。"
                snapshot.worn == false -> "手錶沒有戴好；戴好後重新量測血氧。"
                snapshot.worn == null -> "請確認手錶貼合手腕，再量測血氧。"
                else -> "這次沒有可顯示的血氧百分比；請使用原廠量測頁重測。"
            }
            Mode.OFF_BODY ->
                if (snapshot.worn == true) "結論：已佩戴" else if (snapshot.worn == false) "結論：已離腕" else "結論：佩戴狀態未知"
            else -> if (snapshot.samples > 0) "結論：已取得原始資料" else "結論：無數值"
        }
        val primary = when (mode) {
            Mode.HEART_RATE ->
                (WearMeasurementPolicy.directHeartRateBpm(snapshot) ?: optical?.pulseRateBpm)
                    ?.takeIf { it in 30.0..220.0 }
                    ?.let { "%.0f".format(it) to "bpm" }
            Mode.PPG -> supplementalHeartRate?.let { "%.0f".format(it) to "bpm" }
                ?: (optical?.pulseRateBpm ?: hrv?.bpm)?.takeIf { it in 30.0..220.0 }
                    ?.let { "%.0f".format(it) to "脈搏估算 bpm" }
            Mode.LIFE_Q_OTT ->
                (optical?.pulseRateBpm ?: hrv?.bpm)?.takeIf { it in 30.0..220.0 }
                    ?.let { "%.0f".format(it) to "脈搏估算 bpm" }
            Mode.PRESSURE ->
                (snapshot.average ?: snapshot.latest.firstOrNull())?.takeIf { it.isFinite() }
                    ?.let { "%.0f".format(it) to "hPa" }
            Mode.OFF_BODY -> when (snapshot.worn) {
                true -> "已佩戴" to ""
                false -> "已離腕" to ""
                null -> null
            }
            else -> null
        }
        val hasCapturedSignal = snapshot.samples > 0 || snapshot.eventCount > 0
        headline.text = when {
            mode == Mode.PPG && supplementalHeartRate != null -> "已取得心率讀值"
            !hasCapturedSignal -> "這次沒有收到量測資料"
            mode == Mode.HEART_RATE && primary != null -> "心率已取得 · 已同步手機"
            mode == Mode.HEART_RATE -> "這次沒有量到心率"
            mode in setOf(Mode.PPG, Mode.LIFE_Q_OTT) && primary != null -> "已取得脈搏參考值"
            mode in setOf(Mode.PPG, Mode.LIFE_Q_OTT) && hasCapturedSignal -> "已收到脈搏訊號"
            mode in setOf(Mode.PPG, Mode.LIFE_Q_OTT) -> "沒有收到可用光學訊號"
            mode == Mode.TEMPERATURE && snapshot.worn == false -> "請戴好手錶後重測"
            mode == Mode.TEMPERATURE -> "已收到溫度訊號"
            mode == Mode.SPO2 && snapshot.worn == false -> "請戴好手錶後重測"
            mode == Mode.SPO2 -> "已收到血氧訊號"
            mode in setOf(Mode.BIOZ, Mode.RESPIRATION) && snapshot.worn == false -> "請戴好手錶後重測"
            mode in setOf(Mode.BIOZ, Mode.RESPIRATION) -> "已收到體成分訊號"
            else -> "這次沒有可用的結果"
        }
        if (primary != null) {
            resultValue.visibility = View.VISIBLE
            resultValue.text = if (primary.second.isBlank()) primary.first else "${primary.first}\n${primary.second}"
            resultValue.textSize = if (primary.second.isBlank()) 28f else 36f
        } else {
            resultValue.visibility = View.GONE
            resultValue.text = ""
        }
        val manufacturer = WearManufacturerSelfTest.evaluate(snapshot)?.compact()
        val stopDetail = when (snapshot.acquisitionStopReason) {
            "time_limit_reached" -> "逾時停止：達到量測時限；以上為部分原始資料，不代表分析完成"
            "candidate_timeout" -> "候選通道逾時：已保留目前原始資料"
            else -> null
        }
        detail.text = when {
            primary != null && mode == Mode.HEART_RATE -> WearHealthConclusion.forHeartRateBpm(
                WearMeasurementPolicy.directHeartRateBpm(snapshot) ?: optical?.pulseRateBpm
            )
            primary != null && mode == Mode.PPG -> "已取得心率讀值；可在手機查看趨勢。"
            snapshot.worn == false -> "請戴好手錶後重新量測。"
            hasCapturedSignal && mode == Mode.TEMPERATURE -> "尚無可用的體溫讀值；可在手機查看訊號。"
            hasCapturedSignal && mode == Mode.SPO2 -> "尚無可用的血氧百分比；可在手機查看訊號。"
            hasCapturedSignal && mode in setOf(Mode.BIOZ, Mode.RESPIRATION) -> "尚無可用的體成分讀值；可在手機查看訊號。"
            hasCapturedSignal && mode in setOf(Mode.PPG, Mode.LIFE_Q_OTT) -> "尚無可用的脈率讀值；可在手機查看訊號。"
            else -> "請保持手錶貼合、手腕靜止後重新量測。"
        }
        startButton.text = "再次量測"
        doneButton.visibility = View.VISIBLE
        if (publish && mode == Mode.PPG && supplementalHeartRate != null) {
            val measuredAt = whsTimestamp
            if (measuredAt > 0L) {
                WearHealthStore.recordHeartRate(this, supplementalHeartRate.toLong(), measuredAt)
                publishHeartRateBpm(supplementalHeartRate, measuredAt)
            } else {
                detail.append("\n這次未確認量測時間，請重測。")
            }
            WearRawSensorPublisher.publish(
                this,
                RawVitalsProbe.derived(
                    kind = RawVitalsProbe.Kind.HEART_RATE,
                    values = listOf(supplementalHeartRate),
                    sensorName = "Wear Health Services",
                    vendor = "Google",
                    reason = "獨立心率讀值；與 PPG 原始封包分開",
                    measurementTimestampEpochMs = measuredAt,
                    timestampSource = "health_services_datapoint_epoch"
                ),
                final = true,
                chipPart = "WHS",
                featurePage = "PPG_SUPPLEMENTAL_HEART_RATE",
                formulas = WearRawSensorPublisher.FormulaExtras(
                    pulseBpm = supplementalHeartRate,
                    note = "來源：Wear Health Services；不是 PPG 波形推算"
                )
            )
        }
        if (publish && snapshot.samples > 0 && mode != Mode.SUITE) {
            val trustedHeartRate = when (mode) {
                Mode.HEART_RATE -> WearMeasurementPolicy.directHeartRateBpm(snapshot)
                else -> null
            }
            trustedHeartRate?.toLong()?.takeIf { it in 30L..220L }
                ?.let { WearHealthStore.recordHeartRate(this, it) }
            WearRawSensorPublisher.publish(
                this,
                snapshot,
                final = true,
                chipPart = WearRawSensorPublisher.chipFor(snapshot.kind),
                featurePage = mode.name,
                formulas = WearRawSensorPublisher.FormulaExtras(
                    piPercent = optical?.perfusionIndexPercent,
                    relativeAcDcRatio = optical?.relativeAcDcRatio,
                    pulseBpm = optical?.pulseRateBpm,
                    ratioR = optical?.ratioOfRatiosR,
                    rmssdMs = hrv?.rmssdMs,
                    nRmssd = hrv?.nRmssd,
                    sdnnMs = hrv?.sdnnMs,
                    pnn50 = hrv?.pNn50,
                    cvrr = hrv?.cvRr,
                    rrCount = hrv?.rrCount,
                    altitudeM = pressure?.altitudeM,
                    pressureHpa = pressure?.pressureHpa,
                    stressScore = stressScore,
                    note = listOfNotNull(optical?.note, pressure?.note).joinToString(" | ").ifBlank { null },
                    citations = hrv?.citations?.joinToString(" | ")
                )
            )
        }
    }

    private fun publishHeartRateBpm(bpm: Double, timestamp: Long) {
        val payload = HealthServicesHeartRatePayload.create(bpm.toLong(), timestamp) ?: return
        val request = com.google.android.gms.wearable.PutDataMapRequest.create("/heart-rate/${payload.eventTimestampMillis}").apply {
            dataMap.putLong("bpm", payload.bpm)
            dataMap.putLong("timestamp", payload.eventTimestampMillis)
            dataMap.putString("timestamp_source", payload.timestampSource)
        }.asPutDataRequest().setUrgent()
        com.google.android.gms.wearable.Wearable.getDataClient(this).putDataItem(request)
            .addOnFailureListener { WearHealthStore.markSync(this, false, "心率同步等待重試") }
    }
    private fun pill(start: String, end: String) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(start), Color.parseColor(end))
    ).apply { cornerRadius = dp(8).toFloat() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private val WRIST_CONTACT_MODES = setOf(
            Mode.PPG, Mode.BIOZ, Mode.TEMPERATURE, Mode.SPO2, Mode.SUITE,
            Mode.HEART_RATE, Mode.RESPIRATION, Mode.OFF_BODY, Mode.LIFE_Q_OTT
        )
        const val EXTRA_MODE = "mode"
        const val EXTRA_AUTO_START = "auto_start"
        private const val SAFETY_MAX_MILLIS = 20_000L
        private const val SUITE_STATUS_FRESHNESS_MILLIS = 30 * 60_000L
        private const val SENSOR_CANDIDATE_TIMEOUT_MILLIS = 15_000L
        fun intent(activity: ComponentActivity, mode: Mode) =
            Intent(activity, WearMeasurePageActivity::class.java).putExtra(EXTRA_MODE, mode.name)
    }
}

class WearWaveformView(
    context: android.content.Context,
    private val stroke: Int = Color.rgb(72, 238, 171)
) : View(context) {
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(37, 52, 62); strokeWidth = 1f }
    private val trace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = stroke; strokeWidth = 2.4f; style = Paint.Style.STROKE
    }
    private var samples = emptyList<Float>()
    fun setSamples(next: List<Float>) { samples = next; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(10, 18, 20))
        repeat(5) { i -> canvas.drawLine(0f, height * i / 4f, width.toFloat(), height * i / 4f, grid) }
        if (samples.size < 2) return
        val lower = samples.minOrNull() ?: return
        val upper = samples.maxOrNull() ?: return
        val span = (upper - lower).takeIf { abs(it) > 1e-6f } ?: return
        val path = android.graphics.Path()
        samples.forEachIndexed { index, value ->
            val x = width * index / (samples.size - 1f)
            val y = height - ((value - lower) / span) * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, trace)
    }
}
