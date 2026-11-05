package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class WearOpticalBpActivity : ComponentActivity() {
    companion object {
        const val EXTRA_AUTO_START = "auto_start"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(28))
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = "血壓"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = "此手錶沒有公開且經裝置驗證的無袖帶血壓演算法。PPG 或心率不能換算成可信的 mmHg，因此本頁不會啟動感測器、推算或儲存血壓。"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(12), 0, dp(12))
            })
            addView(TextView(context).apply {
                text = "請使用經驗證的上臂袖帶血壓計；若讀值異常，依醫療專業人員建議處理。"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(255, 190, 120))
            })
        }
        WearUi.installScrollable(this, content)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
