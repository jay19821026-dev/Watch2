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
            in 0..3 -> "今日恢復狀態偏低"
            in 4..5 -> "今日恢復狀態稍低"
            in 6..8 -> "今日恢復狀態平穩"
            in 9..10 -> "今日恢復狀態較好"
            else -> "目前沒有恢復評估"
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
        val sleepRecordTime = WearSleepRecencyPolicy.label(
            prefs.getLong("sleep_session_end_at", 0L), System.currentTimeMillis(), java.time.ZoneId.systemDefault()
        )
        val sleepStagesComplete = prefs.getBoolean("sleep_stages_complete", false)
        val temp = prefs.getFloat("temperature_deviation", Float.NaN)
        val timestamp = prefs.getLong("timestamp", 0L)
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(18), dp(16), dp(28)); setBackgroundColor(Color.BLACK)
            addView(text("健康資料摘要", 10f, Color.rgb(100, 210, 255)))
            addView(text("最近同步的紀錄", 19f, Color.WHITE))
            if (readiness10 >= 0) addView(TextView(context).apply {
                text = "$readiness10 / 10"
                textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor(color), Color.parseColor("#18191D"))).apply { shape = GradientDrawable.OVAL }
            }, LinearLayout.LayoutParams(dp(115), dp(115)).apply { topMargin = dp(12) })
            addView(text(status, 19f, Color.parseColor(color)))
            val sleepLabel = when {
                prefs.getBoolean("sleep_inferred", false) -> "睡眠紀錄"
                sleepStagesComplete -> "睡眠時長"
                else -> "睡眠時段"
            }
            val sleepValue = when {
                sleep.isFinite() && !prefs.getBoolean("sleep_inferred", false) -> "%.1f 小時 · %s".format(sleep, sleepRecordTime)
                sleepSession.isFinite() -> "%.1f 小時 · %s（可能含醒來時間）".format(sleepSession, sleepRecordTime)
                else -> "尚無紀錄"
            }
            addView(card(sleepLabel, sleepValue))
            addView(card("訓練負荷", WearReadinessPresentation.trainingLoad(prefs.getInt("training_load", -1))))
            if (temp.isFinite()) addView(card("腕溫變化", "%+.1f °C".format(temp)))
            addView(text(if (timestamp > 0) "更新於 ${dateFormat.format(Date(timestamp))}" else "請在手機開啟健康中心更新紀錄", 10f, Color.LTGRAY))
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
        if (value >= 0) "已取得運動負荷估算" else "尚無運動負荷紀錄"
}
