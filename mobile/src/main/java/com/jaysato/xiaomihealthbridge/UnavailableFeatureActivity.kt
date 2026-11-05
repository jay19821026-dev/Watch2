package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Feature slot pages for capabilities that competitors show but this device cannot provide.
 * UI still mirrors the competitor slot; values stay honest.
 */
class UnavailableFeatureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val feature = intent.getStringExtra("feature") ?: "blood_pressure"
        val (title, apple, samsung, google, reason) = when (feature) {
            "glucose" -> Spec(
                "血糖",
                "外接／研究趨勢",
                "部分生態／外接",
                "Fitbit 研究／外接",
                "Watch 2 Pro 無連續血糖感測器與公開 CGM API；不偽造血糖曲線。"
            )
            "noise" -> Spec(
                "聽力／噪音",
                "Noise App／聽力健康",
                "環境噪音（部分機型）",
                "Fitbit／Pixel 噪音",
                "本 App 在手錶提供噪音量測頁（麥克風 dBFS→參考校正）；手機端無常駐分貝歷史 API，請在手錶 Apps → 噪音量測。"
            )
            "sleep_stages" -> Spec(
                "睡眠分期",
                "REM／核心／深睡",
                "多階段＋血氧",
                "Fitbit 分期",
                "目前僅總睡眠時長可同步；無經驗證的 REM／深睡分期 API，不推算假分期。"
            )
            else -> Spec(
                "血壓",
                "高血壓趨勢（部分區）",
                "袖帶校準 BP（核准區；Health Sensor SDK 不公開）",
                "趨勢／外接",
                "此裝置無核准袖帶／光學血壓醫療 API。請改開「光學血壓」研究頁：接 PPG 估計＋ACC/AHA 對照與建議；並可用外接血壓計校正。"
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(40))
            setBackgroundColor(Color.rgb(242, 242, 247))
        }
        root.addView(TextView(this).apply {
            text = title; textSize = 34f; setTextColor(Color.BLACK); setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(card("目前狀態", "此功能目前無法從手錶或已連線資料來源取得", Color.rgb(255, 149, 0)))
        root.addView(card("Apple Watch", apple, Color.rgb(28, 28, 30)))
        root.addView(card("Galaxy Watch", samsung, Color.rgb(28, 28, 30)))
        root.addView(card("Pixel／Fitbit", google, Color.rgb(28, 28, 30)))
        root.addView(card("為什麼沒有數值", reason, Color.rgb(28, 28, 30)))
        root.addView(TextView(this).apply {
            text = "UI 對齊競品「有此功能入口」，但不偽造醫療級讀值。"
            textSize = 13f; setTextColor(Color.rgb(90, 90, 100))
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247))
            addView(root)
        })
    }

    private data class Spec(
        val title: String,
        val apple: String,
        val samsung: String,
        val google: String,
        val reason: String
    )

    private fun card(title: String, body: String, titleColor: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.WHITE)
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        addView(TextView(context).apply {
            text = title; textSize = 15f; setTextColor(titleColor); setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = body; textSize = 14f; setTextColor(Color.rgb(60, 60, 67))
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
