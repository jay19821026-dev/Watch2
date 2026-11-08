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
                text = "研究級參考，非醫療診斷、非原廠 AF。胸痛／暈厥請就醫。"
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
        startButton.text = if (snapshot.eventCount > 0) "繼續收集（新區段）" else "重新量測"
        headline.text = when {
            snapshot.eventCount <= 0 -> "尚無事件"
            snapshot.acquisitionStopReason == "time_limit_reached" -> "ECG 研究解碼完成 · 時間／單位未校準"
            snapshot.acquisitionStopReason == "signal_stalled" -> "訊號中斷 · 保留部分資料"
            auto -> "量測完成"
            else -> "已手動結束"
        }
        if (snapshot.eventCount <= 0) detail.text = "未收到 ECG 事件。請雙指同時按住錶冠與側鍵，錶帶貼合，重試。"
        when (snapshot.acquisitionStopReason) {
            "no_callback_timeout" -> detail.append("\n啟動後 8 秒未收到感測事件，已停止以節省電量。")
            "signal_stalled" -> detail.append("\n感測事件中斷，已停止並保留目前資料。")
            "time_limit_reached" -> detail.append("\n本區段已結束並保存原始事件；時間戳未驗證，波形以索引軸呈現。不輸出 bpm、RR 或疾病判定。按「繼續收集」開始下一區段。")
        }
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
        val decoded = EcgRawEventDecoder.decode(rows)
        val structureConclusion = EcgEventStructureInterpreter.interpret(rows.size, decoded.layoutComparison)
        val eventPreview = EcgEventPreview.oemPacketCandidates(
            snapshot.eventRawValueRows, snapshot.ecgEventContactStates, snapshot.eventTimestampsNs, snapshot.eventCount
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
        headline.text = if (active) "量測中 · ${snapshot.eventCount} 個 Sensor API 事件" else "已保存 ${snapshot.eventCount} 個原始事件"
        val layout = decoded.layoutComparison
        detail.text = buildString {
            append("欄位映射：未驗證（${decoded.status}）；原始保存：${snapshot.eventRawValuesStatus}。近期預覽 ${rows.size} 列 × 16 欄 = ${decoded.inspectedFieldValueCount} 個欄位數值；前 10 欄假設候選 ${decoded.candidateValueCount} 個，不是已解碼 ECG 樣本。\n")
            append(if (showQualifiedPreview) "圖表：符合已觀察封包條件的候選值。\n" else "圖表：原始事件第 1 欄數值（${rawFieldPreview.size} 點）；未符合封包條件，仍可查看已收到的數值。\n")
            append("原廠 APK 對照：原廠程式讀取感測器 65573、每列前 10 欄，並在第 11 欄（索引 10）為 1000 且離腕感測器回報配戴時才交由原廠演算法處理。近期 ${EcgEventPreview.oemMarkerCount(rows)}/${rows.size} 列有 1000 標記；本 APP 對齊離腕事件時間後，${snapshot.ecgEventContactStates.takeLast(rows.size).count { it == 1 }} 列有近期配戴狀態。離腕偵測不等於電極接觸，不作接觸品質、mV 或節律判斷。\n")
            append("原廠量測略過前 ${EcgEventPreview.OEM_OBSERVED_WARMUP_EVENTS} 個 ECG 回呼；本 APP 保留所有原始事件，只在研究預覽略過這段起始資料。預覽另要求事件時間戳遞增，超過 1 秒的事件間隔會斷開畫線；這是顯示規則，不是原廠 ADC 取樣率。手機 Mi Fitness 的正式 ECG 紀錄另有報告取樣頻率與至少 25 秒的收件條件，不能套用到這些 Sensor API 原始封包。\n")
            append("排列假設相鄰差絕對中位數：列優先 ${layout?.rowMajorMedianAbsoluteDelta?.let { "%.3f".format(it) } ?: "—"}；欄優先 ${layout?.columnMajorMedianAbsoluteDelta?.let { "%.3f".format(it) } ?: "—"}。同列 ${layout?.withinRowMedianAbsoluteDelta?.let { "%.3f".format(it) } ?: "—"}；跨列邊界 ${layout?.rowBoundaryMedianAbsoluteDelta?.let { "%.3f".format(it) } ?: "—"}。只是數值排列比較，不驗證欄位映射。\n")
            append("第 12 欄（索引 11）僅作計數器假設檢查：+10 ${layout?.counterPlusTenTransitions ?: 0} 次、重置／重複 ${layout?.counterResetTransitions ?: 0} 次、其他遞增 ${layout?.otherCounterTransitions ?: 0} 次；不據此認定語義。\n")
            append("封包結構結論：${structureConclusion.headline}\n")
            structureConclusion.details.forEach { append("· $it\n") }
            append("時間戳、ADC 取樣時鐘及振幅單位未知；保留原始事件供重播驗證。")
        }
        rhythmStatus.text = when {
            active -> "研究結果：量測中 · 已收到 ${snapshot.eventCount} 個原始事件"
            snapshot.eventCount <= 0 -> "研究結論：無法判定（未收到 ECG 事件，請重新量測）"
            else -> "研究結論：無法判定節律 · ${snapshot.eventCount} 個原始事件／${snapshot.samples} 個已解碼樣本；欄位與取樣時鐘尚未驗證"
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
