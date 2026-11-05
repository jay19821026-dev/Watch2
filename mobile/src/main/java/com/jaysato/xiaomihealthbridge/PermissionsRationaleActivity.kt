package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(text("‹  返回", 16f, HealthVisuals.blue).apply {
                minHeight = padding * 2
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { finish() }
            })
            addView(text("你的資料，\n由你掌握。", 30f, Color.WHITE).apply {
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setPadding(0, padding, 0, padding)
            })
            addView(text("HEALTH CONNECT · 隱私與權限", 11f, HealthVisuals.secondary))
            addView(text(
                "本 App 只在你的 Xiaomi Watch 2 Pro、手機與 Health Connect 之間處理健康資料。\n\n" +
                    "讀取權限用於顯示最近七天的同步結果；寫入權限用於保存手錶量測及你主動輸入的可信外部量測。\n\n" +
                    "資料不會上傳到本 App 自有伺服器、不會出售或用於廣告。你可以隨時在 Health Connect 撤銷權限。\n\n" +
                    "本 App 不破解 Mi Fitness、不推算血壓、ECG、睡眠階段或其他缺少來源的資料。",
                15f,
                Color.parseColor("#D9E2F2")
            ).apply {
                setPadding(padding, padding, padding, padding)
                setLineSpacing(6f, 1f)
                background = HealthVisuals.panel(context)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = padding })
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(HealthVisuals.background)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets)
                    .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.START
    }
}
