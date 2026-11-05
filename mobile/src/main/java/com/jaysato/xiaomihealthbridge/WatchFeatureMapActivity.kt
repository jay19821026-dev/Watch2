package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Honest feature map: what this app can do on Watch 2 Pro vs typical smartwatch /
 * Apple Watch wellness UX. Medical ECG/AF equivalence is explicitly out of scope.
 */
class WatchFeatureMapActivity : ComponentActivity() {
    data class Feature(
        val name: String,
        val category: String,
        val phone: String,
        val wear: String,
        val appleNote: String,
        val status: Status
    )

    enum class Status { IMPLEMENTED_UNVERIFIED, RESEARCH, PARTIAL, UNAVAILABLE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(36))
            setBackgroundColor(Color.rgb(10, 12, 18))
        }
        root.addView(label("晶片功能對照", 26f, Color.WHITE, true))
        root.addView(label(
            "依 Android Sensor API 端點與程式功能路徑整理；晶片名稱僅為候選映射，非完整 BOM。入口存在不代表本次有量到資料，需查看掃描結果與時間。",
            13f, Color.rgb(170, 180, 200), false
        ).also { it.setPadding(0, dp(8), 0, dp(18)) })

        features().groupBy { it.category }.forEach { (category, items) ->
            root.addView(label(category, 18f, Color.rgb(120, 220, 190), true).also {
                it.setPadding(0, dp(14), 0, dp(8))
            })
            items.forEach { feature -> root.addView(featureCard(feature)) }
        }

        root.addView(label(
            "Apple ECG：Series 4+／Ultra，約 30 秒單導，年齡與地區限制；AF 通知非連續監測，不適用已確診 AF 與未滿 22 歲（Apple 支援／IFU）。本 App 僅研究級波形與外部 raw→mV profile。",
            12f, Color.rgb(255, 190, 120), false
        ).also { it.setPadding(0, dp(20), 0, 0) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(10, 12, 18))
            addView(root)
        })
    }

    private fun featureCard(feature: Feature) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.rgb(22, 26, 36))
            setStroke(dp(1), statusColor(feature.status).and(0x55FFFFFF))
        }
        setPadding(dp(14), dp(12), dp(14), dp(12))
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        layoutParams = lp
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(label(feature.name, 15f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(label(statusLabel(feature.status), 12f, statusColor(feature.status), true))
        })
        addView(label("手機：${feature.phone}", 12f, Color.rgb(180, 190, 210), false))
        addView(label("手錶：${feature.wear}", 12f, Color.rgb(180, 190, 210), false))
        addView(label("對照：${feature.appleNote}", 11f, Color.rgb(140, 150, 170), false))
    }

    private fun features(): List<Feature> = listOf(
        Feature("PPG／光學心率", "MAX86178", "臨床實驗室＋原始詳情", "PPG／心率專屬頁", "光學錶連續 HR", Status.RESEARCH),
        Feature("SpO₂", "MAX86178", "原始詳情（不寫 %）", "SpO₂ 原始頁", "血氧頁 wellness", Status.PARTIAL),
        Feature("ECG＋心律研究", "MAX86178", "臨床實驗室＋rhythm_*", "本 App 獨立 ECG（非原廠）", "體驗可對標；醫療 AF 不可宣稱", Status.RESEARCH),
        Feature("體成分／BioZ", "MAX86178", "原始阻抗詳情", "BIOZ 專屬頁", "Samsung BIA／小米體成分", Status.RESEARCH),
        Feature("呼吸研究（BioZ）", "MAX86178", "bioz_respiration 詳情", "呼吸研究頁", "ADI BioZ 呼吸參考", Status.RESEARCH),
        Feature("離腕偵測", "MAX86178 候選端點", "佩戴狀態詳情", "離腕專屬頁", "自動暫停量測", Status.IMPLEMENTED_UNVERIFIED),
        Feature("溫度原始通道", "型號未確認", "原始資料", "溫度量測頁", "單位／整機校準未驗證", Status.RESEARCH),
        Feature("主板溫度原始通道", "型號未確認", "原始詳情", "主板溫度頁", "單位未驗證", Status.RESEARCH),
        Feature("加速度／陀螺儀", "BMI26x 候選端點", "原始詳情", "加速度／陀螺儀頁", "活動與姿態", Status.IMPLEMENTED_UNVERIFIED),
        Feature("計步／運動", "計步與運動 API", "HC 步數／運動分析", "計步＋運動專屬頁", "各廠運動 App", Status.IMPLEMENTED_UNVERIFIED),
        Feature("指南針／返航", "磁力計候選端點", "狀態說明", "指南針／返航 Activity", "戶外導航", Status.IMPLEMENTED_UNVERIFIED),
        Feature("氣壓／標準高度估算", "ICP101xx", "原始＋公式估算快照", "氣壓專屬頁", "非實際海拔／樓層／爬升", Status.PARTIAL),
        Feature("環境光／夜間", "LTR311 候選端點", "light 快照", "環境光＋手勢夜間頁", "自動亮度", Status.IMPLEMENTED_UNVERIFIED),
        Feature("LifeQ HR／OTT", "LifeQ", "心率／OTT 原始詳情", "心率＋OTT 頁", "連續 HR 算法來源", Status.RESEARCH),
        Feature("房顫醫療診斷", "醫療邊界", "不提供臨床 AF", "僅研究啟發式＋原廠入口", "需法規核准", Status.UNAVAILABLE),
        Feature("血壓／連續血糖／PAI", "醫療邊界", "無公開 API", "不破解 HAL", "無第三方介面", Status.UNAVAILABLE)
    )

    private fun statusLabel(status: Status) = when (status) {
        Status.IMPLEMENTED_UNVERIFIED -> "程式路徑存在・待實機驗收"
        Status.RESEARCH -> "研究"
        Status.PARTIAL -> "部分"
        Status.UNAVAILABLE -> "不可用"
    }

    private fun statusColor(status: Status) = when (status) {
        Status.IMPLEMENTED_UNVERIFIED -> Color.rgb(255, 190, 90)
        Status.RESEARCH -> Color.rgb(90, 190, 255)
        Status.PARTIAL -> Color.rgb(255, 190, 90)
        Status.UNAVAILABLE -> Color.rgb(255, 120, 120)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1.05f)
        gravity = Gravity.START
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
