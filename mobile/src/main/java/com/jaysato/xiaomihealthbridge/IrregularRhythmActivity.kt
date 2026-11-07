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
            text = "手錶原始 ECG 研究量測與最近紀錄；不提供醫療診斷"
            textSize = 14f; setTextColor(Color.rgb(90, 90, 100))
            setPadding(0, dp(6), 0, dp(18))
        })
        val commandFeedback = TextView(this).apply {
            text = "按下方按鈕會傳送 ECG 量測命令；請在手錶上確認接觸電極。"
            textSize = 13f
            setTextColor(Color.rgb(90, 90, 100))
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(commandFeedback)
        root.addView(card(
            "原廠 ECG 啟用",
            "原廠 ECG 由手機 Mi Fitness 與手錶 Xiaomi 健康服務共同管理。本 App 不會修改原廠狀態。已安裝 Mi Fitness 的程式顯示：裝置設定中的 ECG 卡片先檢查產品功能清單是否含 ecg；Play 版的提醒路徑另檢查 electrocardiogram_global。正式啟用畫面會向手錶發送請求，待手錶回覆成功才寫入手機端啟用狀態。這些並非同一個開關。已配對手機的產品目錄有三種 Watch 2 Pro 列項，均未列出上述 ECG 功能鍵；但目錄不是配對裝置的最終授權清單。若手錶顯示 Activation Guidance，請在 Mi Fitness 查找 ECG 卡片；若沒有，本 App 無法代替原廠開通。下方可獨立擷取未校準的原始感測事件。",
            Color.rgb(90, 90, 100)
        ))

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
                displayedTitle,
                buildString {
                    append(consumerStatus.detail)
                    append("\n已保存原始事件 $eventCount 個 · 研究解碼點 $decodedSampleCount 個 · 原始樣本 $samples 個")
                    append("\n事件、解碼點與原始樣本分別計數；僅代表資料已收到，不等於可判讀心律。")
                },
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
                    append("RR $rr · 品質 ${ResearchScoreDisplay.format(quality)} · $samples 點")
                    if (time > 0) {
                        append('\n')
                        append(SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(time)))
                    }
                },
                Color.rgb(28, 28, 30)
            ))
            root.addView(card(
                "詳細（研究）",
                if (estimateUsable) {
                    (if (!fresh) "此為歷史記錄，不代表目前心律。\n" else "") + (detail ?: "無詳細說明")
                }
                else "已收到原始 ECG 資料，但研究輸出未通過品質／取樣率／RR 門檻；不顯示舊有心率或節律標籤。原始資料仍可查看。",
                Color.rgb(28, 28, 30)
            ))
            root.addView(card(
                "臨床對照",
                ClinicalReferenceTables.hrVsResting(displayedBpm?.toDouble()) +
                    "\n篩檢公式：Tateno–Glass CV、短時 RMSSD／pNN50；非原廠 AF、非醫療診斷。",
                Color.rgb(28, 28, 30)
            ))
        }

        root.addView(Button(this).apply {
            text = "啟動手錶 ECG 研究量測"
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
                    text = "啟動手錶 ECG 研究量測"
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
            text = "查看 ECG 原始波形"
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
            text = "若有胸痛、暈厥、呼吸困難，請立即就醫，不要依研究篩檢自行用藥。"
            textSize = 12f; setTextColor(Color.rgb(140, 90, 40)); setPadding(0, dp(16), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247)); addView(root)
        })
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
