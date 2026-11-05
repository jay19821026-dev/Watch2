package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class WearHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), dp(32))
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = "健康歷史"; textSize = 24f; setTextColor(Color.WHITE)
                typeface = Typeface.create("sans", Typeface.BOLD); gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "最近同步到手錶的實測資料"; textSize = 12f
                setTextColor(Color.parseColor("#8E8E93")); gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(22))
            })
            addView(TextView(context).apply {
                text = WearHealthStore.historyText(this@WearHistoryActivity)
                textSize = 14f; setTextColor(Color.parseColor("#F2F2F7"))
                setPadding(dp(18), dp(18), dp(18), dp(18))
                setBackgroundColor(Color.parseColor("#1C1C1E"))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        WearUi.installScrollable(this, content)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
