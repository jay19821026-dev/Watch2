package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/** Dedicated settings page for gesture / night power — was only a toggle on the home screen. */
class WearGestureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("device_preferences", MODE_PRIVATE)
        if (!prefs.contains("gesture_night_mode")) {
            prefs.edit().putBoolean("gesture_night_mode", true).apply()
        }
        lateinit var status: TextView
        lateinit var toggle: Button
        fun refresh() {
            val on = prefs.getBoolean("gesture_night_mode", true)
            status.text = if (on) "目前：已啟用" else "目前：已關閉"
            toggle.text = if (on) "關閉手勢／夜間省電" else "啟用手勢／夜間省電"
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = "GESTURE / NIGHT"; textSize = 10f; letterSpacing = .14f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(177, 143, 255))
            })
            addView(TextView(context).apply {
                text = "手勢與夜間省電"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setPadding(0, dp(6), 0, dp(10))
            })
            addView(TextView(context).apply {
                text = "抬腕／翻腕手勢與夜間亮度策略。不會在背景常駐高頻取樣。"
                textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.rgb(170, 180, 200))
            })
            addView(TextView(context).apply {
                textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setPadding(0, dp(18), 0, dp(12))
            }.also { status = it })
            addView(Button(context).apply {
                isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#4B477B"), Color.parseColor("#2B294A"))
                ).apply { cornerRadius = dp(8).toFloat() }
                setOnClickListener {
                    val next = !prefs.getBoolean("gesture_night_mode", true)
                    prefs.edit().putBoolean("gesture_night_mode", next).apply()
                    refresh()
                }
            }.also { toggle = it }, LinearLayout.LayoutParams(-1, dp(50)))
            addView(TextView(context).apply {
                text = "本程式提供參考，非醫療用途。"
                textSize = 11f; setTextColor(Color.rgb(255, 190, 120)); setPadding(0, dp(16), 0, 0)
            })
        }
        refresh()
        WearUi.installScrollable(this, content)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
