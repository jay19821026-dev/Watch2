package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/** Explicit, user-tapped links to exported OEM screens; never changes OEM feature flags. */
class WearOemFeaturesActivity : ComponentActivity() {
    private data class Entry(val title: String, val className: String, val note: String)

    private val entries = listOf(
        Entry("心率", "com.xiaomi.wear.fitness.health.heartrate.view.HeartRateMainActivity", "原廠量測與歷史"),
        Entry("血氧", "com.xiaomi.wear.fitness.health.spo2.activity.MainActivity", "原廠百分比與啟用狀態"),
        Entry("心電圖 ECG", "com.xiaomi.wear.fitness.health.ecg.EcgMainTileActivity", "若有 ECG 卡片，請先在手機 Mi Fitness 依原廠流程開通"),
        Entry("體成分", "com.xiaomi.wear.fitness.health.bodycomposition.activity.BodyCompositionActivity", "原廠 BioZ 模型與條件"),
        Entry("體溫", "com.xiaomi.wear.fitness.health.temperature.TemperatureMainActivity", "原廠溫度顯示"),
        Entry("壓力", "com.xiaomi.wear.fitness.health.stress.StressMainActivity", "原廠壓力演算法"),
        Entry("睡眠", "com.xiaomi.wear.fitness.health.sleep.SleepMainActivity", "原廠睡眠階段與歷史"),
        Entry("呼吸練習", "com.xiaomi.wear.fitness.health.breath.activity.BreathMainActivity", "原廠引導練習")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(28))
            setBackgroundColor(Color.BLACK)
        }
        content.addView(label("原廠功能入口", 20, Color.WHITE), LinearLayout.LayoutParams(-1, dp(42)))
        content.addView(label(
            "點擊後由小米健康決定能否使用；此頁不修改原廠啟用狀態。", 11, Color.rgb(170, 180, 198)
        ))
        val status = label("選擇一項功能查看原廠實際畫面。", 11, Color.rgb(255, 200, 125))
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(10) })
        entries.forEach { entry ->
            content.addView(Button(this).apply {
                text = entry.title
                contentDescription = "查看原廠${entry.title}功能"
                textSize = 13f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(35, 45, 64)); cornerRadius = dp(14).toFloat()
                }
                setOnClickListener {
                    status.text = runCatching {
                        startActivity(Intent().setClassName("com.xiaomi.wear.fitness", entry.className))
                        "已請求開啟${entry.title}；請以原廠畫面確認實際狀態。"
                    }.getOrElse { error ->
                        "${entry.title}無法開啟（${error.javaClass.simpleName}）；可能未安裝或此版本不允許外部啟動。"
                    }
                }
            }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
            content.addView(label(entry.note, 10, Color.rgb(140, 150, 170)))
        }
        WearUi.installScrollable(this, content)
    }

    private fun label(value: String, size: Int, color: Int) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.CENTER
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
