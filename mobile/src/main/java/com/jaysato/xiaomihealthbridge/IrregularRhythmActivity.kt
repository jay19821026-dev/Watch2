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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Apple Health–style irregular rhythm / ECG classification history.
 * Research labels only — not medical AF diagnosis.
 */
class IrregularRhythmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = RawSensorStore.open(this@IrregularRhythmActivity)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(48))
            setBackgroundColor(Color.rgb(242, 242, 247))
        }
        root.addView(TextView(this).apply {
            text = "ECG 心電圖"; textSize = 34f; setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "查看這次 ECG 結果，或在手錶重新量測"
            textSize = 14f; setTextColor(Color.rgb(90, 90, 100))
            setPadding(0, dp(6), 0, dp(18))
        })
        val commandFeedback = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.rgb(90, 90, 100))
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(commandFeedback)
        val title = raw.getString("ecg_rhythm_title", null)
        val detail = raw.getString("ecg_rhythm_detail", null)
        val bpm = raw.getFloat("ecg_rhythm_bpm", Float.NaN).takeIf { it.isFinite() }
        val rr = raw.getInt("ecg_rhythm_rr_count", 0)
        val samples = raw.getInt("ecg_raw_value_count", 0)
        val eventCount = raw.getInt("ecg_event_count", 0).coerceAtLeast(
            raw.getInt("ecg_research_decoded_event_count", 0)
        )
        val decodedSampleCount = raw.getInt("ecg_research_decoded_value_count", 0)
        val hasEvidence = EcgEvidencePresencePolicy.hasEvidence(samples, eventCount, decodedSampleCount)
        val quality = raw.getInt("ecg_quality_score", 0)
        val time = raw.getLong("ecg_time", 0L)
        val now = System.currentTimeMillis()
        val timestampSource = raw.getString("ecg_timestamp_source", null)
        val trustedPastTimestamp = HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(timestampSource) &&
            time in 1L..now
        val fresh = trustedPastTimestamp && HealthAnalysisFreshnessPolicy.isFreshSensor(time, timestampSource, now)
        val estimateUsable = trustedPastTimestamp && ClinicalVerdict.isEcgPulseDisplayable(
            qualityScore = quality,
            sampleCount = samples,
            sampleRate = raw.getFloat("ecg_sample_rate", 0f),
            status = raw.getString("ecg_status", "").orEmpty(),
            rrCount = rr,
            wearQualified = if (raw.contains("ecg_wear_qualified")) raw.getBoolean("ecg_wear_qualified", false) else null,
            heartRateBpm = bpm?.toDouble(),
            waveformEncoding = raw.getString("ecg_waveform_encoding", ""),
            timelineSource = raw.getString("ecg_research_timeline_source", "NORMALIZED_INDEX").orEmpty()
        )
        val displayedBpm = bpm.takeIf { estimateUsable }
        val consumerStatus = EcgConsumerStatus.classify(
            eligible = estimateUsable,
            rhythmTitle = title,
            hasEvidence = hasEvidence,
            historical = estimateUsable && !fresh,
            measurementComplete = raw.getBoolean("ecg_final", false)
        )
        val displayedTitle = consumerStatus.label

        if (!hasEvidence) {
            root.addView(card(displayedTitle, consumerStatus.detail, Color.rgb(142, 142, 147)))
        } else if (!estimateUsable || title.isNullOrBlank()) {
            root.addView(card(
                "本次沒有心律結果",
                "請讓手指貼緊電極、手臂保持不動，再量測一次。",
                Color.rgb(90, 200, 250)
            ))
        } else {
            val accent = when {
                estimateUsable && title.contains("房顫") -> Color.rgb(255, 59, 48)
                estimateUsable && title.contains("不整") -> Color.rgb(255, 149, 0)
                estimateUsable && (title.contains("竇性") || title.contains("規則")) -> Color.rgb(52, 199, 89)
                else -> Color.rgb(90, 200, 250)
            }
            root.addView(card("最近結果", displayedTitle, accent))
            root.addView(card(
                "摘要",
                buildString {
                    displayedBpm?.let { append("%.0f bpm · ".format(it)) }
                    append("${if (title.contains("不整") || title.contains("房顫")) "心律跡象需留意" else "心律間距規則"}")
                    if (time > 0) {
                        append('\n')
                        append(SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(time)))
                    }
                },
                Color.rgb(28, 28, 30)
            ))
        }

        root.addView(Button(this).apply {
            text = "ECG 啟用說明"
            isAllCaps = false
            setOnClickListener {
                android.app.AlertDialog.Builder(this@IrregularRhythmActivity)
                    .setTitle("原廠 ECG 啟用")
                    .setMessage("原廠 ECG 需要在 Mi Fitness 的 ECG 卡片啟用。目前這支手錶的裝置頁沒有該卡片；AILISA 的重新量測按鈕無法代替原廠啟用。")
                    .setPositiveButton("知道了", null).show()
            }
        })

        root.addView(Button(this).apply {
            text = "重新量測 ECG"
            isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setColor(Color.rgb(52, 199, 89))
            }
            setOnClickListener {
                isEnabled = false
                text = "正在連接手錶…"
                commandFeedback.text = "正在搜尋手錶並傳送 ECG 啟動命令…"
                WearCommandDispatcher.send(
                    this@IrregularRhythmActivity,
                    "/control/ecg-now"
                ) { outcome ->
                    isEnabled = true
                    text = "重新量測 ECG"
                    commandFeedback.text = WearCommandDispatcher.feedback(outcome)
                    if (outcome !is WearCommandDispatcher.Outcome.Sent) {
                        android.widget.Toast.makeText(
                            this@IrregularRhythmActivity,
                            commandFeedback.text,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) }
        })
        root.addView(Button(this).apply {
            text = "查看波形"
            isAllCaps = false
            setOnClickListener {
                commandFeedback.text = "正在開啟 ECG 原始資料與波形…"
                try {
                    startActivity(
                        Intent(this@IrregularRhythmActivity, RawSensorDetailActivity::class.java)
                            .putExtra("kind", "ecg")
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                } catch (error: android.content.ActivityNotFoundException) {
                    commandFeedback.text = "無法開啟 ECG 原始資料頁，請重新啟動 App 後再試。"
                    android.widget.Toast.makeText(
                        this@IrregularRhythmActivity,
                        commandFeedback.text,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }
        })
        root.addView(TextView(this).apply {
            text = "手錶結果僅供參考；若有胸痛、暈厥或呼吸困難，請立即就醫。"
            textSize = 12f; setTextColor(Color.rgb(140, 90, 40)); setPadding(0, dp(16), 0, 0)
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247))
            clipToPadding = false
            addView(root)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
    }

    private fun card(title: String, body: String, titleColor: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat(); setColor(Color.WHITE)
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        addView(TextView(context).apply {
            text = title; textSize = 15f; setTextColor(titleColor); setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = body; textSize = 14f; setTextColor(Color.rgb(60, 60, 67))
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
