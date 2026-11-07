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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Apple Health / Samsung Health style sleep page:
 * total duration + honest unavailable stages (no fake REM/deep).
 */
class SleepSummaryActivity : ComponentActivity() {
    private lateinit var hoursView: TextView
    private lateinit var hoursMeaningView: TextView
    private lateinit var sessionsView: TextView
    private lateinit var stagesView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(48))
            setBackgroundColor(Color.rgb(242, 242, 247))
        }
        root.addView(TextView(this).apply {
            text = "睡眠"; textSize = 34f; setTextColor(Color.BLACK); setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "顯示已記錄的睡眠時段；若沒有睡眠分期資料，會明確標示未提供。"
            textSize = 14f; setTextColor(Color.rgb(90, 90, 100))
            setPadding(0, dp(6), 0, dp(16))
        })
        hoursView = TextView(this).apply {
            textSize = 48f; setTextColor(Color.BLACK); setTypeface(typeface, Typeface.BOLD); text = "—"
        }
        hoursMeaningView = TextView(this).apply {
            text = "小時 · 等待來源資料"
            textSize = 13f; setTextColor(Color.rgb(110, 110, 120))
        }
        sessionsView = TextView(this).apply {
            textSize = 14f; setTextColor(Color.rgb(60, 60, 67)); text = "載入中…"
        }
        stagesView = TextView(this).apply {
            textSize = 14f; setTextColor(Color.rgb(60, 60, 67))
            text = "REM／核心／深睡：此裝置無經驗證分期 API，不推算假分期。"
        }
        root.addView(card("過去 7 日睡眠") {
            addView(hoursView)
            addView(hoursMeaningView)
        })
        root.addView(card("階段") { addView(stagesView) })
        root.addView(card("近期時段") { addView(sessionsView) })
        root.addView(Button(this).apply {
            text = "手動輸入睡眠時數"
            isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setColor(Color.rgb(94, 92, 230))
            }
            setOnClickListener {
                startActivity(Intent(this@SleepSummaryActivity, MainActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) }
        })
        root.addView(Button(this).apply {
            text = "為什麼沒有分期？"
            isAllCaps = false
            setOnClickListener {
                startActivity(
                    Intent(this@SleepSummaryActivity, UnavailableFeatureActivity::class.java)
                        .putExtra("feature", "sleep_stages")
                )
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }
        })
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242, 242, 247)); addView(root)
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            runCatching {
                val client = HealthConnectClient.getOrCreate(this@SleepSummaryActivity)
                val perm = HealthPermission.getReadPermission(SleepSessionRecord::class)
                if (perm !in client.permissionController.getGrantedPermissions()) {
                    hoursView.text = "—"
                    hoursMeaningView.text = "需要 Health Connect 睡眠讀取權限"
                    sessionsView.text = "尚未授予 Health Connect 睡眠讀取權限。"
                    stagesView.text = "無法讀取分期資料。"
                    return@launch
                }
                val end = Instant.now()
                val start = end.minusSeconds(7L * 24 * 3600)
                val sessions = client.readAllRecords(
                    SleepSessionRecord::class,
                    TimeRangeFilter.between(start, end)
                ).filter { it.startTime.isBefore(end) && it.endTime.isAfter(start) }
                val sleepValues = sessions.map { record ->
                    val awake = record.stages.filter { stage ->
                        stage.stage == SleepSessionRecord.STAGE_TYPE_AWAKE ||
                            stage.stage == SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED ||
                            stage.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
                    }.map { SleepAwakeInterval(it.startTime, it.endTime) }
                    val knownTypes = record.stages.all { stage ->
                        stage.stage in setOf(
                            SleepSessionRecord.STAGE_TYPE_AWAKE,
                            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
                            SleepSessionRecord.STAGE_TYPE_SLEEPING,
                            SleepSessionRecord.STAGE_TYPE_LIGHT,
                            SleepSessionRecord.STAGE_TYPE_DEEP,
                            SleepSessionRecord.STAGE_TYPE_REM
                        )
                    }
                    SleepSessionValue(
                        record.startTime, record.endTime, awake, record.stages.isNotEmpty(),
                        record.stages.map { SleepAwakeInterval(it.startTime, it.endTime) }, knownTypes
                    )
                }
                val summary = SleepIntervalMath.summarize(sleepValues, start, end)
                hoursView.text = if (sessions.isEmpty()) "—" else "%.1f".format(
                    (summary.sleepMinutes ?: summary.sessionMinutes) / 60.0
                )
                hoursMeaningView.text = if (summary.stagesComplete) {
                    "小時 · 完整分期已扣除清醒；時段共 %.1f 小時".format(summary.sessionMinutes / 60.0)
                } else {
                    "小時 · 睡眠時段聯集，可能包含清醒；不作實際睡眠時長"
                }
                val fmt = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())
                sessionsView.text = if (sessions.isEmpty()) {
                    "尚無睡眠紀錄。可從主畫面手動輸入，或由手錶／手機同步。"
                } else {
                    sessions.sortedBy { it.endTime }.takeLast(8).reversed().joinToString("\n") { s ->
                        val clippedStart = maxOf(s.startTime, start)
                        val clippedEnd = minOf(s.endTime, end)
                        val h = java.time.Duration.between(clippedStart, clippedEnd).toMinutes() / 60.0
                        "${fmt.format(clippedStart)}–${fmt.format(clippedEnd)} · %.1f 小時時段 · ${s.metadata.dataOrigin.packageName}".format(h)
                    }
                }
                stagesView.text = if (summary.stagesComplete) {
                    "Health Connect 分期完整覆蓋所示時段，已扣除清醒；分期由來源 App 提供，未經本 App 臨床驗證。"
                } else if (sessions.any { it.stages.isNotEmpty() }) {
                    "來源有分期，但有缺口、重疊、未知類型或不完整時段；不推算實際睡眠或 REM／深睡。"
                } else {
                    "REM／核心／深睡：此裝置沒有可讀取的分期資料，因此不顯示推算分期。"
                }
            }.onFailure {
                hoursView.text = "—"
                hoursMeaningView.text = "讀取失敗，無可用時數"
                sessionsView.text = "讀取失敗：${it.message}"
                stagesView.text = "無法讀取分期資料。"
            }
        }
    }

    private fun card(title: String, build: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat(); setColor(Color.WHITE)
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        addView(TextView(context).apply {
            text = title; textSize = 15f; setTextColor(Color.rgb(94, 92, 230))
            setTypeface(typeface, Typeface.BOLD)
        })
        build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
