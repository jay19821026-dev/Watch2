package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.text.DateFormat
import java.util.Date

class WearReadinessActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prefs = getSharedPreferences(WearInsightsListenerService.PREFS, MODE_PRIVATE)
        val readiness100 = WearInsightPacketPolicy.compositeScoreForDisplay(prefs.getInt("readiness", -1))
        val readiness10 = readiness100?.let { ((it + 5) / 10).coerceIn(0, 10) } ?: -1
        val status = when (readiness10) {
            in 0..3 -> "規則指標較低"
            in 4..5 -> "規則指標中低"
            in 6..8 -> "規則指標中等"
            in 9..10 -> "規則指標較高"
            else -> "複合分數目前停用"
        }
        val color = when (readiness10) {
            in 0..3 -> "#FF5F57"
            in 4..5 -> "#FFB340"
            in 6..8 -> "#34C759"
            in 9..10 -> "#30D5C8"
            else -> "#596273"
        }
        val sleep = prefs.getFloat("sleep_hours", Float.NaN)
        val sleepSession = prefs.getFloat("sleep_session_hours", Float.NaN)
        val sleepStagesComplete = prefs.getBoolean("sleep_stages_complete", false)
        val temp = prefs.getFloat("temperature_deviation", Float.NaN)
        val timestamp = prefs.getLong("timestamp", 0L)
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        fun sourceTime(key: String): String = prefs.getLong(key, 0L).takeIf { it > 0L }
            ?.let { dateFormat.format(Date(it)) } ?: "時間未提供"
        val sleepTimeLabel = if (prefs.getBoolean("sleep_inferred", false)) {
            "心率推估樣本區間 ${sourceTime("sleep_inference_start_at")} 至 ${sourceTime("sleep_inference_end_at")}"
        } else "睡眠紀錄結束 ${sourceTime("sleep_session_end_at")}"
        val trainingWindow = "${sourceTime("training_window_start_at")} 至 ${sourceTime("training_window_end_at")}"
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(18), dp(16), dp(28)); setBackgroundColor(Color.BLACK)
            addView(text("健康資料摘要", 10f, Color.rgb(100, 210, 255)))
            addView(text("個別觀測與訓練紀錄", 19f, Color.WHITE))
            addView(TextView(context).apply {
                text = if (readiness10 >= 0) "$readiness10" else "—"
                textSize = 48f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor(color), Color.parseColor("#18191D"))).apply { shape = GradientDrawable.OVAL }
            }, LinearLayout.LayoutParams(dp(115), dp(115)).apply { topMargin = dp(12) })
            addView(text(status, 19f, Color.parseColor(color)))
            addView(card("恢復／電量／壓力", "未提供未驗證複合分數"))
            val sleepLabel = when {
                prefs.getBoolean("sleep_inferred", false) -> "低心率跨度"
                sleepStagesComplete -> "睡眠時長"
                else -> "睡眠 session"
            }
            val sleepValue = when {
                sleep.isFinite() -> "%.1f 小時${if (prefs.getBoolean("sleep_inferred", false)) "（非睡眠紀錄）" else ""}".format(sleep)
                sleepSession.isFinite() -> "%.1f 小時（session時段，非實際睡眠）".format(sleepSession)
                else -> "—"
            }
            addView(card(sleepLabel, sleepValue))
            addView(card("訓練負荷", WearReadinessPresentation.trainingLoad(prefs.getInt("training_load", -1))))
            addView(card("腕溫偏移", if (temp.isFinite()) "%+.1f °C".format(temp) else "—"))
            addView(text(if (timestamp > 0) "手機分析更新 ${dateFormat.format(Date(timestamp))} · 信心度 ${prefs.getString("confidence", "—")}" else "請在手機開啟健康中心更新分析", 10f, Color.LTGRAY))
            addView(text("靜息心率時間：${sourceTime("rhr_measured_at")} · HRV 時間：${sourceTime("hrv_measured_at")} · $sleepTimeLabel", 10f, Color.LTGRAY))
            addView(text("腕溫來源時間：${sourceTime("temperature_measured_at")} · 訓練負荷區間：$trainingWindow", 10f, Color.LTGRAY))
            addView(text("血氧觀測區間：${sourceTime("oxygen_window_start_at")} 至 ${sourceTime("oxygen_window_end_at")} · 每日能量：${sourceTime("daily_energy_window_start_at")} 至 ${sourceTime("daily_energy_window_end_at")}", 10f, Color.LTGRAY))
            addView(text("Health Connect 查詢資料截止（非量測時間）：${sourceTime("source_data_cutoff_at")}", 10f, Color.LTGRAY))
            addView(text("目前不提供未具可追溯驗證的壓力、電量、恢復或準備度複合分數；活動卡路里不作訓練負荷。", 10f, Color.rgb(255, 190, 120)))
        }
        WearUi.installScrollable(this, body)
    }

    private fun card(title: String, value: String) = text("$title   $value", 14f, Color.WHITE).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply { setColor(Color.rgb(30, 31, 36)); cornerRadius = dp(12).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(7) }
    }
    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER; setPadding(0, dp(5), 0, dp(5))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

internal object WearReadinessPresentation {
    fun trainingLoad(value: Int): String =
        if (value >= 0) "$value AU" else "尚無有效訓練負荷資料"
}
