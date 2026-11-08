package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full health conclusions report: every sensor calculated vs clinical reference bands.
 */
class HealthConclusionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(48))
            setBackgroundColor(Color.rgb(242, 242, 247))
        }
        root.addView(TextView(this).apply {
            text = "健康結論"; textSize = 34f; setTextColor(Color.BLACK); setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "研究原始訊號與可對照生理數值分開呈現；不代表醫療診斷。"
            textSize = 14f; setTextColor(Color.rgb(90, 90, 100))
            setPadding(0, dp(6), 0, dp(14))
        })
        val acknowledgement = CheckBox(this).apply {
            text = "我了解以下內容僅供研究與健康參考，可能包含估算或不完整資料，不等同醫療診斷；異常或不適時仍須就醫。"
            textSize = 13f
            setTextColor(Color.rgb(45, 45, 55))
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(acknowledgement)
        val reveal = Button(this).apply {
            text = "勾選後查看健康結論"
            isAllCaps = false
            isEnabled = false
        }
        root.addView(reveal)
        val reportContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
        }
        root.addView(reportContainer)
        acknowledgement.setOnCheckedChangeListener { _, checked ->
            reveal.isEnabled = checked
            if (!checked) reportContainer.visibility = android.view.View.GONE
        }
        reveal.setOnClickListener {
            if (!acknowledgement.isChecked) return@setOnClickListener
            if (reportContainer.visibility == android.view.View.VISIBLE) return@setOnClickListener
            if (reportContainer.childCount > 0) {
                reportContainer.visibility = android.view.View.VISIBLE
                reveal.isEnabled = false
                return@setOnClickListener
            }
            val report = HealthConclusionEngine.build(this)
            if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_EXPORT_MATRIX, false)) {
                exportDebugMatrix(report)
            }
            reportContainer.visibility = android.view.View.VISIBLE
            reportContainer.addView(hero("已記錄的健康數值", "請看各項讀值與參考範圍；無效量測可重新進行。"))
            report.items.forEach { item -> reportContainer.addView(itemCard(item)) }
            reportContainer.addView(TextView(this).apply {
                text = report.disclaimer
                textSize = 12f; setTextColor(Color.rgb(140, 80, 60))
                setPadding(0, dp(16), 0, 0)
            })
            reveal.isEnabled = false
        }
        if (HealthConclusionDebugLaunchPolicy.shouldAutoReveal(
                BuildConfig.DEBUG,
                intent.getBooleanExtra(EXTRA_DEBUG_AUTO_REVEAL, false)
            )
        ) {
            acknowledgement.isChecked = true
            reveal.performClick()
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247))
            addView(root)
        })
    }

    private fun hero(title: String, detail: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Color.rgb(0, 122, 255))
        }
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        addView(TextView(context).apply {
            text = title; textSize = 18f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = detail; textSize = 13f; setTextColor(Color.rgb(220, 235, 255))
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun itemCard(item: HealthConclusionEngine.Item) = LinearLayout(this).apply {
        val display = ConsumerMetricPresentation.from(item)
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.WHITE)
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        val accent = when (item.level) {
            HealthConclusionEngine.Level.GOOD -> Color.rgb(52, 199, 89)
            HealthConclusionEngine.Level.WATCH -> Color.rgb(255, 149, 0)
            HealthConclusionEngine.Level.ATTENTION -> Color.rgb(255, 59, 48)
            HealthConclusionEngine.Level.RESEARCH -> Color.rgb(90, 200, 250)
            HealthConclusionEngine.Level.UNAVAILABLE -> Color.rgb(142, 142, 147)
        }
        addView(TextView(context).apply {
            text = display.title
            textSize = 15f; setTextColor(accent); setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = display.value; textSize = 28f; setTextColor(Color.BLACK); setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        })
        addView(TextView(context).apply {
            val recordedValue = item.valueText.substringBefore('%').trim().toDoubleOrNull()
            text = if (item.id == "spo2" && item.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL) {
                VitalsCardPresentationPolicy.oxygenInterpretation(recordedValue)
            } else if (item.id == "hr" && item.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL) {
                val bpm = item.valueText.substringBefore("bpm").trim().toDoubleOrNull()
                if (bpm == null) "尚無心率讀值" else VitalsCardPresentationPolicy.heartRateReference(bpm)
            } else if (item.level == HealthConclusionEngine.Level.RESEARCH || item.level == HealthConclusionEngine.Level.UNAVAILABLE) {
                "這次沒有可用的健康判讀；請重新量測。"
            } else when (item.id) {
                "hr" -> "成人靜息心率參考 60–100 bpm；活動時不使用此範圍判斷。"
                "spo2" -> "多數健康成人血氧常見參考 95–100%。"
                else -> when (item.level) {
                    HealthConclusionEngine.Level.GOOD -> "本次讀值落在參考範圍。"
                    HealthConclusionEngine.Level.WATCH -> "這項讀值建議再次觀察。"
                    HealthConclusionEngine.Level.ATTENTION -> "這項讀值偏離參考範圍，建議重測確認。"
                    else -> "請查看讀值與資料來源。"
                }
            }
            textSize = 14f; setTextColor(Color.rgb(28, 28, 30)); setPadding(0, dp(6), 0, 0)
        })
        addView(Button(context).apply {
            text = "查看數據與來源"; isAllCaps = false
            setOnClickListener {
                android.app.AlertDialog.Builder(this@HealthConclusionActivity)
                    .setTitle(item.title)
                    .setMessage("${item.valueText}\n\n${item.clinicalCompare}\n\n${item.conclusion}")
                    .setPositiveButton("知道了", null).show()
            }
        })
        if (HealthConclusionSummaryPolicy.shouldShowScore(item)) {
            addView(TextView(context).apply {
                text = item.score?.let { "參考分數 $it/100" }.orEmpty()
                textSize = 12f; setTextColor(accent); setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun levelLabel(level: HealthConclusionEngine.Level) = when (level) {
        HealthConclusionEngine.Level.GOOD -> "參考良好"
        HealthConclusionEngine.Level.WATCH -> "需觀察"
        HealthConclusionEngine.Level.ATTENTION -> "需留意"
        HealthConclusionEngine.Level.RESEARCH -> "研究級"
        HealthConclusionEngine.Level.UNAVAILABLE -> "無資料／不可用"
    }

    private fun riskLabel(level: HealthConclusionEngine.Level) = when (level) {
        HealthConclusionEngine.Level.GOOD -> "目前未見明顯異常"
        HealthConclusionEngine.Level.WATCH -> "需要持續觀察"
        HealthConclusionEngine.Level.ATTENTION -> "需要重新測量或進一步確認"
        HealthConclusionEngine.Level.RESEARCH -> "資料限制，尚不能判定"
        HealthConclusionEngine.Level.UNAVAILABLE -> "目前沒有足夠資料"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun exportDebugMatrix(report: HealthConclusionEngine.Report) {
        val items = JSONArray()
        report.items.forEach { item ->
            items.put(JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("value", item.valueText)
                .put("level", item.level.name)
                .put("conclusion", item.conclusion)
            )
        }
        val output = JSONObject()
            .put("schema", "health-conclusion-debug-matrix-v1")
            .put("generated_at_ms", System.currentTimeMillis())
            .put("headline", report.overallHeadline)
            .put("detail", report.overallDetail)
            .put("items", items)
        val directory = getExternalFilesDir("debug-export") ?: return
        File(directory, DEBUG_MATRIX_FILE).writeText(output.toString(2))
    }

    companion object {
        const val EXTRA_DEBUG_AUTO_REVEAL = "debug_auto_reveal"
        const val EXTRA_DEBUG_EXPORT_MATRIX = "debug_export_matrix"
        const val DEBUG_MATRIX_FILE = "health-conclusion-matrix.json"
    }
}

internal object HealthConclusionDebugLaunchPolicy {
    fun shouldAutoReveal(debugBuild: Boolean, requested: Boolean): Boolean = debugBuild && requested
}
