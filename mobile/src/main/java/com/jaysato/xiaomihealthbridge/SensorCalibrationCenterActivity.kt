package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class SensorCalibrationCenterActivity : ComponentActivity() {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val evidenceChannels = listOf(
        EvidenceChannel("ecg", "ECG", "保留原始事件與索引波形；mV 需裝置專屬導聯、同步參考 ECG 與獨立留出驗證。"),
        EvidenceChannel("ppg", "PPG", "可檢視原始振幅與波形品質；脈率需可信取樣時間、通道資訊及參考驗證。"),
        EvidenceChannel("spo2_raw", "SpO₂ 原始光學通道", "保留原始值；不能把原始碼當百分比。百分比需 OEM 輸出或參考血氧計驗證。"),
        EvidenceChannel("mi_spo2", "MI SpO₂ 通道", "依來源保存原始／廠商通道資料；此頁不將它轉成血氧百分比。"),
        EvidenceChannel("bioz", "BioZ", "可檢視原始阻抗通道相對變化；Ω、體脂與體水需通道映射、激勵條件及同步阻抗參考。"),
        EvidenceChannel("body_temperature_raw", "體表溫度原始通道", "依序列檢視原始讀值；單位未驗證時不換算成 °C。"),
        EvidenceChannel("thermometer", "溫度計 HAL 通道", "依來源保存原始讀值；需確認裝置單位並與校驗溫度計同步配對。"),
        EvidenceChannel("whs_heart_rate", "Health Services 心率", "顯示平台已處理的 bpm 與來源時間；不以此建立原始感測器校準。", featureSnapshot = true),
        EvidenceChannel("heart_rate_raw", "手錶心率通道", "保留手錶回報的心率結果與來源時間；勿與原始 PPG 樣本數混為一談。")
    )
    private val channels = listOf(
        Channel("pressure", "氣壓計", "hPa", "與已校驗氣壓計在同地點、同時間同步記錄；高度變化時避免以天氣造成的壓力變化混用。"),
        Channel("light", "環境光", "lux", "與照度計保持相同方向與遮蔽條件，跨低、中、高照度至少五點。"),
        Channel("magnetometer", "磁力計幅值", "µT", "使用可追溯磁場參考或已校驗磁力計；遠離磁吸錶帶、充電器與鋼構。單一幅值 profile 不取代三軸 hard／soft-iron 矩陣。"),
        Channel("accelerometer_uncalibrated", "加速度計幅值", "m/s²", "以多姿態靜止重力與外部慣性感測參考配對；單一幅值 profile 不取代三軸 bias／scale／misalignment 校正。"),
        Channel("gyroscope_uncalibrated", "陀螺儀幅值", "rad/s", "使用已知角速度轉台或外部 IMU；靜止零偏與多個角速度點需分開記錄。")
    )

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(40))
            setBackgroundColor(HealthVisuals.background)
            addView(text("‹  返回", 16f, HealthVisuals.blue).apply { setOnClickListener { finish() } })
            addView(text("感測器校準中心", 30f, Color.WHITE, true), lp(top = 18))
            addView(text("EXTERNAL REFERENCE CALIBRATION", 11f, Color.rgb(118, 181, 255), true).apply { letterSpacing = .12f }, lp(top = 5))
            addView(panel().apply {
                addView(text("研究級外部參考", 19f, Color.WHITE, true))
                addView(text("以下氣壓／光線／慣性感測器工具僅適用其各自列出的物理量。配對資料須來自同時量測的外部參考設備；留一驗證是探索性檢查，不等同獨立外部驗證。", 13f, HealthVisuals.secondary), lp(top = 8))
                addView(text("Profile 僅供本 App 研究預覽，不會改寫 Health Connect、原廠韌體或醫療數據。ECG、PPG／SpO₂、BioZ、溫度不使用下方通用斜率公式。", 12f, Color.rgb(255, 194, 112)), lp(top = 10))
            }, lp(top = 16, bottom = 14))
            addView(panel().apply {
                addView(text("其他生理感測器：原始資料與校準狀態", 18f, Color.WHITE, true))
                addView(text("以下狀態不代表感測器不存在；表示目前沒有可套用到本機裝置的驗證校準 profile。", 12f, HealthVisuals.secondary), lp(top = 6))
                val rawPrefs = RawSensorStore.open(this@SensorCalibrationCenterActivity)
                val featurePrefs = getSharedPreferences(WearDataProcessor.FEATURE_PREFS, MODE_PRIVATE)
                evidenceChannels.forEach { channel ->
                    val prefs = if (channel.featureSnapshot) featurePrefs else rawPrefs
                    val countPrefs = if (channel.featureSnapshot) rawPrefs else prefs
                    val kind = channel.kind
                    val events = countPrefs.getInt("${kind}_event_count", -1)
                        .takeIf { it >= 0 && countPrefs.getInt("${kind}_event_count_known", 0) == 1 }
                    val rawValues = countPrefs.getInt("${kind}_raw_value_count", -1)
                        .takeIf { it >= 0 && countPrefs.contains("${kind}_raw_value_count") }
                    val samples = countPrefs.getInt("${kind}_samples", -1)
                        .takeIf { it >= 0 && countPrefs.contains("${kind}_samples") }
                    val timestamp = prefs.getLong("${kind}_time", 0L)
                        .takeIf { it > 0L } ?: prefs.getLong("${kind}_timestamp", 0L)
                    val timestampSource = prefs.getString("${kind}_timestamp_source", null)
                        ?: prefs.getString("${kind}_time_source", null)
                    val latest = prefs.getFloat("${kind}_average", Float.NaN).takeIf(Float::isFinite)
                        ?: prefs.getFloat(kind, Float.NaN).takeIf(Float::isFinite)
                    val hasEvidence = rawValues?.let { it > 0 } == true || samples?.let { it > 0 } == true ||
                        events?.let { it > 0 } == true || latest != null
                    val state = if (hasEvidence) {
                        buildList {
                            latest?.let { add("最新回報 ${"%.2f".format(it)}${if (kind.contains("heart_rate")) " bpm" else ""}") }
                            rawValues?.let { add("原始值 $it") }
                            samples?.let { add("樣本 $it") }
                            events?.let { add("事件 $it") }
                        }.joinToString(" · ")
                    } else "目前沒有已保存的數值或事件"
                    addView(text(channel.title, 15f, Color.rgb(112, 178, 255), true), lp(top = 12))
                    val countSummary = if (hasEvidence) state else
                        "原始值 ${rawValues ?: "未提供"} · 樣本 ${samples ?: "未提供"} · 事件 ${events ?: "未確認"}"
                    val timeSummary = when {
                        timestamp <= 0L -> "記錄時間：未提供"
                        HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource) ->
                            "量測時間：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(timestamp))} · 來源 $timestampSource"
                        else -> "時間欄位：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(timestamp))} · 來源未確認 (${timestampSource ?: "UNKNOWN"})"
                    }
                    val plainStatus = when {
                        kind == "whs_heart_rate" && latest != null -> "最近心率 ${"%.0f".format(latest)} bpm"
                        hasEvidence -> "已收到手錶訊號，尚無可用的健康讀值"
                        else -> "尚無量測紀錄"
                    }
                    addView(text(plainStatus, 13f, Color.WHITE), lp(top = 4))
                    addView(text("查看技術詳情 ›", 12f, HealthVisuals.blue).apply {
                        setOnClickListener {
                            android.app.AlertDialog.Builder(this@SensorCalibrationCenterActivity)
                                .setTitle(channel.title)
                                .setMessage("$countSummary\n$timeSummary\n來源：${prefs.getString("${kind}_source_sensor", "感測通道").orEmpty()}\n\n${channel.explanation}")
                                .setPositiveButton("關閉", null)
                                .show()
                        }
                    }, lp(top = 4))
                }
            }, lp(bottom = 14))
            channels.forEach { addView(channelPanel(it), lp(bottom = 12)) }
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(HealthVisuals.background); addView(root) })
    }

    private fun channelPanel(channel: Channel) = panel().apply {
        val rawPrefs = RawSensorStore.open(this@SensorCalibrationCenterActivity)
        val store = SensorCalibrationProfileStore(this@SensorCalibrationCenterActivity)
        val latestRaw = rawPrefs.getFloat("${channel.kind}_average", Float.NaN).toDouble().takeIf(Double::isFinite)
        val current = text(profileSummary(store.read(channel.kind), latestRaw), 12f, Color.rgb(137, 220, 190))
        val pairs = EditText(context).apply {
            hint = "例如\n100.0:99.8\n200.0:199.9\n300.0:300.2\n400.0:400.1\n500.0:499.9"
            setHintTextColor(Color.rgb(95, 103, 121)); setTextColor(Color.WHITE); textSize = 13f
            minLines = 6; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = HealthVisuals.panel(context, 14)
        }
        val source = EditText(context).apply {
            hint = "外部參考設備、型號與校驗日期"; setHintTextColor(Color.rgb(95, 103, 121)); setTextColor(Color.WHITE)
            textSize = 13f; setPadding(dp(12), dp(10), dp(12), dp(10)); background = HealthVisuals.panel(context, 14)
        }
        val result = text("尚未計算", 12f, HealthVisuals.secondary)
        addView(text(channel.title, 20f, Color.WHITE, true))
        addView(text("目標單位 ${channel.unit} · 最新 raw ${latestRaw?.let { "%.5f".format(it) } ?: "尚無資料"}", 13f, Color.rgb(112, 178, 255)), lp(top = 6))
        addView(text(channel.instructions, 12f, HealthVisuals.secondary), lp(top = 7))
        addView(current, lp(top = 9))
        addView(pairs, lp(top = 10))
        addView(source, lp(top = 8))
        addView(Button(context).apply {
            text = "計算並保存驗證 profile"; isAllCaps = false; setTextColor(Color.WHITE); textSize = 14f
            background = HealthVisuals.interactive(context, 16)
            setOnClickListener {
                val parsed = parsePairs(pairs.text.toString())
                val fit = ExternalReferenceCalibration.fit(parsed.first, parsed.second)
                val saved = store.save(channel.kind, channel.unit, fit, source.text.toString())
                result.text = if (saved) {
                    "已保存研究 profile：slope=${"%.9f".format(fit.slope)} · offset=${"%.6f".format(fit.offset)} ${channel.unit} · fit RMSE=${"%.6f".format(fit.fitRmse)} · leave-one-out RMSE=${"%.6f".format(fit.validationRmse)}。尚未完成獨立外部驗證。"
                } else "未保存：${fit.note}；需至少五組同步、有限且有變化的參考配對，並填寫參考設備來源。"
                fit.validationReport?.let { report ->
                    result.append("\n留一驗證：MAE=${"%.6f".format(report.mae)} · bias=${"%.6f".format(report.bias)} ${channel.unit}")
                    result.append("\n差值平均 ±1.96 SD：${"%.6f".format(report.lowerAgreementLimit)} 至 ${"%.6f".format(report.upperAgreementLimit)} ${channel.unit}")
                    result.append("\n偏差近似 95% CI：${"%.6f".format(report.lowerBiasConfidenceLimit)} 至 ${"%.6f".format(report.upperBiasConfidenceLimit)}；n=${report.sampleCount}")
                    result.append("\n探索性常態近似；重複量測、受試者群聚與外部驗證需另行評估。")
                }
                current.text = profileSummary(store.read(channel.kind), latestRaw)
            }
        }, lp(height = 54, top = 9))
        addView(Button(context).apply {
            text = "清除此通道 profile"; isAllCaps = false; setTextColor(Color.rgb(255, 165, 145)); textSize = 13f
            background = HealthVisuals.interactive(context, 16)
            setOnClickListener {
                store.clear(channel.kind); current.text = profileSummary(null, latestRaw); result.text = "已清除研究 profile。"
            }
        }, lp(height = 48, top = 7))
        addView(result, lp(top = 8))
    }

    private fun parsePairs(input: String): Pair<List<Double>, List<Double>> {
        val pairs = input.lineSequence().mapNotNull { line ->
            val values = line.trim().split(':', ',', '\t').map(String::trim)
            if (values.size != 2) null else {
                val raw = values[0].toDoubleOrNull()
                val reference = values[1].toDoubleOrNull()
                if (raw == null || reference == null) null else raw to reference
            }
        }.toList()
        return pairs.map { it.first } to pairs.map { it.second }
    }

    private fun profileSummary(profile: SensorCalibrationProfileStore.Profile?, raw: Double?): String = when (profile) {
        null -> "目前沒有已驗證的外部參考 profile。"
        else -> buildString {
            append("目前 profile：${profile.source} · ${profile.points} 點\n")
            append("slope=${"%.9f".format(profile.slope)} · offset=${"%.6f".format(profile.offset)} ${profile.targetUnit}\n")
            append("fit RMSE=${"%.6f".format(profile.fitRmse)} · validation RMSE=${"%.6f".format(profile.validationRmse)}")
            append("\n演算法 ${profile.algorithmVersion}")
            if (profile.validationReport.isNotBlank()) append("\n${profile.validationReport}")
            raw?.let { profile.apply(it) }?.let { append("\n最新研究校正值 ${"%.4f".format(it)} ${profile.targetUnit}") }
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); background = HealthVisuals.panel(context, 22, true)
    }

    private fun lp(height: Int = -2, top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, if (height > 0) dp(height) else height).apply {
        topMargin = dp(top); bottomMargin = dp(bottom)
    }

    private data class Channel(val kind: String, val title: String, val unit: String, val instructions: String)
    private data class EvidenceChannel(
        val kind: String,
        val title: String,
        val explanation: String,
        val featureSnapshot: Boolean = false
    )
}
