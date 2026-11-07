package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Apple / Samsung Galaxy / Google Pixel feature parity map vs this Watch 2 Pro bridge.
 */
class CompetitorParityActivity : ComponentActivity() {
    data class Row(
        val feature: String,
        val apple: String,
        val samsung: String,
        val google: String,
        val ours: String,
        val status: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = RawSensorStore.open(this@CompetitorParityActivity)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(40))
            setBackgroundColor(Color.rgb(8, 10, 16))
        }
        root.addView(label("他錶功能對照", 26f, Color.WHITE, true))
        root.addView(label(
            "對標 Apple Watch／Galaxy Watch／Pixel Watch 常見健康功能；本 App 僅公開 HAL＋文獻公式，非原廠演算法複製。",
            13f, Color.rgb(160, 170, 190), false
        ).also { it.setPadding(0, dp(8), 0, dp(14)) })

        rows().forEach { row ->
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(Color.rgb(20, 24, 34))
                }
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                addView(label(row.feature, 15f, Color.WHITE, true))
                addView(label("Apple：${row.apple}", 11f, Color.rgb(150, 160, 180), false))
                addView(label("Samsung：${row.samsung}", 11f, Color.rgb(150, 160, 180), false))
                addView(label("Google：${row.google}", 11f, Color.rgb(150, 160, 180), false))
                addView(label("本 App：${row.ours}", 12f, Color.rgb(100, 210, 255), false).also {
                    it.setPadding(0, dp(4), 0, 0)
                })
                addView(label(row.status, 12f, Color.rgb(120, 220, 160), true).also {
                    it.setPadding(0, dp(4), 0, 0)
                })
            })
        }

        val formulaKinds = listOf("ppg", "hrv_research", "pressure", "ecg")
        root.addView(label("已同步文獻公式欄位", 18f, Color.rgb(255, 180, 120), true).also {
            it.setPadding(0, dp(16), 0, dp(8))
        })
        formulaKinds.forEach { kind ->
            val sourceKind = if (kind == "hrv_research") raw.getString("hrv_research_source_kind", "").orEmpty() else kind
            val measuredAt = if (kind == "hrv_research") raw.getLong("hrv_research_time", 0L)
                else raw.getLong("${sourceKind}_time", 0L)
            val hasOpticalMetrics = listOf("formula_pi_percent", "formula_pulse_bpm", "formula_ratio_r")
                .any { raw.contains("${kind}_$it") }
            val hasHrvMetrics = listOf("formula_rmssd_ms", "formula_nrmssd", "formula_sdnn_ms")
                .any { raw.contains("${kind}_$it") }
            val hasEnvironmentMetrics = listOf("formula_altitude_m", "formula_pressure_hpa")
                .any { raw.contains("${kind}_$it") }
            val eligibility = RawSensorFormulaDisplayPolicy.evaluate(
                kind = sourceKind,
                samples = if (kind == "hrv_research") raw.getInt("hrv_research_samples", 0)
                    else raw.getInt("${sourceKind}_raw_value_count", 0),
                status = raw.getString("${sourceKind}_status", "").orEmpty(),
                measuredAtMillis = measuredAt,
                timestampSource = raw.getString("${sourceKind}_timestamp_source", null),
                hrvTimestampSource = raw.getString("hrv_research_timestamp_source", null),
                sessionStartedAtMillis = raw.getLong("${sourceKind}_session_started_at_ms", 0L),
                nowMillis = System.currentTimeMillis(),
                qualityScore = raw.getInt("${sourceKind}_quality_score", 0),
                sampleRateHz = raw.getFloat("${sourceKind}_sample_rate", 0f),
                channelMappingVerified = raw.getBoolean("${sourceKind}_channel_mapping_verified", false),
                formulaPulseBpm = raw.getFloat("${sourceKind}_formula_pulse_bpm", Float.NaN).takeIf { it.isFinite() },
                hrvMeasuredAtMillis = raw.getLong("hrv_research_time", 0L),
                hrvSourceKind = raw.getString("hrv_research_source_kind", "").orEmpty(),
                hrvSampleCount = raw.getInt("hrv_research_samples", 0),
                hrvRrCount = raw.getInt("hrv_research_rr_count", 0),
                ecgResearchOnly = raw.getBoolean("ecg_rhythm_research_only", false),
                hasOpticalMetrics = hasOpticalMetrics,
                hasHrvMetrics = hasHrvMetrics,
                hasEnvironmentMetrics = hasEnvironmentMetrics,
                wearQualified = if (raw.contains("${sourceKind}_wear_qualified")) {
                    raw.getBoolean("${sourceKind}_wear_qualified", false)
                } else null,
                waveformEncoding = raw.getString("${sourceKind}_waveform_encoding", null)
            )
            val note = raw.getString("${kind}_formula_note", null)
            val pi = raw.getFloat("${kind}_formula_pi_percent", Float.NaN)
                .takeIf { eligibility.showOpticalMetrics }
            val rmssd = raw.getFloat("${kind}_formula_rmssd_ms", Float.NaN)
                .takeIf { eligibility.showHrvMetrics }
            val alt = raw.getFloat("${kind}_formula_altitude_m", Float.NaN)
                .takeIf { eligibility.showEnvironmentMetrics }
            val hasCachedFormula = listOf(
                "formula_pi_percent", "formula_pulse_bpm", "formula_ratio_r", "formula_rmssd_ms",
                "formula_nrmssd", "formula_sdnn_ms", "formula_altitude_m", "formula_pressure_hpa"
            )
                .any { raw.contains("${kind}_$it") }
            val line = CompetitorFormulaRowFormatter.format(
                kind = kind,
                piPercent = pi,
                rmssdMs = rmssd,
                altitudeM = alt,
                note = note,
                eligibility = eligibility,
                hasCachedFormula = hasCachedFormula
            )
            root.addView(label(line, 12f, Color.rgb(180, 190, 210), false).also {
                it.setPadding(0, 0, 0, dp(6))
            })
        }

        root.addView(Button(this).apply {
            text = "開啟外部袖帶血壓紀錄"
            isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#E85A6B"), Color.parseColor("#7A1F2C"))
            ).apply { cornerRadius = dp(12).toFloat() }
            setOnClickListener {
                startActivity(Intent(this@CompetitorParityActivity, OpticalBpActivity::class.java))
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })

        root.addView(Button(this).apply {
            text = "開啟晶片功能與資料"
            isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1F6FEB"), Color.parseColor("#123A7A"))
            ).apply { cornerRadius = dp(12).toFloat() }
            setOnClickListener {
                startActivity(Intent(this@CompetitorParityActivity, ChipFeatureHubActivity::class.java))
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8, 10, 16))
            addView(root)
        })
    }

    private fun rows() = listOf(
        Row("心率／區間", "連續 HR＋警示", "連續 HR＋警示", "Fitbit HR", "WHS＋光學通道＋專屬頁", "可用／研究"),
        Row("ECG", "單導＋AF 通知（核准區）", "單導＋AF", "ECG App", "ECG RESEARCH＋心律啟發式", "研究（非醫療 AF）"),
        Row("SpO₂", "Blood Oxygen", "血氧", "SpO₂", "原始通道＋PI；不寫未校準 %", "部分"),
        Row("HRV／壓力", "Vitals／HRV", "壓力·HRV", "壓力分數", "HRV 專屬頁：SDNN/RMSSD/nRMSSD/pNN50", "研究"),
        Row("體溫", "腕溫 overnight", "皮膚溫", "皮膚溫", "CT7117 體表／板溫頁", "研究"),
        Row("體成分", "無 BIA", "BIA 體成分", "無", "BioZ 原始（無 OEM Ω／體脂）", "研究"),
        Row("睡眠", "分期＋總時長", "分期＋血氧", "Fitbit 分期", "總時長專頁；分期誠實不可用", "部分"),
        Row("氣壓高度", "高度計", "氣壓", "氣壓", "ISA 高度公式＋專屬頁", "部分"),
        Row("指南針／運動", "指南針／健身", "運動／BIA", "Fitbit 運動", "指南針／返航／運動頁", "可用"),
        Row("血壓", "高血壓趨勢（部分區）", "袖帶校準 BP（核准區）", "趨勢／外接", "僅外部袖帶手動紀錄＋參考分類；手錶無血壓估算", "外部紀錄"),
        Row("血糖", "外接", "外接", "研究／外接", "無 CGM API；槽位誠實不可用", "不可用"),
        Row("聽力／噪音", "Noise／聽力健康", "環境噪音（部分）", "噪音／暴露", "手錶噪音頁＋參考校正", "部分"),
        Row("準備度", "Vitals／Recovery", "能量分數", "Readiness／Recovery", "手機洞察→手錶準備度", "研究"),
        Row("智慧省電", "低電模式", "省電／睡眠模式", "Battery saver", "手錶智慧省電調度 App", "可用"),
        Row("量測結束條件", "足夠資料／固定流程", "足夠資料", "足夠資料", "等到有數值才結束（取消秒數門檻）", "可用"),
    )

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.START
        setLineSpacing(dp(2).toFloat(), 1.05f)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
