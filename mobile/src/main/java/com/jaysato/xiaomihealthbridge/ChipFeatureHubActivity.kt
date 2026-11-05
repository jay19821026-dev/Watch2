package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.Wearable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phone hub: same chip → feature table as the watch, with synced raw data + remote measure.
 */
class ChipFeatureHubActivity : ComponentActivity() {
    private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = RawSensorStore.open(this@ChipFeatureHubActivity)
        val feature = getSharedPreferences(WearDataProcessor.FEATURE_PREFS, MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(40))
            setBackgroundColor(Color.rgb(8, 10, 16))
        }
        root.addView(label("晶片功能與資料", 26f, Color.WHITE, true))
        root.addView(label(
            "依公開 Sensor API 名稱與通道線索整理候選晶片／演算法端點；不代表料號已確認、功能已啟動或已取得有效數值。",
            13f, Color.rgb(160, 170, 190), false
        ).also { it.setPadding(0, dp(8), 0, dp(16)) })
        root.addView(Button(this).apply {
            text = "開啟完整感測器掃描（手錶）"
            isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
            background = pill("#1F6FEB", "#123A7A")
            setOnClickListener { send("/control/sensor-suite") }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(14) })

        ChipFeatureCatalog.chips.forEach { chip ->
            root.addView(label("${chip.part} · ${chip.vendor}", 18f, Color.rgb(100, 210, 255), true).also {
                it.setPadding(0, dp(14), 0, dp(2))
            })
            root.addView(label(chip.role, 12f, Color.rgb(140, 150, 170), false).also {
                it.setPadding(0, 0, 0, dp(8))
            })
            chip.features.forEach { item ->
                val summary = item.kind?.let { raw.getString("${it}_summary", null) }
                val status = item.kind?.let { raw.getString("${it}_status", null) }
                val samples = item.kind?.let {
                    RawSensorPayloadPolicy.displayPresenceCount(
                        raw.getInt("${it}_count_contract_version", 0),
                        raw.getInt("${it}_raw_value_count", -1).takeIf { count -> count >= 0 },
                        raw.getInt("${it}_event_count", 0),
                        raw.getInt("${it}_preview_point_count", 0),
                        raw.getInt("${it}_samples", 0)
                    )
                } ?: 0
                val whenMs = item.kind?.let { raw.getLong("${it}_time", 0L) } ?: 0L
                val chipPart = item.kind?.let { raw.getString("${it}_chip_part", null) }
                val displayState = item.kind?.let {
                    SensorDisplayState.from(
                        samples,
                        status.orEmpty(),
                        whenMs,
                        System.currentTimeMillis(),
                        SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
                        it,
                        raw.getString("${it}_timestamp_source", null)
                    )
                }
                val dataLine = when {
                    summary != null -> buildString {
                        if (displayState != null) append("${displayState.label} · ")
                        append(summary)
                        if (samples > 0) append(" · ${samples} 點")
                        if (whenMs > 0) append(" · ${timeFmt.format(Date(whenMs))}")
                        if (!chipPart.isNullOrBlank()) append(" · $chipPart")
                    }
                    item.kind == "pressure" && feature.contains("altitude") ->
                        "標準大氣公式估算 ${"%.1f".format(feature.getFloat("altitude", Float.NaN))} m（非實際海拔／爬升）"
                    item.kind == "light" && feature.contains("light") ->
                        "環境光快照 ${"%.0f".format(feature.getFloat("light", Float.NaN))} lux"
                    item.title.contains("磁場方向") && feature.contains("heading") ->
                        "方位快照 ${"%.0f".format(feature.getFloat("heading", Float.NaN))}°"
                    else -> "尚未同步；請在手錶晶片功能中心量測，或點下方遠端啟動"
                }
                val accent = when {
                    displayState == SensorDisplayState.AVAILABLE ||
                        displayState == SensorDisplayState.SIGNAL_VALID_RESEARCH -> Color.rgb(67, 211, 166)
                    displayState != null && displayState !in setOf(
                        SensorDisplayState.NOT_MEASURED,
                        SensorDisplayState.CONDITION_NOT_MET
                    ) -> Color.rgb(255, 190, 77)
                    summary != null -> Color.rgb(255, 190, 77)
                    else -> Color.rgb(90, 110, 140)
                }
                root.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(Color.rgb(20, 24, 34))
                        setStroke(dp(1), accent.and(0x55FFFFFF))
                    }
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                    addView(label(item.title, 15f, Color.WHITE, true))
                    addView(label("他錶：${item.otherWatchUse}", 11f, Color.rgb(140, 150, 170), false))
                    addView(label("手機：${item.phoneView}", 11f, Color.rgb(140, 150, 170), false))
                    addView(label(dataLine, 12f, accent, false).also { it.setPadding(0, dp(6), 0, dp(8)) })
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        if (item.kind != null) {
                            addView(Button(context).apply {
                                text = "查看資料"; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE)
                                background = pill("#2A3857", "#182238")
                                setOnClickListener {
                                    startActivity(
                                        Intent(this@ChipFeatureHubActivity, RawSensorDetailActivity::class.java)
                                            .putExtra("kind", item.kind)
                                    )
                                }
                            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
                        }
                        when {
                            item.controlPath != null -> addView(Button(context).apply {
                                text = "手錶量測"; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE)
                                background = pill("#198C68", "#0E513D")
                                setOnClickListener { send(item.controlPath) }
                            }, LinearLayout.LayoutParams(0, dp(42), 1f))
                            item.title.contains("運動") -> addView(Button(context).apply {
                                text = "運動分析"; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE)
                                background = pill("#236A82", "#174759")
                                setOnClickListener {
                                    startActivity(Intent(this@ChipFeatureHubActivity, WorkoutAnalysisActivity::class.java))
                                }
                            }, LinearLayout.LayoutParams(0, dp(42), 1f))
                            item.title.contains("返航") || item.title.contains("指南針") -> addView(Button(context).apply {
                                text = "說明"; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE)
                                background = pill("#315A86", "#1C3552")
                                setOnClickListener {
                                    Toast.makeText(
                                        this@ChipFeatureHubActivity,
                                        "請在手錶開啟指南針／返航；手機顯示同步狀態。",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }, LinearLayout.LayoutParams(0, dp(42), 1f))
                            item.title.contains("手勢") -> addView(Button(context).apply {
                                text = "說明"; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE)
                                background = pill("#4B477B", "#2B294A")
                                setOnClickListener {
                                    Toast.makeText(
                                        this@ChipFeatureHubActivity,
                                        "請在手錶「手勢／夜間省電」頁設定。",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }, LinearLayout.LayoutParams(0, dp(42), 1f))
                        }
                    })
                })
            }
        }
        root.addView(label(
            "研究／健康參考，非醫療診斷。未驗證單位（SpO₂ %、BIOZ Ω、ECG mV）不會在此宣稱。",
            12f, Color.rgb(255, 190, 120), false
        ).also { it.setPadding(0, dp(12), 0, 0) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(8, 10, 16))
            addView(root)
        })
    }

    private fun send(path: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                val node = WearCommandTargetPolicy.uniqueTarget(nodes)
                if (node == null) {
                    Toast.makeText(this, WearCommandTargetPolicy.failureMessage(nodes.size), Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                Wearable.getMessageClient(this).sendMessage(node.id, path, ByteArray(0))
                Toast.makeText(this, "已送出 $path", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "送出失敗：${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1.05f)
        gravity = Gravity.START
    }

    private fun pill(start: String, end: String) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(start), Color.parseColor(end))
    ).apply { cornerRadius = dp(12).toFloat() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
