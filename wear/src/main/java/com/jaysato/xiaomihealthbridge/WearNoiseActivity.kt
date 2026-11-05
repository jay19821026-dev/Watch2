package com.jaysato.xiaomihealthbridge

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

class WearNoiseActivity : ComponentActivity() {
    private lateinit var headline: TextView
    private lateinit var detail: TextView
    private lateinit var reference: EditText
    @Volatile private var running = false
    private var recorder: AudioRecord? = null
    private var latestDbfs = Double.NaN
    private var measuredSeconds = 0
    private var dose = 0.0
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) start() else detail.text = "需要麥克風權限" }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(18), dp(16), dp(28)); setBackgroundColor(Color.BLACK)
            addView(text("HEARING", 10f, Color.rgb(100, 210, 255)))
            addView(text("環境聲音", 22f, Color.WHITE))
            addView(text("— dBFS", 36f, Color.WHITE).also { headline = it })
            addView(text("尚未開始", 12f, Color.LTGRAY).also { detail = it })
            addView(action("開始／停止量測") { if (running) stop() else ensurePermission() })
            addView(EditText(context).apply {
                hint = "外部聲級計同步讀值 dBA"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(100, 210, 255))
            }.also { reference = it }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
            addView(action("以目前讀值校正") { calibrate() })
            addView(action("清除麥克風校正") {
                prefs().edit().clear().apply(); detail.text = "已清除；恢復只顯示 dBFS"
            })
            addView(text("未使用外部校準聲級計時，只顯示數位滿刻度 dBFS，不冒充 dBA。量測不儲存音訊。校正後的暴露時間採 NIOSH 85 dBA／8 小時、3 dB 交換率。", 10f, Color.rgb(255, 190, 120)))
        }
        WearUi.installScrollable(this, content)
    }

    override fun onStop() { stop(); super.onStop() }

    private fun ensurePermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        if (running) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val sampleRate = 16_000
        val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) { detail.text = "麥克風格式不可用"; return }
        val next = runCatching {
            AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, sampleRate * 2))
        }.getOrElse {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, sampleRate * 2))
        }
        if (next.state != AudioRecord.STATE_INITIALIZED) { next.release(); detail.text = "麥克風無法初始化"; return }
        recorder = next; running = true; measuredSeconds = 0; dose = 0.0
        next.startRecording()
        thread(name = "wear-noise-meter", isDaemon = true) {
            val buffer = ShortArray(sampleRate)
            while (running) {
                val count = next.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) continue
                val squareMean = buffer.take(count).sumOf { it.toDouble() * it.toDouble() } / count
                val rms = sqrt(squareMean)
                val dbfs = if (rms > 0.0) 20.0 * log10(rms / Short.MAX_VALUE) else -120.0
                latestDbfs = dbfs.coerceIn(-120.0, 0.0); measuredSeconds++
                val offset = prefs().getFloat(KEY_OFFSET, Float.NaN).toDouble()
                val dba = if (offset.isFinite()) latestDbfs + offset else Double.NaN
                if (dba.isFinite()) HearingExposure.dosePercent(dba, 1.0 / 60.0)?.let { dose += it }
                runOnUiThread { render(latestDbfs, dba) }
            }
        }
    }

    private fun stop() {
        running = false
        runCatching { recorder?.stop() }; recorder?.release(); recorder = null
        if (latestDbfs.isFinite() && measuredSeconds > 0) publishNoise()
    }

    private fun publishNoise() {
        val offset = prefs().getFloat(KEY_OFFSET, Float.NaN).toDouble()
        val dba = if (offset.isFinite()) latestDbfs + offset else Double.NaN
        val now = System.currentTimeMillis()
        val request = com.google.android.gms.wearable.PutDataMapRequest.create("/raw-sensor/noise/$now").apply {
            dataMap.putString("kind", "noise")
            dataMap.putString("label", "環境噪音")
            dataMap.putInt("samples", measuredSeconds)
            dataMap.putDouble("average", latestDbfs)
            dataMap.putDouble("noise_dbfs", latestDbfs)
            if (dba.isFinite()) dataMap.putDouble("noise_dba", dba)
            dataMap.putBoolean("final", true)
            dataMap.putString("status", if (dba.isFinite()) "calibrated_dba" else "dbfs_only")
            dataMap.putString("summary", if (dba.isFinite()) "%.1f dBA".format(dba) else "%.1f dBFS".format(latestDbfs))
            dataMap.putString("feature_page", "NOISE")
            dataMap.putLong("timestamp", now)
        }.asPutDataRequest().setUrgent()
        com.google.android.gms.wearable.Wearable.getDataClient(this).putDataItem(request)
    }

    private fun render(dbfs: Double, dba: Double) {
        headline.text = if (dba.isFinite()) "%.1f dBA*".format(dba) else "%.1f dBFS".format(dbfs)
        detail.text = if (dba.isFinite()) {
            val minutes = HearingExposure.allowedMinutesAt(dba)
            "校正估值 · ${if (dba >= 85) "請降低音量或使用護耳" else "目前低於 NIOSH 85 dBA 職業門檻"}\n" +
                "本次劑量 ${"%.3f".format(dose)}% · 容許時間 ${minutes?.let { "%.0f 分".format(it) } ?: "未限制"}"
        } else "原始數位音量 · 已分析 $measuredSeconds 秒\n輸入同步聲級計值才能換算 dBA"
    }

    private fun calibrate() {
        val target = reference.text.toString().toDoubleOrNull()
        if (!latestDbfs.isFinite() || target == null || target !in 30.0..130.0) {
            detail.text = "請先量測並輸入 30–130 dBA 參考值"; return
        }
        val offset = target - latestDbfs
        prefs().edit().putFloat(KEY_OFFSET, offset.toFloat()).putLong("time", System.currentTimeMillis()).apply()
        detail.text = "已儲存 offset ${"%+.2f".format(offset)} dB\n僅對目前裝置／佩戴方向有效"
    }

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)
    private fun action(label: String, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(Color.WHITE); setOnClickListener { click() }
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(36, 48, 68), Color.rgb(21, 27, 40))).apply { cornerRadius = dp(8).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) }
    }
    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { private const val PREFS = "microphone_calibration"; private const val KEY_OFFSET = "db_offset" }
}
