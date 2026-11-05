package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class WearCalibrationActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var reference: EditText
    private var probe: RawVitalsProbe? = null
    private var latestRaw: Double? = null
    private var selected = RawVitalsProbe.Kind.PRESSURE

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(18), dp(16), dp(30))
            setBackgroundColor(Color.BLACK)
            addView(label("SENSOR CALIBRATION", 10f, Color.rgb(100, 210, 255)))
            addView(label("手錶校正中心", 22f, Color.WHITE))
            addView(label("使用同時間、同位置的外部參考儀器", 11f, Color.LTGRAY))
            addView(label("選擇通道", 12f, Color.LTGRAY).apply { setPadding(0, dp(12), 0, dp(4)) })
            addView(channelButton("氣壓", RawVitalsProbe.Kind.PRESSURE))
            addView(channelButton("環境光", RawVitalsProbe.Kind.LIGHT))
            addView(EditText(context).apply {
                hint = "參考值"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(100, 210, 255))
            }.also { reference = it }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
            addView(label("尚未量測", 12f, Color.WHITE).also { status = it }.apply { setPadding(0, dp(10), 0, dp(8)) })
            addView(actionButton("開始取得穩定讀值") { startProbe() })
            addView(actionButton("儲存校正") { save() })
            addView(actionButton("重設校正") {
                WearCalibrationStore.reset(this@WearCalibrationActivity)
                status.text = "已重設 App 校正；不會改寫原廠韌體"
            })
            addView(label("溫度只保留裝置原始值：通道單位與整機校準未驗證，因此不提供溫度校正。氣壓／環境光的 App 偏移也僅供研究，不改變原廠感測器。ECG、PPG、BioZ 不偽造醫療單位。", 11f, Color.rgb(255, 190, 120)).apply {
                setPadding(0, dp(14), 0, 0)
            })
        }
        WearUi.installScrollable(this, body)
    }

    override fun onStop() {
        probe?.stop(); probe = null
        super.onStop()
    }

    private fun startProbe() {
        probe?.stop(); latestRaw = null
        status.text = "量測中，請保持穩定…"
        probe = RawVitalsProbe(this, selected) { snapshot ->
            val raw = snapshot.average ?: snapshot.latest.firstOrNull() ?: return@RawVitalsProbe
            latestRaw = raw
            runOnUiThread {
                status.text = "原始 ${"%.4f".format(raw)}\n目前套用 ${"%.4f".format(WearCalibrationStore.apply(this, selected, raw))}"
            }
        }.also {
            if (!it.start()) status.text = "通道無法啟動"
        }
    }

    private fun save() {
        val raw = latestRaw
        val target = reference.text.toString().toDoubleOrNull()
        if (raw == null || target == null) {
            status.text = "請先取得讀值並輸入參考值"
            return
        }
        status.text = WearCalibrationStore.save(this, selected, raw, target)
            ?.let { "已儲存：$it\n研究／趨勢用途，非醫療校準" }
            ?: "參考值超出合理範圍，未儲存"
    }

    private fun channelButton(text: String, kind: RawVitalsProbe.Kind) = actionButton(text) {
        selected = kind; latestRaw = null; status.text = "已選擇 $text"
    }

    private fun actionButton(text: String, click: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; setTextColor(Color.WHITE); setOnClickListener { click() }
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(36, 48, 68), Color.rgb(21, 27, 40))).apply {
            cornerRadius = dp(8).toFloat()
        }
    }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) } }

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
