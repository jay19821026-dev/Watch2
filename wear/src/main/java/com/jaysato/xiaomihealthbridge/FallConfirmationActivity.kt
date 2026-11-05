package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class FallConfirmationActivity : ComponentActivity() {
    private var timer: CountDownTimer? = null
    private lateinit var countdown: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val magnitude = intent.getDoubleExtra(EXTRA_MAGNITUDE, 0.0)
        WearUi.installStatic(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18)); setBackgroundColor(Color.BLACK)
            addView(text("偵測到可能跌倒", 23f, Color.WHITE))
            addView(text("衝擊 ${"%.1f".format(magnitude)} m/s²\n你還好嗎？", 14f, Color.LTGRAY))
            addView(text("30", 34f, Color.rgb(255, 95, 115)).also { countdown = it })
            addView(Button(context).apply {
                text = "我沒事"; isAllCaps = false; setOnClickListener {
                    WearSafetyAlerts.cancelFall(this@FallConfirmationActivity); finish()
                }
            }, LinearLayout.LayoutParams(-1, dp(52)))
            addView(Button(context).apply {
                text = "呼叫緊急服務"; isAllCaps = false; setOnClickListener { openEmergencyDialer() }
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
            addView(text("本功能是消費級輔助，可能誤判且不能保證偵測所有跌倒。", 10f, Color.rgb(255, 190, 120)))
        })
        timer = object : CountDownTimer(30_000L, 1_000L) {
            override fun onTick(remaining: Long) { countdown.text = "${remaining / 1000}" }
            override fun onFinish() { openEmergencyDialer() }
        }.start()
    }

    override fun onDestroy() { timer?.cancel(); super.onDestroy() }

    private fun openEmergencyDialer() {
        timer?.cancel()
        WearSafetyAlerts.publish(this, "fall_sos_requested", 1.0, System.currentTimeMillis())
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        finish()
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER; setPadding(0, dp(7), 0, dp(7))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { const val EXTRA_MAGNITUDE = "magnitude" }
}
