package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Apple Health Browse / Samsung Health category list style.
 */
class HealthBrowseActivity : ComponentActivity() {
    data class Item(
        val title: String,
        val subtitle: String,
        val color: Int,
        val open: (HealthBrowseActivity) -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val focus = intent.getStringExtra("category")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(48))
            setBackgroundColor(Color.rgb(242, 242, 247))
        }
        root.addView(TextView(this).apply {
            text = "瀏覽"; textSize = 34f; setTextColor(Color.BLACK); setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "依健康主題查看已同步資料與量測入口。"
            textSize = 14f; setTextColor(Color.rgb(90, 90, 100))
            setPadding(0, dp(6), 0, dp(18))
        })

        val items = listOf(
            Item("心律", "心率 · ECG · HRV · 心律通知", Color.rgb(255, 59, 48)) {
                it.startActivity(Intent(it, IrregularRhythmActivity::class.java))
            },
            Item("心率歷史", "日／週／月／年趨勢", Color.rgb(255, 69, 58)) {
                it.startActivity(Intent(it, MetricDetailActivity::class.java).putExtra("metric", "heart_rate"))
            },
            Item("ECG／心律通知", "研究用節律參考 · 非醫療診斷", Color.rgb(52, 199, 89)) {
                it.startActivity(Intent(it, IrregularRhythmActivity::class.java))
            },
            Item("HRV／壓力", "RMSSD · nRMSSD 研究", Color.rgb(175, 82, 222)) {
                it.startActivity(Intent(it, RawSensorDetailActivity::class.java).putExtra("kind", "hrv_research"))
            },
            Item("血氧", "SpO₂ 紀錄與原始通道", Color.rgb(10, 132, 255)) {
                val prefs = RawSensorStore.open(it)
                val counts = Spo2ChannelSelectionPolicy.KINDS.associateWith { kind ->
                    RawSensorPayloadPolicy.displayPresenceCount(
                        prefs.getInt("${kind}_count_contract_version", 0),
                        prefs.getInt("${kind}_raw_value_count", -1).takeIf { count -> count >= 0 },
                        prefs.getInt("${kind}_event_count", 0),
                        prefs.getInt("${kind}_preview_point_count", 0),
                        prefs.getInt("${kind}_samples", 0)
                    )
                }
                val times = Spo2ChannelSelectionPolicy.KINDS.associateWith { kind ->
                    prefs.getLong("${kind}_time", 0L)
                }
                val selected = Spo2ChannelSelectionPolicy.selectForStoredData(null, counts, times) ?: "spo2_raw"
                it.startActivity(Intent(it, RawSensorDetailActivity::class.java).putExtra("kind", selected))
            },
            Item("睡眠", "總時長 · 分期誠實標示", Color.rgb(94, 92, 230)) {
                it.startActivity(Intent(it, SleepSummaryActivity::class.java))
            },
            Item("活動", "步數 · 運動 · 卡路里", Color.rgb(52, 199, 89)) {
                it.startActivity(Intent(it, WorkoutAnalysisActivity::class.java))
            },
            Item("身體", "溫度原始值 · BioZ · 體重", Color.rgb(255, 149, 0)) {
                it.startActivity(Intent(it, ClinicalLabActivity::class.java))
            },
            Item("壓力／恢復", "準備度與健康結論", Color.rgb(48, 209, 88)) {
                it.startActivity(Intent(it, HealthConclusionActivity::class.java))
            },
            Item("行動能力", "氣壓高度 · 指南針", Color.rgb(90, 200, 250)) {
                it.startActivity(Intent(it, RawSensorDetailActivity::class.java).putExtra("kind", "pressure"))
            },
            Item("血壓", "外部袖帶紀錄 · 參考分類", Color.rgb(255, 45, 85)) {
                it.startActivity(Intent(it, OpticalBpActivity::class.java))
            },
            Item("聽力／噪音", "手錶量測的噪音紀錄與校正狀態", Color.rgb(255, 204, 0)) {
                it.startActivity(Intent(it, NoiseSummaryActivity::class.java))
            },
            Item("他錶對照", "Apple／Samsung／Google 槽位", Color.rgb(100, 210, 255)) {
                it.startActivity(Intent(it, CompetitorParityActivity::class.java))
            },
            Item("血糖", "無 CGM／公開 API", Color.rgb(142, 142, 147)) {
                it.startActivity(Intent(it, UnavailableFeatureActivity::class.java).putExtra("feature", "glucose"))
            }
        )

        val query = when (focus) {
            "sleep" -> "睡眠"
            "heart" -> "心律"
            else -> focus
        }
        items.filter { query == null || it.title.contains(query, ignoreCase = true) }
            .ifEmpty { items }
            .forEach { item ->
                root.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = dp(16).toFloat()
                        setColor(Color.WHITE)
                    }
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
                    isClickable = true
                    setOnClickListener { item.open(this@HealthBrowseActivity) }
                    addView(TextView(context).apply {
                        text = item.title; textSize = 18f; setTextColor(item.color)
                        setTypeface(typeface, Typeface.BOLD)
                    })
                    addView(TextView(context).apply {
                        text = item.subtitle; textSize = 13f; setTextColor(Color.rgb(110, 110, 120))
                        setPadding(0, dp(4), 0, 0)
                    })
                })
            }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247))
            addView(root)
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
