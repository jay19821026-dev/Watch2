package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Blood-pressure record page. Only a real cuff reading is presented as a measurement.
 */
class OpticalBpActivity : ComponentActivity() {
    private lateinit var valueView: TextView
    private lateinit var compareView: TextView
    private lateinit var conclusionView: TextView
    private lateinit var adviceView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(48))
            setBackgroundColor(Color.rgb(242, 242, 247))
        }
        root.addView(TextView(this).apply {
            text = "血壓"; textSize = 34f; setTextColor(Color.BLACK); setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "僅顯示手動輸入的袖帶讀值；不以手錶心率或 PPG 推算血壓。"
            textSize = 14f; setTextColor(Color.rgb(90, 90, 100))
            setPadding(0, dp(6), 0, dp(16))
        })

        valueView = TextView(this).apply {
            textSize = 42f; setTextColor(Color.BLACK); setTypeface(typeface, Typeface.BOLD)
        }
        compareView = bodyText()
        conclusionView = bodyText()
        adviceView = bodyText()

        root.addView(card("最近袖帶讀值") { addView(valueView) })
        root.addView(card("參考篩查區間（單次讀值不等於診斷）") { addView(compareView) })
        root.addView(card("分析結論") { addView(conclusionView) })
        root.addView(card("建議") { addView(adviceView) })

        val systolic = EditText(this).apply {
            hint = "袖帶收縮壓 mmHg"; inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.BLACK); setHintTextColor(Color.GRAY)
        }
        val diastolic = EditText(this).apply {
            hint = "袖帶舒張壓 mmHg"; inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.BLACK); setHintTextColor(Color.GRAY)
        }
        root.addView(card("新增上臂袖帶讀值") {
            addView(systolic)
            addView(diastolic)
            addView(Button(context).apply {
                text = "儲存袖帶讀值並寫入 Health Connect"
                isAllCaps = false
                setOnClickListener {
                    val s = systolic.text.toString().toDoubleOrNull()
                    val d = diastolic.text.toString().toDoubleOrNull()
                    if (s == null || d == null) {
                        adviceView.text = "請輸入有效的收縮／舒張壓。"
                        return@setOnClickListener
                    }
                    getSharedPreferences(CUFF_PREFS, MODE_PRIVATE).edit()
                        .putFloat("manual_cuff_sbp", s.toFloat())
                        .putFloat("manual_cuff_dbp", d.toFloat())
                        .putLong("manual_cuff_time", System.currentTimeMillis())
                        .apply()
                    lifecycleScope.launch {
                        ManualEntryWriter(this@OpticalBpActivity).writeBloodPressure(s, d)
                            .onSuccess { refresh() }
                            .onFailure { adviceView.text = "寫入失敗：${it.message}" }
                    }
                    refresh()
                }
            })
        })

        root.addView(Button(this).apply {
            text = "為什麼手錶不能測血壓？"
            isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setColor(Color.rgb(255, 59, 48))
            }
            setOnClickListener {
                conclusionView.text = "Xiaomi Watch 2 Pro 沒有公開且經裝置級臨床驗證的血壓演算法。PPG 或心率不能直接換算 mmHg；請用經驗證的上臂袖帶血壓計，再把讀值記錄於此。"
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) }
        })
        root.addView(Button(this).apply {
            text = "重新計算分析"
            isAllCaps = false
            setOnClickListener { refresh() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }
        })
        root.addView(TextView(this).apply {
            text = OpticalBloodPressureApi.DISCLAIMER
            textSize = 12f; setTextColor(Color.rgb(140, 90, 40)); setPadding(0, dp(16), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247))
            addView(root)
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val cuffPrefs = getSharedPreferences(CUFF_PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val time = cuffPrefs.getLong("manual_cuff_time", 0L)
        val systolic = cuffPrefs.getFloat("manual_cuff_sbp", Float.NaN)
        val diastolic = cuffPrefs.getFloat("manual_cuff_dbp", Float.NaN)
        if (time <= 0L || now - time !in 0..(7 * 24 * 60 * 60 * 1000L) ||
            systolic !in 70f..250f || diastolic !in 35f..150f) {
            valueView.text = "—"
            compareView.text = ClinicalReferenceTables.AHA_SBP_NORMAL_MMHG.compare(null)
            conclusionView.text = "目前沒有 7 天內有效的袖帶紀錄；沒有使用手錶 PPG／心率估算血壓。"
            adviceView.text = "坐姿安靜休息 5 分鐘，使用合適袖帶的上臂血壓計量測；單次偏高請休息後重測。"
            return
        }
        val cat = OpticalBloodPressureApi.categorize(systolic.toDouble(), diastolic.toDouble())
        valueView.text = "%.0f / %.0f mmHg".format(systolic, diastolic)
        compareView.text = buildString {
            append("ACC/AHA 成人分類參考："); append(OpticalBloodPressureApi.categoryLabel(cat)); append('\n')
            append(ClinicalReferenceTables.AHA_SBP_NORMAL_MMHG.compare(systolic.toDouble())); append('\n')
            append(ClinicalReferenceTables.AHA_DBP_NORMAL_MMHG.compare(diastolic.toDouble()))
            append("\n量測時間："); append(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time)))
        }
        conclusionView.text = "這是手動輸入的一次袖帶讀值，不構成高血壓診斷；需依臨床建議以多次、不同日期的讀值評估。"
        adviceView.text = if (cat == OpticalBloodPressureApi.Category.HYPERTENSIVE_CRISIS) {
            "讀值達嚴重偏高範圍；請立即用合格血壓計重測。如伴胸痛、呼吸困難、視力或神經症狀，立即尋求急診。"
        } else {
            "若多日讀值反覆偏高，請攜帶紀錄諮詢醫療人員；不要依單次讀值自行調整藥物。"
        }
    }

    private fun bodyText() = TextView(this).apply {
        textSize = 14f; setTextColor(Color.rgb(60, 60, 67))
    }

    private fun card(title: String, build: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat(); setColor(Color.WHITE)
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        addView(TextView(context).apply {
            text = title; textSize = 15f; setTextColor(Color.rgb(28, 28, 30))
            setTypeface(typeface, Typeface.BOLD)
        })
        build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val CUFF_PREFS = "optical_bp_cuff"
    }
}
