package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.Wearable

/**
 * Phone Noise / hearing entry aligned with Apple Noise App slot.
 * Live dB capture runs on watch; phone shows last synced value and opens watch measure.
 */
class NoiseSummaryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = RawSensorStore.open(this@NoiseSummaryActivity)
        val dbfs = raw.getFloat("noise_dbfs", Float.NaN).takeIf { it.isFinite() }
        val dba = raw.getFloat("noise_dba", Float.NaN).takeIf { it.isFinite() }
        val time = raw.getLong("noise_time", 0L)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(48))
            setBackgroundColor(Color.rgb(242, 242, 247))
        }
        root.addView(TextView(this).apply {
            text = "聽力／噪音"; textSize = 34f; setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "顯示手錶最近一次噪音讀值與校正狀態。"
            textSize = 14f; setTextColor(Color.rgb(90, 90, 100))
            setPadding(0, dp(6), 0, dp(16))
        })
        root.addView(card("最近讀值") {
            addView(TextView(context).apply {
                text = when {
                    dba != null -> "%.0f".format(dba)
                    else -> "—"
                }
                textSize = 48f; setTextColor(Color.BLACK); setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = when {
                    dba != null -> "dBA（已參考校正）"
                    dbfs != null -> "已有量測；尚未校正，無法判斷環境噪音強度"
                    else -> "尚無手錶噪音讀值"
                }
                textSize = 14f; setTextColor(Color.rgb(110, 110, 120))
            })
            if (time > 0) {
                addView(TextView(context).apply {
                    text = "更新於 ${java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))}"
                    textSize = 13f; setTextColor(Color.rgb(120, 120, 130))
                    setPadding(0, dp(6), 0, 0)
                })
            }
        })
        root.addView(card("說明") {
            addView(TextView(context).apply {
                text = "噪音強度需要校正後才能與常見聲級參考比較。請在安靜環境重新量測，或先完成聲級校正。"
                textSize = 14f; setTextColor(Color.rgb(60, 60, 67))
            })
        })
        root.addView(Button(this).apply {
            text = "手錶開啟噪音量測"
            isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setColor(Color.rgb(255, 204, 0))
            }
            setOnClickListener {
                Wearable.getNodeClient(this@NoiseSummaryActivity).connectedNodes
                    .addOnSuccessListener { nodes ->
                        val node = WearCommandTargetPolicy.uniqueTarget(nodes)
                        if (node == null) {
                            android.widget.Toast.makeText(this@NoiseSummaryActivity, WearCommandTargetPolicy.failureMessage(nodes.size), android.widget.Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                        Wearable.getMessageClient(this@NoiseSummaryActivity)
                            .sendMessage(node.id, "/control/noise-now", byteArrayOf())
                            .addOnSuccessListener {
                                android.widget.Toast.makeText(this@NoiseSummaryActivity, "已通知手錶開始量測", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                android.widget.Toast.makeText(this@NoiseSummaryActivity, "無法通知手錶，請確認連線", android.widget.Toast.LENGTH_LONG).show()
                            }
                    }
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) }
        })
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247))
            addView(root)
        })
    }

    private fun card(title: String, build: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat(); setColor(Color.WHITE)
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        addView(TextView(context).apply {
            text = title; textSize = 15f; setTextColor(Color.rgb(255, 179, 0))
            setTypeface(typeface, Typeface.BOLD)
        })
        build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
