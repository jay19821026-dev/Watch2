package com.jaysato.xiaomihealthbridge

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.jaysato.xiaomihealthbridge.engine.EcgEventStructureInterpreter
import com.jaysato.xiaomihealthbridge.engine.EcgRawEventDecoder
import kotlin.math.abs
import kotlin.math.max

/**
 * First-party ECG measure page for Watch 2 Pro.
 * Does not depend on Xiaomi OEM ECG (often unavailable / consent / sleep-mode blocked).
 * Uses the public Sensor API endpoint `com.google.wear.sensor.ecg`; the physical AFE/configuration is unverified.
 */
class WearEcgActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var probe: RawVitalsProbe? = null
    private lateinit var chart: EcgSignalView
    private lateinit var headline: TextView
    private lateinit var detail: TextView
    private lateinit var rhythmStatus: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var doneButton: Button
    private var active = false
    private var completedSegments = 0
    private var captureGeneration = 0
    private var continuationPending = false
    private val scheduledStart = Runnable {
        continuationPending = false
        if (!isFinishing && !isDestroyed && !active) startMeasurement(resetSegments = false)
    }
    private var lastSampleCount = 0
    private var lastSampleAtMs = 0L
    private var nextPreviewAtElapsedMs = 0L
    private var lastComputePolicyReadAtMs = 0L
    private var hasComputePolicySample = false
    private var previewIntervalMs = 700L
    private val hardStop = Runnable {
        if (active) stopMeasurement(auto = true, stopReason = "time_limit_reached")
    }
    private val quietCheck = object : Runnable {
        override fun run() {
            if (!active) return
            val idle = System.currentTimeMillis() - lastSampleAtMs
            val stopReason = EcgCapturePolicy.idleStopReason(lastSampleCount, idle)
            if (stopReason != null) {
                stopMeasurement(auto = true, stopReason = stopReason)
                return
            }
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(20))
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = "ECG"; textSize = 11f; letterSpacing = .18f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(85, 220, 167))
            })
            addView(TextView(context).apply {
                text = "心電圖"; textSize = 20f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE); setPadding(0, dp(2), 0, dp(2))
            })
            addView(TextView(context).apply {
                text = "研究解碼波形 · 相對振幅，非 mV"
                textSize = 10f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(140, 150, 170)); setPadding(0, 0, 0, dp(8))
            })
            addView(TextView(context).apply {
                text = "尚未開始"; textSize = 15f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            }.also { headline = it })
            addView(TextView(context).apply {
                text = "雙指同時按住錶冠與側鍵（電極），手臂平放靜止"
                textSize = 11f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(172, 182, 203)); setPadding(0, dp(4), 0, dp(8))
            }.also { detail = it })
            addView(EcgSignalView(context).also { chart = it; it.visibility = View.GONE }, LinearLayout.LayoutParams(-1, dp(110)))
            addView(TextView(context).apply {
                text = "心律：尚未量測"
                textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.rgb(255, 210, 120))
                setPadding(0, dp(8), 0, dp(4))
            }.also { rhythmStatus = it })
            addView(Button(context).apply {
                text = "開始量測"; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
                background = ecgButtonBackground("#198C68", "#0E513D")
                setOnClickListener { startMeasurement(resetSegments = true) }
            }.also { startButton = it }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
            addView(Button(context).apply {
                text = "開啟原廠 ECG 頁面"; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE)
                background = ecgButtonBackground("#2A3857", "#182238")
                setOnClickListener {
                    if (active || continuationPending) {
                        detail.text = "請先結束本 App 的原始資料擷取，再查看原廠頁面。"
                    } else {
                        detail.text = OemChipEnabler.enable(this@WearEcgActivity, RawVitalsProbe.Kind.ECG)
                            ?: "此裝置沒有可開啟的原廠 ECG 頁面"
                    }
                }
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
            addView(TextView(context).apply {
                text = "此按鈕只開啟原廠頁面，不讀取或變更啟用狀態。若 Mi Fitness 有 ECG 卡片，需先依其流程開通。"
                textSize = 10f; setTextColor(Color.rgb(165, 177, 196)); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            addView(Button(context).apply {
                text = "結束並保存原始資料"; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
                background = ecgButtonBackground("#6B3A3A", "#3A1E1E")
                isEnabled = false
                setOnClickListener {
                    if (active || continuationPending) {
                        stopMeasurement(auto = false)
                    }
                }
            }.also { stopButton = it }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            addView(Button(context).apply {
                text = "完成"; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
                background = ecgButtonBackground("#2C2C2E", "#1C1C1E")
                visibility = android.view.View.GONE
                setOnClickListener { finish() }
            }.also { doneButton = it }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
            addView(TextView(context).apply {
                text = "研究判讀可能不準確，不能診斷或排除疾病。胸痛、暈厥、呼吸困難或持續不適請就醫；以醫師評估為準。"
                textSize = 10f; setTextColor(Color.rgb(255, 170, 110)); setPadding(0, dp(10), 0, 0)
            })
        }
        WearUi.installScrollable(this, content)
        if (intent?.getBooleanExtra(EXTRA_AUTO_START, false) == true) {
            handler.postDelayed(scheduledStart, 350L)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTO_START, false) && !active) {
            handler.removeCallbacks(scheduledStart)
            handler.postDelayed(scheduledStart, 200L)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(quietCheck)
        handler.removeCallbacks(hardStop)
        stopMeasurement(auto = false, publish = active)
        super.onDestroy()
    }

    private fun startMeasurement(resetSegments: Boolean = false) {
        if (isFinishing || isDestroyed) return
        handler.removeCallbacks(scheduledStart)
        continuationPending = false
        if (resetSegments) completedSegments = 0
        if (getSystemService(BatteryManager::class.java)?.isCharging == true) {
            active = false
            headline.text = "請先移除充電器"
            detail.text = "充電期間無法確認手腕與電極接觸，可能收不到可用 ECG。拔除充電器、戴好手錶並用雙指接觸電極後再試。"
            rhythmStatus.text = "心律：未量測"
            startButton.isEnabled = true
            stopButton.isEnabled = false
            return
        }
        stopMeasurement(auto = false, publish = false)
        val generation = captureGeneration
        val session = SensorRegistrationGuard.create(factory = { RawVitalsProbe(this, RawVitalsProbe.Kind.ECG) { snapshot ->
            runOnUiThread {
                if (!active || generation != captureGeneration) return@runOnUiThread
                update(snapshot)
                lastSampleCount = snapshot.eventCount
                if (snapshot.eventCount > 0) lastSampleAtMs = System.currentTimeMillis()
            }
        } }, onFailure = {
            headline.text = "ECG 通道建立失敗"
            detail.text = if (it is SecurityException) "請重新授予感測器權限後重試。" else "感測器初始化失敗；其他功能仍可使用，請重新量測。"
            rhythmStatus.text = "未啟動，沒有量測結果"
        }) ?: return
        if (!EcgMeasurementLease.acquire(session)) {
            headline.text = "ECG 正由另一個量測使用"
            detail.text = "請先結束全感測器掃描，再重新開始 ECG。"
            rhythmStatus.text = "心律：未量測"
            return
        }
        if (!session.start()) {
            val failedSnapshot = session.stop()
            EcgMeasurementLease.release(session)
            headline.text = "無法啟動 ECG 通道"
            detail.text = "註冊：${failedSnapshot.registrationResult}\n原因：${failedSnapshot.acquisitionStopReason}\n請確認感測器權限後重試。"
            rhythmStatus.text = "心律：無法啟動"
            runCatching { publishFinal(failedSnapshot) }.onFailure {
                detail.append("\n失敗紀錄未能送出；請重新量測。")
            }
            return
        }
        probe = session
        active = true
        nextPreviewAtElapsedMs = 0L
        lastComputePolicyReadAtMs = 0L
        hasComputePolicySample = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lastSampleCount = 0
        lastSampleAtMs = System.currentTimeMillis()
        startButton.isEnabled = false
        stopButton.isEnabled = true
        stopButton.text = "結束並保存原始資料"
        if (::doneButton.isInitialized) doneButton.visibility = android.view.View.GONE
        headline.text = "持續收集中 · 第 ${completedSegments + 1} 區段"
        detail.text = "雙指接觸電極以觸發手錶事件。格式通過研究解碼後會顯示索引軸相對波形；沒有時間及裝置校準時不換算 mV、RR 或心率。"
        rhythmStatus.text = "節律：需可信時間與波形校準"
        chart.setSamples(emptyList())
        handler.removeCallbacks(quietCheck)
        handler.removeCallbacks(hardStop)
        handler.postDelayed(quietCheck, 500L)
        handler.postDelayed(hardStop, EcgCapturePolicy.MAX_CAPTURE_MILLIS)
    }

    private fun stopMeasurement(
        auto: Boolean,
        publish: Boolean = true,
        stopReason: String? = null
    ) {
        handler.removeCallbacks(scheduledStart)
        continuationPending = false
        captureGeneration++
        handler.removeCallbacks(quietCheck)
        handler.removeCallbacks(hardStop)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val session = probe ?: run {
            active = false
            if (::startButton.isInitialized) startButton.isEnabled = true
            if (::stopButton.isInitialized) stopButton.isEnabled = false
            return
        }
        probe = null
        active = false
        if (::startButton.isInitialized) startButton.isEnabled = true
        if (::stopButton.isInitialized) stopButton.isEnabled = false
        if (::doneButton.isInitialized) doneButton.visibility = android.view.View.VISIBLE
        val snapshot = try {
            session.stop(stopReason)
        } catch (error: RuntimeException) {
            headline.text = "停止或快照建立失敗"
            detail.text = "${error.javaClass.simpleName}；沒有確認保存成功，可重新量測。"
            stopButton.text = "停止失敗，請重新量測"
            return
        } finally {
            EcgMeasurementLease.release(session)
        }
        stopButton.text = "本次量測已結束"
        update(snapshot)
        val published = !publish || runCatching { publishFinal(snapshot) }.isSuccess
        if (publish && snapshot.eventCount > 0) completedSegments++
        val continueAutomatically = publish && published && !isFinishing && !isDestroyed && EcgCapturePolicy.canAutoContinue(
            snapshot.acquisitionStopReason,
            completedSegments
        )
        startButton.text = "重新量測"
        if (continueAutomatically) {
            headline.text = "持續收集中 · 已保存第 ${completedSegments} 區段"
            detail.append("\n將自動開始下一區段（最多 ${EcgCapturePolicy.MAX_AUTO_SEGMENTS} 區段）；按「結束並保存」可隨時停止。")
            continuationPending = true
            startButton.isEnabled = false
            stopButton.isEnabled = true
            stopButton.text = "結束連續收集"
            handler.postDelayed(scheduledStart, 250L)
        } else if (snapshot.acquisitionStopReason == "time_limit_reached" && completedSegments >= EcgCapturePolicy.MAX_AUTO_SEGMENTS) {
            detail.append("\n已達本次連續收集上限 ${EcgCapturePolicy.MAX_AUTO_SEGMENTS} 區段，請按「完成」或重新開始。")
        }
        if (!published) detail.append("\n資料送出失敗；尚未確認保存成功，請重試。")
    }

    private fun update(snapshot: RawVitalsProbe.Snapshot) {
        lastSampleCount = snapshot.eventCount
        if (active && !shouldRenderPreview()) return
        val rows = if (active) snapshot.eventRawValueRows.takeLast(120) else snapshot.eventRawValueRows
        val eventPreview = EcgEventPreview.oemPacketCandidates(
            snapshot.eventRawValueRows, snapshot.ecgEventContactStates, snapshot.eventTimestampsNs, snapshot.eventCount,
            runCatching { android.provider.Settings.Global.getInt(contentResolver, "wear_wrist_orientation_mode", 0) }.getOrDefault(0)
        )
        // Always let the owner inspect received numeric events. A missing OEM marker or
        // on-body callback only disqualifies the qualified preview, not the raw data view.
        val rawFieldPreview = EcgEventPreview.firstField(rows)
        val showQualifiedPreview = eventPreview.size >= 2
        val visiblePreview = if (showQualifiedPreview) eventPreview else rawFieldPreview
        chart.setSamples(visiblePreview)
        chart.contentDescription = if (showQualifiedPreview) {
            "符合已觀察原廠封包條件的前 10 欄研究預覽；時間及單位未校準"
        } else {
            "原始 ECG 感測事件第 1 欄數值，按事件順序顯示；未通過封包條件，不代表已解碼心電波形"
        }
        chart.visibility = if (visiblePreview.size >= 2) View.VISIBLE else View.GONE
        headline.text = if (active) "正在擷取 ECG 訊號" else if (snapshot.eventCount > 0) "已擷取 ECG 訊號" else "尚未取得 ECG 訊號"
        detail.text = if (active) "雙指穩定接觸錶冠與側鍵，保持不動" else if (showQualifiedPreview)
            "量測已完成，請在手機查看結果。" else "未取得可分析的心電波形，請重新量測。"
        rhythmStatus.text = when {
            active -> "量測中"
            snapshot.eventCount <= 0 -> "手錶尚未收到訊號"
            !showQualifiedPreview -> "已收到訊號；尚無心律分析結果"
            else -> "已取得波形；手機尚未顯示心律結果"
        }
    }

    private fun shouldRenderPreview(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now < nextPreviewAtElapsedMs) return false
        if (!hasComputePolicySample || now - lastComputePolicyReadAtMs >= 10_000L) {
            val powerManager = getSystemService(android.os.PowerManager::class.java)
            val thermalStatus = runCatching { powerManager.currentThermalStatus }.getOrNull()
            val batteryPercent = getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
            previewIntervalMs = WearComputePolicy.ecgPreviewIntervalMillis(
                availableProcessors = Runtime.getRuntime().availableProcessors(),
                thermalStatus = thermalStatus,
                batteryPercent = batteryPercent
            )
            lastComputePolicyReadAtMs = now
            hasComputePolicySample = true
        }
        nextPreviewAtElapsedMs = now + previewIntervalMs
        return true
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun publishFinal(snapshot: RawVitalsProbe.Snapshot) {
        val now = System.currentTimeMillis()
        WearRawSensorPublisher.publish(
            context = this,
            snapshot = snapshot,
            final = true,
            chipPart = WearRawSensorPublisher.chipFor(RawVitalsProbe.Kind.ECG),
            featurePage = "ECG"
        )
        getSharedPreferences(WearRhythmActivity.PREFS, MODE_PRIVATE).edit()
            .remove("ecg_rhythm_title")
            .remove("ecg_rhythm_detail")
            .remove("ecg_rhythm_label")
            .remove("ecg_rhythm_rr_count")
            .remove("ecg_rhythm_bpm")
            .putInt("ecg_samples", snapshot.samples)
            .putLong("ecg_time", now)
            .apply()
    }

    private fun ecgButtonBackground(start: String, end: String) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(start), Color.parseColor(end))
    ).apply { cornerRadius = dp(8).toFloat() }

    private data class EcgQuality(
        val score: Int,
        val summary: String,
        val rrText: String,
        val rrMs: List<Double>
    )

    companion object {
        const val EXTRA_AUTO_START = "auto_start"
        private const val MIN_RR_FOR_FINISH = 8
        private const val MIN_POINTS_WITH_RR = 400
        private const val CHART_POINTS = 360
        private const val MAX_ANALYSIS_POINTS = 5_000
    }
}

private class EcgSignalView(context: android.content.Context) : View(context) {
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(37, 52, 62); strokeWidth = 1f }
    private val trace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(72, 238, 171); strokeWidth = 2.4f; style = Paint.Style.STROKE
    }
    private var samples = emptyList<Float>()

    fun setSamples(next: List<Float>) {
        samples = next
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(10, 18, 20))
        repeat(5) { index -> canvas.drawLine(0f, height * index / 4f, width.toFloat(), height * index / 4f, grid) }
        repeat(6) { index -> canvas.drawLine(width * index / 5f, 0f, width * index / 5f, height.toFloat(), grid) }
        if (samples.size < 2) return
        val finiteSamples = samples.filter(Float::isFinite)
        if (finiteSamples.size < 2) return
        val lower = finiteSamples.minOrNull() ?: return
        val upper = finiteSamples.maxOrNull() ?: return
        val span = (upper - lower).takeIf { abs(it) > 1e-6f } ?: return
        val path = android.graphics.Path()
        var connected = false
        samples.forEachIndexed { index, value ->
            if (!value.isFinite()) {
                connected = false
                return@forEachIndexed
            }
            val x = width * index / (samples.size - 1f)
            val y = height - ((value - lower) / span) * height
            if (!connected) path.moveTo(x, y) else path.lineTo(x, y)
            connected = true
        }
        canvas.drawPath(path, trace)
    }
}
