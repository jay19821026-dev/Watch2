package com.jaysato.xiaomihealthbridge

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseEvent
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import java.text.DateFormat
import java.io.File

class WearMainActivity : ComponentActivity() {
    private val measureClient by lazy { HealthServices.getClient(this).measureClient }
    private lateinit var status: TextView
    private lateinit var action: Button
    private lateinit var capabilities: TextView
    private lateinit var clearQuarantineAction: Button
    private lateinit var thresholdStatus: TextView
    private lateinit var exerciseAction: Button
    private lateinit var exercisePauseAction: Button
    private lateinit var installAction: Button
    private lateinit var oxygenProbeAction: Button
    private lateinit var backgroundSyncAction: Button
    private lateinit var backgroundSyncIntervalAction: Button
    private lateinit var deviceStatus: TextView
    private lateinit var syncStatus: TextView
    private lateinit var historyStatus: TextView
    private var oxygenProbe: RawOxygenProbe? = null
    private var measuring = false
    private var measureTypes = emptySet<androidx.health.services.client.data.DataType<*, *>>()
    private var passiveTypes = emptySet<androidx.health.services.client.data.DataType<*, *>>()
    private var exerciseTypes = emptySet<androidx.health.services.client.data.DataType<*, *>>()
    private var exerciseCount = 0
    private var walkingTypes = emptySet<androidx.health.services.client.data.DataType<*, *>>()
    private var exerciseActive = false
    private var exerciseStartMillis = 0L
    private var exercisePaused = false
    private var pauseStartedMillis = 0L
    private var totalPausedMillis = 0L
    private val exerciseHeartRates = mutableListOf<Long>()
    private val routePoints = mutableListOf<RoutePoint>()
    private var passiveRegistered = false
    private var passiveRegistrationInFlight = false
    private var lastAlertMillis = 0L
    private var lastHeartRateSentMillis = 0L
    private var gpsFixReceived = false
    private var advancedSensorController: AdvancedSensorController? = null
    private lateinit var ambientLightController: AmbientLightController
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gpsTimeoutWarning = Runnable {
        if (exerciseActive && !gpsFixReceived) {
            status.text = "GPS 尚未定位\n請移到開闊處"
        }
    }
    private val oxygenSensors by lazy { findOxygenSensors() }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (corePermissionsGranted()) {
            capabilities.append("\n授權完成；請再次點擊剛才的運動或背景同步操作。")
        }
    }
    private val heartRatePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestBackgroundMonitoring()
        else if (::capabilities.isInitialized) capabilities.append("\n心率權限未授予，背景心率同步保持關閉。")
    }
    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            PassiveMonitoringSettings.setEnabled(this, true)
            registerPassiveMonitoring()
        }
        else if (::capabilities.isInitialized) {
            PassiveMonitoringSettings.setEnabled(this, false)
            capabilities.append("\n背景心率同步未啟用；前景量測仍可使用，背景關閉較省電。")
        }
    }
    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            runOnUiThread { status.text = "感測器\n$availability" }
        }
        override fun onDataReceived(data: DataPointContainer) {
            val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { point ->
                val bpm = point.value.toLong()
                sendHeartRate(bpm, point.getTimeInstant(bootInstant).toEpochMilli())
                runOnUiThread {
                    val alert = when {
                        bpm >= highThreshold() -> "⚠ 高心率"
                        bpm <= lowThreshold() -> "⚠ 低心率"
                        else -> null
                    }
                    status.text = if (alert == null) "$bpm\nbpm" else "$bpm\nbpm\n$alert"
                    action.text = "同步中"
                    if (alert != null) vibrateAlert()
                }
            }
        }
    }
    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onRegistered() = Unit
        override fun onRegistrationFailed(throwable: Throwable) {
            runOnUiThread { capabilities.append("\n運動回呼失敗：${throwable.message}") }
        }
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
            update.latestMetrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { point ->
                val bpm = point.value.toLong()
                val timestamp = point.getTimeInstant(bootInstant).toEpochMilli()
                if (!exercisePaused) exerciseHeartRates += bpm
                sendHeartRate(bpm, timestamp)
                runOnUiThread { status.text = "$bpm bpm\n${heartZone(bpm)}" }
            }
            val state = update.exerciseStateInfo.state.toString()
            runOnUiThread {
                exercisePaused = state.contains("PAUSED")
                exercisePauseAction.text = if (exercisePaused) "▶  繼續" else "Ⅱ  暫停"
                exercisePauseAction.isEnabled = exerciseActive
            }
            update.latestMetrics.getData(DataType.LOCATION).forEach { point ->
                val location = point.value
                val time = point.getTimeInstant(bootInstant).toEpochMilli()
                if (routePoints.size < MAX_ROUTE_POINTS && time >= exerciseStartMillis) {
                    routePoints += RoutePoint(time, location.latitude, location.longitude, location.altitude)
                    if (!gpsFixReceived) {
                        gpsFixReceived = true
                        runOnUiThread { status.text = "GPS 已定位\n步行記錄中" }
                    }
                }
            }
        }
        override fun onLapSummaryReceived(summary: ExerciseLapSummary) = Unit
        override fun onAvailabilityChanged(
            dataType: androidx.health.services.client.data.DataType<*, *>,
            availability: Availability
        ) = Unit
        override fun onExerciseEventReceived(event: ExerciseEvent) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearSnapshotOutbox.recoverPending(this)
        WearPowerScheduler.migrateRoutineReminders(this)
        handleOemArmIntent(intent)
        status = TextView(this).apply {
            text = "—\nbpm"; textSize = 23f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            contentDescription = "心率，點擊開始量測"
            setOnClickListener {
                startActivity(WearMeasurePageActivity.intent(this@WearMainActivity, WearMeasurePageActivity.Mode.HEART_RATE))
            }
            background = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor("#391923"), Color.parseColor("#101012"))
                gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = dp(72).toFloat()
                setStroke(dp(5), Color.parseColor("#FF5F73")); shape = GradientDrawable.OVAL
            }
            elevation = dp(7).toFloat()
        }
        action = Button(this).apply {
            text = "♥  開始心率量測"; textSize = 14f; isAllCaps = false; setTextColor(Color.WHITE)
            background = buttonBackground("#FF6478", "#D83C58")
            setOnClickListener {
                startActivity(WearMeasurePageActivity.intent(this@WearMainActivity, WearMeasurePageActivity.Mode.HEART_RATE))
            }
        }
        exerciseAction = Button(this).apply {
            text = "▶  步行運動"; textSize = 14f; isAllCaps = false; setTextColor(Color.WHITE)
            background = buttonBackground("#236A82", "#174759")
            setOnClickListener {
                startActivity(Intent(this@WearMainActivity, WearWorkoutActivity::class.java))
            }
        }
        exercisePauseAction = Button(this).apply {
            text = "Ⅱ  暫停"; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE); isEnabled = false
            background = buttonBackground("#49494F", "#2B2B30")
            setOnClickListener { toggleExercisePause() }
        }
        installAction = Button(this).apply {
            text = "尚未收到 APK"; textSize = 13f; setTextColor(Color.WHITE); isEnabled = false
            background = GradientDrawable().apply { setColor(Color.parseColor("#526078")); cornerRadius = dp(18).toFloat() }
            setOnClickListener { installReceivedApk() }
        }
        oxygenProbeAction = Button(this).apply {
            text = "◉  SpO₂ 原始"; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
            background = buttonBackground("#78496E", "#4D304A")
            setOnClickListener {
                startActivity(WearMeasurePageActivity.intent(this@WearMainActivity, WearMeasurePageActivity.Mode.SPO2))
            }
        }
        thresholdStatus = TextView(this).apply {
            textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#C8D0E0"))
        }
        deviceStatus = infoCard("裝置狀態讀取中…")
        syncStatus = infoCard("同步狀態讀取中…")
        historyStatus = infoCard("歷史資料讀取中…")
        capabilities = TextView(this).apply {
            text = "正在檢查健康功能…"; textSize = 12f; setTextColor(Color.parseColor("#C8D0E0"))
            setPadding(dp(14), dp(14), dp(14), dp(18))
            background = buttonBackground("#17213A", "#0F172A")
        }
        clearQuarantineAction = Button(this).apply {
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = buttonBackground("#6B3434", "#402020")
            setOnClickListener { confirmClearQuarantine() }
            visibility = View.GONE
        }
        val updateAction = Button(this).apply {
            text = "↻  系統更新"; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
            background = buttonBackground("#343438", "#232326")
            setOnClickListener { openSystemUpdate() }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(18), dp(24), dp(22))
            addView(TextView(context).apply {
                text = "HEALTH BRIDGE"; textSize = 10f; letterSpacing = .18f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#64D2FF"))
            })
            addView(TextView(context).apply {
                text = "健康中心"; textSize = 23f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#F5F5F7"))
                setPadding(0, dp(4), 0, dp(2))
            })
            addView(TextView(context).apply {
                text = "Xiaomi Watch 2 Pro"; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#9097A4"))
            })
            addView(status, LinearLayout.LayoutParams(dp(88), dp(88)).apply { setMargins(0, dp(8), 0, dp(8)) })
            addView(action, LinearLayout.LayoutParams(dp(190), dp(48)))
            addView(measureButton("一鍵檢測", WearControlService.ACTION_SENSOR_SUITE, "#236A82").apply {
                textSize = 15f
                minHeight = dp(48)
                contentDescription = "一鍵檢測，開啟完整感測器掃描與進度頁"
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(TextView(context).apply {
                text = "快捷功能"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, dp(12))
            })
            addView(Button(context).apply {
                text = "健康 Apps"
                textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
                background = buttonBackground("#007AFF", "#0040DD")
                setOnClickListener {
                    startActivity(Intent(this@WearMainActivity, WearAppsActivity::class.java))
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
            addView(Button(context).apply {
                text = "今日 Vitals"
                textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
                background = buttonBackground("#30D158", "#248A3D")
                setOnClickListener {
                    startActivity(Intent(this@WearMainActivity, WearVitalsActivity::class.java))
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
            addView(Button(context).apply {
                text = "感測器與校正"
                textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
                background = buttonBackground("#1F6FEB", "#123A7A")
                setOnClickListener {
                    startActivity(Intent(this@WearMainActivity, WearChipHubActivity::class.java))
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(exerciseAction, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(5) })
                addView(oxygenProbeAction, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(5) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(measureButton("PPG", WearControlService.ACTION_MEASURE_PPG, "#FF6478"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
                addView(measureButton("ECG", WearControlService.ACTION_MEASURE_ECG, "#3DDC97"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
                addView(measureButton("BIOZ", WearControlService.ACTION_MEASURE_BIOZ, "#8B7CFF"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(measureButton("體溫", WearControlService.ACTION_MEASURE_TEMPERATURE, "#FFB347"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
                addView(measureButton("全掃描", WearControlService.ACTION_SENSOR_SUITE, "#56AEFF"), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(Button(context).apply {
                text = "心率專屬頁"; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
                background = buttonBackground("#FF5F73", "#8B1E3F")
                setOnClickListener {
                    startActivity(WearMeasurePageActivity.intent(this@WearMainActivity, WearMeasurePageActivity.Mode.HEART_RATE))
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
            addView(exercisePauseAction, LinearLayout.LayoutParams(dp(190), dp(48)).apply { setMargins(0, dp(10), 0, 0) })
            addView(Button(context).apply {
                text = "開啟運動專屬頁"; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
                background = buttonBackground("#236A82", "#174759")
                setOnClickListener {
                    startActivity(Intent(this@WearMainActivity, WearWorkoutActivity::class.java))
                }
            }, LinearLayout.LayoutParams(dp(220), dp(48)).apply { setMargins(0, dp(8), 0, 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(updateAction, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(5) })
                addView(installAction, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(5) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Button(context).apply {
                    text = "指南針"; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
                    background = buttonBackground("#315A86", "#1C3552")
                    setOnClickListener { startActivity(Intent(this@WearMainActivity, CompassActivity::class.java)) }
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
                addView(Button(context).apply {
                    text = "返航導航"; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
                    background = buttonBackground("#315A86", "#1C3552")
                    setOnClickListener { startActivity(Intent(this@WearMainActivity, BacktrackActivity::class.java)) }
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(Button(context).apply {
                text = "手勢／夜間省電設定"
                textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
                background = buttonBackground("#4B477B", "#2B294A")
                setOnClickListener {
                    startActivity(Intent(this@WearMainActivity, WearGestureActivity::class.java))
                }
            }, LinearLayout.LayoutParams(dp(240), dp(48)).apply { setMargins(0, dp(8), 0, 0) })
            addView(TextView(context).apply {
                text = "心率警示"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, dp(7))
            })
            addView(thresholdStatus, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)))
            addView(thresholdControls("高心率", true))
            addView(thresholdControls("低心率", false))
            addView(TextView(context).apply {
                text = "同步與裝置"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, dp(12))
            })
            addView(deviceStatus, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(syncStatus, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            backgroundSyncAction = Button(context).apply {
                textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
                background = buttonBackground("#236A82", "#174759")
                setOnClickListener { toggleBackgroundMonitoring() }
            }
            addView(backgroundSyncAction, LinearLayout.LayoutParams(dp(220), dp(48)).apply { setMargins(0, dp(10), 0, 0) })
            backgroundSyncIntervalAction = Button(context).apply {
                textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
                background = buttonBackground("#343438", "#232326")
                setOnClickListener { showBackgroundIntervalPicker() }
            }
            addView(backgroundSyncIntervalAction, LinearLayout.LayoutParams(dp(240), dp(48)).apply { setMargins(0, dp(6), 0, 0) })
            addView(TextView(context).apply {
                text = "此間隔只控制 App 心率去重／同步；Wear OS 決定感測器取樣與批次回報。省電模式至少 5 分鐘，極省模式暫停心率同步。"
                textSize = 10f; setTextColor(Color.parseColor("#AEB4C0"))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            addView(TextView(context).apply {
                text = "歷史與趨勢"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, dp(12))
            })
            addView(historyStatus, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(Button(context).apply {
                text = "開啟完整歷史"; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE)
                background = buttonBackground("#343438", "#232326")
                setOnClickListener { startActivity(Intent(this@WearMainActivity, WearHistoryActivity::class.java)) }
            }, LinearLayout.LayoutParams(dp(190), dp(48)).apply { setMargins(0, dp(10), 0, 0) })
            addView(TextView(context).apply {
                text = "裝置能力"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, dp(12))
            })
            addView(capabilities, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(clearQuarantineAction, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply {
                topMargin = dp(6)
            })
            addView(TextView(context).apply {
                text = "HEALTH BRIDGE · 研究資料與趨勢僅供健康參考"; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#75D5C8")); setPadding(0, dp(8), 0, dp(12))
            })
        }
        WearUi.installScrollable(this, content)
        ambientLightController = AmbientLightController(this) { mode ->
            runOnUiThread {
                val brightness = when (mode) {
                    "夜間省電" -> 0.18f
                    "室內省電" -> 0.42f
                    else -> 0.78f
                }
                window.attributes = window.attributes.apply { screenBrightness = brightness }
                deviceStatus.text = "環境光：$mode\n" + deviceStatus.text.toString().substringAfter('\n', "")
            }
        }
        updateBackgroundSyncControls()
        animateWearUi(content)
        updateThresholdStatus()
        loadCapabilities()
        refreshReceivedFile()
        OxygenSyncWorker.schedule(this)
        refreshDashboard()
        if (!measuring && !exerciseActive) {
            val latestHeartRate = WearHealthStore.latestHeartBpm(this)
            status.text = latestHeartRate?.let { "${it.toLong()}\nbpm\n最近量測" } ?: "點擊\n量測心率"
        }
        updateQuarantineAction()
        handleWorkoutExtras(intent)
    }

    private fun animateWearUi(root: ViewGroup) {
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(650).start()
        fun visit(parent: ViewGroup) {
            for (index in 0 until parent.childCount) {
                val child = parent.getChildAt(index)
                child.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { view.animate().scaleX(.94f).scaleY(.94f).setDuration(70).start(); false }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { view.animate().scaleX(1f).scaleY(1f).setDuration(150).start(); false }
                        else -> false
                    }
                }
                child.alpha = 0f
                child.translationY = dp(10).toFloat()
                child.animate().alpha(1f).translationY(0f).setStartDelay(index * 45L).setDuration(400).start()
                if (child is ViewGroup) visit(child)
            }
        }
        visit(root)
    }

    override fun onResume() {
        super.onResume()
        ambientLightController.start()
        refreshReceivedFile()
        updateBackgroundSyncControls()
        refreshDashboard()
        if (PassiveMonitoringSettings.isEnabled(this) && !passiveRegistered) registerPassiveMonitoring()
    }

    override fun onStop() {
        stopBackgroundMeasurement()
        super.onStop()
    }

    override fun onPause() {
        ambientLightController.stop()
        stopBackgroundMeasurement()
        super.onPause()
    }

    private fun stopBackgroundMeasurement() {
        oxygenProbe?.stop()
        oxygenProbe = null
        oxygenProbeAction.text = "◉  擷取 SpO₂ 原始資料"
        if (measuring && !exerciseActive) {
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
            measuring = false
            action.text = "♥  開始心率量測"
        }
    }

    private fun refreshReceivedFile() {
        if (!::installAction.isInitialized) return
        val prefs = getSharedPreferences(WearFileReceiverService.PREFS, MODE_PRIVATE)
        val file = prefs.getString(WearFileReceiverService.KEY_FILE, null)?.let(::File)
        installAction.isEnabled = file?.isFile == true
        installAction.text = if (file?.isFile == true) "安裝收到的 ${file.name}" else "尚未收到 APK"
    }

    private fun installReceivedApk() {
        val path = getSharedPreferences(WearFileReceiverService.PREFS, MODE_PRIVATE)
            .getString(WearFileReceiverService.KEY_FILE, null) ?: return
        val file = File(path).takeIf { it.isFile } ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun ensurePermissionAndStart() {
        val sensorPermission = if (android.os.Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE"
        else Manifest.permission.BODY_SENSORS
        val permissions = arrayOf(
            sensorPermission,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
        if (!corePermissionsGranted()) permissionLauncher.launch(permissions)
    }
    private fun corePermissionsGranted(): Boolean {
        val sensorPermission = if (android.os.Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE"
        else Manifest.permission.BODY_SENSORS
        return checkSelfPermission(sensorPermission) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestBackgroundMonitoring() {
        val heartRatePermission = WearMeasurementPermissionPolicy.heartRatePermission(android.os.Build.VERSION.SDK_INT)
        if (checkSelfPermission(heartRatePermission) != PackageManager.PERMISSION_GRANTED) {
            heartRatePermissionLauncher.launch(heartRatePermission)
            return
        }
        val permission = WearMeasurementPermissionPolicy.backgroundHeartRatePermission(android.os.Build.VERSION.SDK_INT)
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            PassiveMonitoringSettings.setEnabled(this, true)
            registerPassiveMonitoring()
        } else {
            backgroundPermissionLauncher.launch(permission)
        }
    }
    private fun startMeasurement() {
        if (!measuring) {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
            measuring = true; status.text = "等待\n心率…"; action.text = "量測中"
        }
    }
    override fun onDestroy() {
        mainHandler.removeCallbacks(gpsTimeoutWarning)
        oxygenProbe?.stop()
        if (measuring) measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        advancedSensorController?.stop()
        super.onDestroy()
    }
    private fun loadCapabilities() {
        val measureFuture = measureClient.getCapabilitiesAsync()
        measureFuture.addListener({
            runCatching { measureFuture.get() }.onSuccess { measureTypes = it.supportedDataTypesMeasure; renderCapabilities() }
        }, mainExecutor)
        val passiveFuture = HealthServices.getClient(this).passiveMonitoringClient.getCapabilitiesAsync()
        passiveFuture.addListener({
            runCatching { passiveFuture.get() }.onSuccess {
                passiveTypes = it.supportedDataTypesPassiveMonitoring
                renderCapabilities()
                if (PassiveMonitoringSettings.isEnabled(this@WearMainActivity) && !passiveRegistered) {
                    registerPassiveMonitoring()
                }
            }
        }, mainExecutor)
        val exerciseFuture = HealthServices.getClient(this).exerciseClient.getCapabilitiesAsync()
        exerciseFuture.addListener({
            runCatching { exerciseFuture.get() }.onSuccess { result ->
                exerciseCount = result.supportedExerciseTypes.size
                exerciseTypes = result.typeToCapabilities.values.flatMap { it.supportedDataTypes }.toSet()
                walkingTypes = result.typeToCapabilities[ExerciseType.WALKING]?.supportedDataTypes.orEmpty()
                renderCapabilities()
            }
        }, mainExecutor)
    }
    private fun renderCapabilities() {
        val allTypes = measureTypes + passiveTypes + exerciseTypes
        fun support(type: androidx.health.services.client.data.DataType<*, *>) = if (type in allTypes) "✓ 可用" else "– 韌體未回報"
        capabilities.text = buildString {
            append("\n裝置能力\n")
            append("心率  ${support(DataType.HEART_RATE_BPM)}\n")
            append("高心率警示  ✓ ≥ ${highThreshold()} bpm\n")
            append("低心率警示  ✓ ≤ ${lowThreshold()} bpm\n")
            append("步數  ${support(DataType.STEPS)}\n")
            append("距離  ${support(DataType.DISTANCE)}\n")
            append("卡路里  ${support(DataType.CALORIES)}\n")
            append("GPS 路線  ${support(DataType.LOCATION)}\n")
            append("運動模式  ${if (exerciseCount > 0) "✓ $exerciseCount 種" else "– 韌體未回報"}\n\n")
            append("Health Services 無直接標準資料：\n")
            append("靜止心率：缺少可靠靜止狀態長期資料\n")
            append("HRV：缺少 RR 間期／逐拍資料\n")
            append("呼吸速率、睡眠／階段、體重\n")
            append("SpO₂：手錶硬體支援；Health Services 無標準資料型別\n")
            if (oxygenSensors.isEmpty()) {
                append("SpO₂ 原始感測器：– Xiaomi 韌體未向第三方 App 暴露\n")
            } else {
                append("SpO₂ 原始感測器：✓ 偵測到 ${oxygenSensors.joinToString { it.name }}\n")
                append("SpO₂ 原始擷取：✓ 僅手動短測，不在背景喚醒感測器，且不寫入百分比\n")
                val oxygenStatus = getSharedPreferences(OxygenSyncWorker.PREFS, MODE_PRIVATE)
                    .getString(OxygenSyncWorker.KEY_STATUS, null)
                if (oxygenStatus != null) append("最近狀態：$oxygenStatus\n")
            }
            append("PPG／ECG／BIOZ：✓ 研究用原始訊號與品質檢查（非醫療診斷）\n")
            append("溫度感測：原始值可讀；單位與整機校準未驗證\n")
            append("全感測器掃描：✓ 快捷鍵已開\n")
            append("原始封包傳送：${WearSnapshotOutbox.statusSummary(this@WearMainActivity)}（Data Layer 接受不等同手機已保存）\n")
            append("被動同步：${if (passiveRegistered) "✓ 已註冊" else "– 尚未註冊或缺少權限"}\n")
            getSharedPreferences(PassiveRegistration.PREFS, MODE_PRIVATE)
                .getString(PassiveRegistration.KEY_STATUS, null)
                ?.let { append("背景同步狀態：$it\n") }
            append("\n研究／誠實標示：\n")
            append("光學血壓：✓ PPG 研究估計（非袖帶核准裝置）\n")
            append("無法開啟：臨床 ECG 診斷、REM／深睡分期、小米 PAI／壓力私有演算法、Health Services 標準 SpO₂／HRV")
        }
        updateQuarantineAction()
    }

    private fun updateQuarantineAction() {
        if (!::clearQuarantineAction.isInitialized) return
        val count = WearSnapshotOutbox.quarantinedCount(this)
        clearQuarantineAction.visibility = if (count > 0) View.VISIBLE else View.GONE
        clearQuarantineAction.text = "清除隔離封包（$count）"
    }

    private fun confirmClearQuarantine() {
        val count = WearSnapshotOutbox.quarantinedCount(this)
        if (count == 0) {
            updateQuarantineAction()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("刪除隔離封包？")
            .setMessage("將從 App 移除 $count 筆無法解密或驗證的隔離檔；待送封包不會刪除。移除後無法由 App 復原，但不代表已對快閃儲存裝置進行安全抹除。")
            .setPositiveButton("刪除隔離檔") { _, _ ->
                val removed = WearSnapshotOutbox.clearQuarantine(this)
                renderCapabilities()
                updateQuarantineAction()
                android.widget.Toast.makeText(this, "已刪除 $removed 筆隔離檔", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    private fun registerPassiveMonitoring() {
        if (!PassiveMonitoringSettings.isEnabled(this) || passiveRegistrationInFlight) return
        passiveRegistrationInFlight = true
        PassiveRegistration.register(this) { result ->
            runOnUiThread {
                passiveRegistrationInFlight = false
                passiveRegistered = result.getOrNull()?.let { it > 0 } == true
                updateBackgroundSyncControls()
                renderCapabilities()
                result.onSuccess { count ->
                    if (count > 0) capabilities.append("\n背景同步已註冊 $count 種資料。")
                    else capabilities.append("\n背景同步目前暫停或尚無可註冊資料型別。")
                }
                    .onFailure { capabilities.append("\n背景同步未註冊：${it.message}") }
            }
        }
    }

    private fun toggleBackgroundMonitoring() {
        if (PassiveMonitoringSettings.isEnabled(this) && passiveRegistered) {
            PassiveRegistration.unregister(this) { result ->
                runOnUiThread {
                    passiveRegistered = false
                    updateBackgroundSyncControls()
                    result.onFailure { capabilities.append("\n停止背景同步失敗：${it.message}") }
                    renderCapabilities()
                }
            }
        } else {
            requestBackgroundMonitoring()
        }
    }

    private fun showBackgroundIntervalPicker() {
        val intervals = PassiveMonitoringSettings.supportedIntervalsMinutes
        val selected = intervals.indexOf(PassiveMonitoringSettings.intervalMinutes(this)).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("心率資料轉送間隔")
            .setMessage("只控制收到的心率資料何時同步，不會改變手錶感測器取樣頻率。PPG、ECG、血氧與 BioZ 不會因此在背景連續量測。")
            .setSingleChoiceItems(intervals.map(PassiveMonitoringSettings::intervalLabel).toTypedArray(), selected) { dialog, which ->
                PassiveMonitoringSettings.setIntervalMinutes(this, intervals[which])
                updateBackgroundSyncControls()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateBackgroundSyncControls() {
        if (!::backgroundSyncAction.isInitialized || !::backgroundSyncIntervalAction.isInitialized) return
        val enabled = PassiveMonitoringSettings.isEnabled(this)
        val interval = PassiveMonitoringSettings.intervalMinutes(this)
        passiveRegistered = getSharedPreferences(PassiveRegistration.PREFS, MODE_PRIVATE)
            .getBoolean(PassiveRegistration.KEY_REGISTERED, false) && enabled
        backgroundSyncAction.text = when {
            !enabled -> "開啟背景同步（較耗電）"
            !passiveRegistered -> "完成背景心率授權"
            else -> "關閉背景同步"
        }
        backgroundSyncIntervalAction.text = "心率轉送：${PassiveMonitoringSettings.intervalLabel(interval)}"
    }
    private fun vibrateAlert() {
        val now = System.currentTimeMillis()
        if (now - lastAlertMillis < ALERT_COOLDOWN_MILLIS) return
        lastAlertMillis = now
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1)
        )
    }
    private fun toggleWalkingExercise() {
        if (exerciseActive) stopWalkingExercise() else startWalkingExercise()
    }
    private fun startWalkingExercise() {
        if (!corePermissionsGranted()) {
            ensurePermissionAndStart()
            capabilities.append("\n授權後請再次按開始步行運動")
            return
        }
        if (walkingTypes.isEmpty()) {
            capabilities.append("\n此韌體未回報步行運動支援")
            return
        }
        if (measuring) {
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
            measuring = false
        }
        val requested = setOf(DataType.HEART_RATE_BPM, DataType.STEPS, DataType.DISTANCE, DataType.CALORIES, DataType.LOCATION)
            .filter { it in walkingTypes }.toSet()
        val gps = DataType.LOCATION in requested &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val config = ExerciseConfig.builder(ExerciseType.WALKING)
            .setDataTypes(if (gps) requested else requested - DataType.LOCATION)
            .setIsGpsEnabled(gps)
            .build()
        val client = HealthServices.getClient(this).exerciseClient
        client.setUpdateCallback(exerciseCallback)
        val future = client.startExerciseAsync(config)
        future.addListener({
            runCatching { future.get() }.onSuccess {
                exerciseActive = true
                exerciseStartMillis = System.currentTimeMillis()
                routePoints.clear()
                exerciseHeartRates.clear()
                totalPausedMillis = 0L
                pauseStartedMillis = 0L
                exercisePaused = false
                gpsFixReceived = false
                advancedSensorController?.stop()
                advancedSensorController = AdvancedSensorController(
                    this,
                    onUpdate = { snapshot ->
                        runOnUiThread {
                            val altitude = snapshot.altitude.takeIf { it.isFinite() }?.let { "%.0f m".format(it) } ?: "—"
                            val temperature = snapshot.rawTemperature?.let { "%.1f（單位未驗證）".format(it) } ?: "—"
                            status.text = "${if (gps) "GPS 定位中" else "步行記錄中"}\n海拔 $altitude · 溫度原始值 $temperature"
                        }
                    },
                    onTemperatureReady = { rawValue, _, _ ->
                        runOnUiThread { status.text = "運動記錄中\n已收到溫度原始值 ${"%.1f".format(rawValue)}（單位未驗證）" }
                    },
                    onFallRisk = { magnitude ->
                        runOnUiThread { status.text = "⚠ 可能跌倒\n請確認狀態（${"%.1f".format(magnitude)} m/s²）" } 
                        WearSafetyAlerts.showFallConfirmation(this, magnitude)
                    }
                ).also { it.resetElevation(); it.start() }
                exerciseAction.text = "結束並同步運動"
                status.text = if (gps) "GPS 定位中\n請到開闊處" else "步行\n記錄中"
                if (gps) mainHandler.postDelayed(gpsTimeoutWarning, GPS_COLD_START_TIMEOUT_MILLIS)
            }.onFailure { capabilities.append("\n啟動運動失敗：${it.message}") }
        }, mainExecutor)
    }
    private fun stopWalkingExercise() {
        val client = HealthServices.getClient(this).exerciseClient
        val future = client.endExerciseAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess {
                mainHandler.removeCallbacks(gpsTimeoutWarning)
                val end = System.currentTimeMillis()
                advancedSensorController?.syncElevationIfNeeded(force = true)
                advancedSensorController?.stop()
                advancedSensorController = null
                if (exercisePaused && pauseStartedMillis > 0) totalPausedMillis += end - pauseStartedMillis
                val validRoute = routePoints.filter { it.time in exerciseStartMillis until end }
                val request = PutDataMapRequest.create("/health/exercise/$end").apply {
                    dataMap.putString("metric", "exercise")
                    dataMap.putDouble("value", 1.0)
                    dataMap.putLong("start", exerciseStartMillis)
                    dataMap.putLong("end", end)
                    dataMap.putStringArray(
                        "route_points",
                        validRoute.map { "${it.time},${it.latitude},${it.longitude},${it.altitude}" }.toTypedArray()
                    )
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(this).putDataItem(request)
                exerciseActive = false
                exercisePauseAction.isEnabled = false
                exerciseAction.text = "開始步行運動"
                status.text = "已同步\n步行運動"
                val average = exerciseHeartRates.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
                WearHealthStore.recordExercise(
                    this, exerciseStartMillis, end, average, heartZone(average), totalPausedMillis,
                    validRoute.map { "${it.time},${it.latitude},${it.longitude},${it.altitude}" }
                )
                WearHealthStore.markSync(this, true, "運動已同步")
                refreshDashboard()
                client.clearUpdateCallbackAsync(exerciseCallback)
            }.onFailure { capabilities.append("\n結束運動失敗：${it.message}") }
        }, mainExecutor)
    }
    private fun sendHeartRate(bpm: Long, timestamp: Long) {
        if (bpm <= 0) return
        val payload = HealthServicesHeartRatePayload.create(bpm, timestamp) ?: return
        if (timestamp - lastHeartRateSentMillis < HEART_RATE_UPLOAD_INTERVAL_MILLIS) return
        lastHeartRateSentMillis = timestamp
        WearHealthStore.recordHeartRate(this, bpm, timestamp)
        val request = PutDataMapRequest.create("/heart-rate/${payload.eventTimestampMillis}").apply {
            dataMap.putLong("bpm", payload.bpm)
            dataMap.putLong("timestamp", payload.eventTimestampMillis)
            dataMap.putString("timestamp_source", payload.timestampSource)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request)
            .addOnSuccessListener { WearHealthStore.markSync(this, true, "心率已同步"); refreshDashboard() }
            .addOnFailureListener { WearHealthStore.markSync(this, false, "心率等待重試"); refreshDashboard() }
    }

    private fun openSystemUpdate() {
        val updateIntent = Intent("android.settings.SYSTEM_UPDATE_SETTINGS")
        val intent = if (packageManager.resolveActivity(updateIntent, 0) != null) {
            updateIntent
        } else {
            Intent("android.settings.SETTINGS")
        }
        startActivity(intent)
    }
    private fun toggleExercisePause() {
        if (!exerciseActive) return
        val client = HealthServices.getClient(this).exerciseClient
        val wasPaused = exercisePaused
        exercisePauseAction.isEnabled = false
        val future = if (wasPaused) client.resumeExerciseAsync() else client.pauseExerciseAsync()
        future.addListener({
            runCatching { future.get() }
                .onSuccess {
                    val now = System.currentTimeMillis()
                    if (wasPaused) {
                        if (pauseStartedMillis > 0) totalPausedMillis += now - pauseStartedMillis
                        pauseStartedMillis = 0L
                    } else {
                        pauseStartedMillis = now
                    }
                }
                .onFailure { runOnUiThread { capabilities.append("\n暫停控制失敗：${it.message}"); exercisePauseAction.isEnabled = true } }
        }, mainExecutor)
    }

    private fun heartZone(bpm: Long) = when {
        bpm <= 0 -> "無心率資料"
        bpm < 100 -> "暖身區"
        bpm < 130 -> "燃脂區"
        bpm < 160 -> "有氧區"
        else -> "高強度區"
    }

    private fun refreshDashboard() {
        if (!::deviceStatus.isInitialized) return
        val battery = getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                val dashboard = WearHealthStore.dashboard(this)
                deviceStatus.text = "電量  ${if (battery >= 0) "$battery%" else "未知"}\n手機連線  ${if (nodes.isNotEmpty()) "已連線" else "未連線"}\nGPS  ${if (exerciseActive) if (gpsFixReceived) "已定位" else "定位中" else "待命"}"
                syncStatus.text = "${if (dashboard.syncOk) "✓" else "!"} ${dashboard.syncMessage}\n${if (dashboard.syncTime > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(dashboard.syncTime) else "尚未同步"}"
                historyStatus.text = WearHealthStore.historyText(this)
            }
    }

    private fun infoCard(initial: String) = TextView(this).apply {
        text = initial; textSize = 12f; setTextColor(Color.parseColor("#E5E5EA")); setPadding(dp(16), dp(15), dp(16), dp(15))
        background = buttonBackground("#242426", "#19191B")
    }
    private fun thresholdControls(label: String, high: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        addView(TextView(context).apply {
            text = label; textSize = 12f; setTextColor(Color.parseColor("#AAB5CB")); gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(dp(70), dp(48)))
        addView(smallButton("−") { adjustThreshold(high, -5) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(smallButton("＋") { adjustThreshold(high, 5) }, LinearLayout.LayoutParams(dp(48), dp(48)))
    }
    private fun smallButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label; textSize = 15f; setTextColor(Color.WHITE); setOnClickListener { click() }
    }
    private fun adjustThreshold(high: Boolean, delta: Int) {
        val prefs = getSharedPreferences(THRESHOLD_PREFS, MODE_PRIVATE)
        if (high) {
            val next = (highThreshold() + delta).coerceIn(lowThreshold() + 10, 220)
            prefs.edit().putLong(KEY_HIGH, next).apply()
        } else {
            val next = (lowThreshold() + delta).coerceIn(30, highThreshold() - 10)
            prefs.edit().putLong(KEY_LOW, next).apply()
        }
        updateThresholdStatus(); renderCapabilities()
    }
    private fun updateThresholdStatus() {
        thresholdStatus.text = "警示範圍  ${lowThreshold()}–${highThreshold()} bpm"
    }
    private fun highThreshold() = getSharedPreferences(THRESHOLD_PREFS, MODE_PRIVATE).getLong(KEY_HIGH, DEFAULT_HIGH)
    private fun lowThreshold() = getSharedPreferences(THRESHOLD_PREFS, MODE_PRIVATE).getLong(KEY_LOW, DEFAULT_LOW)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun measureButton(label: String, action: String, color: String) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = buttonBackground(color, "#1A1A1E")
        setOnClickListener {
            when (action) {
                WearControlService.ACTION_MEASURE_ECG ->
                    startActivity(Intent(this@WearMainActivity, WearEcgActivity::class.java))
                WearControlService.ACTION_MEASURE_PPG ->
                    startActivity(WearMeasurePageActivity.intent(this@WearMainActivity, WearMeasurePageActivity.Mode.PPG))
                WearControlService.ACTION_MEASURE_BIOZ ->
                    startActivity(WearMeasurePageActivity.intent(this@WearMainActivity, WearMeasurePageActivity.Mode.BIOZ))
                WearControlService.ACTION_MEASURE_TEMPERATURE ->
                    startActivity(WearMeasurePageActivity.intent(this@WearMainActivity, WearMeasurePageActivity.Mode.TEMPERATURE))
                WearControlService.ACTION_SENSOR_SUITE ->
                    startActivity(WearMeasurePageActivity.intent(this@WearMainActivity, WearMeasurePageActivity.Mode.SUITE))
                else -> {
                    status.text = "$label 處理中"
                    runCatching {
                        startForegroundService(WearControlCommandAuth.createIntent(this@WearMainActivity, action))
                    }.onFailure { error -> status.text = "$label 啟動失敗：${error.javaClass.simpleName}" }
                }
            }
        }
    }

    private fun buttonBackground(start: String, end: String) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(start), Color.parseColor(end))
    ).apply {
        cornerRadius = dp(7).toFloat()
        setStroke(dp(1), Color.argb(70, 255, 255, 255))
    }

    private fun findOxygenSensors(): List<Sensor> {
        val keywords = listOf("spo2", "spo₂", "oxygen", "blood oxygen", "o2")
        return getSystemService(SensorManager::class.java)
            ?.getSensorList(Sensor.TYPE_ALL)
            .orEmpty()
            .filter { sensor ->
                val description = "${sensor.name} ${sensor.vendor} ${sensor.stringType}".lowercase()
                keywords.any(description::contains)
            }
    }

    private fun toggleOxygenProbe() {
        if (oxygenProbe != null) {
            oxygenProbe?.stop(); oxygenProbe = null; oxygenProbeAction.text = "擷取 SpO₂ 原始資料"
            return
        }
        val probeHolder = arrayOfNulls<RawOxygenProbe>(1)
        val probe = RawOxygenProbe(
            this,
            onReading = { reading ->
                runOnUiThread {
                    capabilities.text = "SpO₂ 原始事件（單位未驗證）\n$reading\n\n${capabilities.text}"
                    val active = probeHolder[0]
                    if (active != null && oxygenProbe === active) {
                        active.stop()
                        oxygenProbe = null
                        probeHolder[0] = null
                        oxygenProbeAction.text = "擷取 SpO₂ 原始資料"
                        status.text = "已取得 SpO₂ 原始事件\n單位未驗證，未寫入百分比"
                    }
                }
            }
        )
        probeHolder[0] = probe
        val count = probe.start()
        if (count == 0) {
            probe.stop()
            probeHolder[0] = null
            status.text = "SpO₂\n量測忙碌"
            return
        }
        oxygenProbe = probe
        status.text = "正在擷取原始 SpO₂\n取得事件即停止，最長 60 秒"
        oxygenProbeAction.text = "停止原始擷取（$count）"
        mainHandler.postDelayed({
            val active = probeHolder[0]
            if (active != null && oxygenProbe === active) {
                active.stop()
                oxygenProbe = null
                probeHolder[0] = null
                oxygenProbeAction.text = "擷取 SpO₂ 原始資料"
                status.text = "等待過久仍無 SpO₂ 事件"
            }
        }, RawOxygenProbe.MAX_CAPTURE_MILLIS)
    }

    companion object {
        const val EXTRA_ARM_OEM_KIND = "arm_oem_kind"
        const val EXTRA_TOGGLE_WALKING = "toggle_walking"
        const val EXTRA_TOGGLE_PAUSE = "toggle_pause"
        const val DEFAULT_HIGH = 120L
        const val DEFAULT_LOW = 50L
        const val ALERT_COOLDOWN_MILLIS = 5 * 60 * 1000L
        const val THRESHOLD_PREFS = "heart_rate_thresholds"
        const val KEY_HIGH = "high"
        const val KEY_LOW = "low"
        const val MAX_ROUTE_POINTS = 1000
        const val HEART_RATE_UPLOAD_INTERVAL_MILLIS = 60_000L
        const val GPS_COLD_START_TIMEOUT_MILLIS = 90_000L
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOemArmIntent(intent)
        handleWorkoutExtras(intent)
    }

    private fun handleWorkoutExtras(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_TOGGLE_WALKING, false) == true) {
            intent.removeExtra(EXTRA_TOGGLE_WALKING)
            toggleWalkingExercise()
        }
        if (intent?.getBooleanExtra(EXTRA_TOGGLE_PAUSE, false) == true) {
            intent.removeExtra(EXTRA_TOGGLE_PAUSE)
            toggleExercisePause()
        }
    }

    private fun handleOemArmIntent(intent: Intent?) {
        val wire = intent?.getStringExtra(EXTRA_ARM_OEM_KIND) ?: return
        val kind = RawVitalsProbe.Kind.entries.firstOrNull { it.wireName == wire } ?: return
        intent.removeExtra(EXTRA_ARM_OEM_KIND)
        mainHandler.postDelayed({
            val message = OemChipEnabler.enable(this, kind)
            if (::status.isInitialized && message != null) {
                status.text = message
            }
        }, 350L)
    }

    private data class RoutePoint(val time: Long, val latitude: Double, val longitude: Double, val altitude: Double)
}
