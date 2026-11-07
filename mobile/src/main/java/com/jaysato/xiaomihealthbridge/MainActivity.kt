package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.Asset
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var metricsStatus: TextView
    private lateinit var insightsStatus: TextView
    private lateinit var heartRateCard: MetricCardView
    private lateinit var restingHeartRateCard: MetricCardView
    private lateinit var oxygenCard: MetricCardView
    private lateinit var weightCard: MetricCardView
    private lateinit var stepsCard: MetricCardView
    private lateinit var caloriesCard: MetricCardView
    private lateinit var sleepCard: MetricCardView
    private lateinit var distanceCard: MetricCardView
    private lateinit var temperatureCard: MetricCardView
    private lateinit var elevationCard: MetricCardView
    private lateinit var floorsCard: MetricCardView
    private lateinit var hydrationCard: MetricCardView
    private lateinit var todayScroll: ScrollView
    private lateinit var healthScroll: ScrollView
    private lateinit var deviceScroll: ScrollView
    private lateinit var exerciseScroll: ScrollView
    private lateinit var profileScroll: ScrollView
    private lateinit var syncRing: TextView
    private lateinit var stepsSummary: TextView
    private lateinit var oxygenSummary: TextView
    private lateinit var sleepSummary: TextView
    private lateinit var heartRateSummary: TextView
    private lateinit var todayNavigation: Button
    private lateinit var healthNavigation: Button
    private lateinit var deviceNavigation: Button
    private lateinit var exerciseNavigation: Button
    private lateinit var profileNavigation: Button
    private var selectedPage = 0
    private var lastSummaryLoadMillis = 0L
    private var lastInsightsLoadMillis = 0L
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) sendFileToWatch(uri)
    }
    private val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getWritePermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getWritePermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        HealthPermission.getWritePermission(ElevationGainedRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getWritePermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class)
    )
    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { refreshStatus() }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SensorSuiteDump.requestPartialCleanup(this)
        MeasureResultActivity.cleanupLegacyNotifications(this)
        status = label("", 14f, "#DDE1EB").apply {
            setPadding(dp(20), dp(20), dp(20), dp(20)); background = HealthVisuals.panel(context)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        metricsStatus = label("最近 7 天資料讀取中…", 14f, "#DDE1EB").apply {
            setPadding(dp(20), dp(20), dp(20), dp(20)); background = HealthVisuals.panel(context)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        heartRateCard = metricCard("心率", "尚未測量", "結果待取得", "#EF5364", EMPTY_TREND)
        restingHeartRateCard = metricCard("靜止心率", "尚未測量", "結果待取得", "#EF5364", "·  ·  ·  ·  ·")
        oxygenCard = metricCard("血氧濃度 (SpO₂)", "尚未測量", "結果待取得", "#36C98F", EMPTY_TREND)
        weightCard = metricCard("體重", "尚未測量", "結果待取得", "#36C98F", EMPTY_TREND)
        stepsCard = metricCard("步數", "尚未取得", "結果待取得", "#EAC879", EMPTY_TREND)
        caloriesCard = metricCard("消耗的熱量", "尚未取得", "結果待取得", "#FF975E", EMPTY_TREND)
        sleepCard = metricCard("睡眠", "尚未取得", "結果待取得", "#7C58D6", EMPTY_TREND)
        distanceCard = metricCard("距離", "尚未取得", "結果待取得", "#4F8FDB", EMPTY_TREND)
        temperatureCard = metricCard("體表溫度", "尚未測量", "結果待取得", "#FF6F91", EMPTY_TREND)
        elevationCard = metricCard("爬升", "尚未取得", "結果待取得", "#56AEFF", EMPTY_TREND)
        floorsCard = metricCard("樓層", "尚未取得", "結果待取得", "#AC9BFF", EMPTY_TREND)
        hydrationCard = metricCard("補水（手動）", "尚未輸入", "結果待取得", "#36C98F", EMPTY_TREND)
        wireMetricDetails()
        syncRing = label("同步狀態檢查中", 12f, "#9ADDC7").apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded("#172D29", 12)
        }
        stepsSummary = summaryChip("步數", "尚未取得", "#087A70")
        oxygenSummary = summaryChip("血氧", "尚未測量", "#6840A8")
        sleepSummary = summaryChip("睡眠時段", "尚未取得", "#7440A9")
        heartRateSummary = summaryChip("心率", "尚未測量", "#EF5364")
        insightsStatus = label("等待健康資料分析…", 15f, "#FFFFFF").apply {
            setPadding(dp(20), dp(20), dp(20), dp(20)); background = HealthVisuals.panel(context)
        }
        val grant = Button(this).apply {
            text = "管理 Health Connect 權限"; textSize = 14f; setTextColor(Color.WHITE); isAllCaps = false
            background = HealthVisuals.interactive(context)
            setOnClickListener { permissionLauncher.launch(permissions) }
        }
        val todayContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(36))
            addView(pageHeading("摘要", "HEALTH BRIDGE", "資料，回到生活的節奏。"), margins(bottom = 24))
            addView(heroCard(), margins(bottom = 24))
            addView(sectionTitle("一眼掌握", "心率顯示有效讀值；步數與睡眠為今日彙總，血氧為最近紀錄"), margins(bottom = 12))
            addView(todayOverview(), margins(bottom = 24))
            addView(featureRow("中心", "手錶健康中心", "集中查看手錶資料、能力與同步狀態", "#FF6F8F") {
                startActivity(Intent(this@MainActivity, HealthCenterActivity::class.java))
            })
            addView(featureRow("V", "健康指標總覽", "查看已同步指標、資料時間，或啟動手錶量測", "#007AFF") {
                startActivity(Intent(this@MainActivity, VitalsHomeActivity::class.java))
            }, margins(top = 12))
            addView(featureRow("覽", "瀏覽分類", "心律／睡眠／活動／身體／血壓槽位", "#34C759") {
                startActivity(Intent(this@MainActivity, HealthBrowseActivity::class.java))
            }, margins(top = 12))
            addView(featureRow("健康", "健康趨勢分析", "本機規則與統計整理已同步資料；不作疾病診斷", "#AC9BFF") {
                startActivity(Intent(this@MainActivity, AiHealthAnalysisActivity::class.java))
            }, margins(top = 12))
        }
        val healthContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(26), dp(20), dp(36))
            addView(pageHeading("健康", "YOUR HEALTH", "每一個訊號，都有跡可循。"))
            addView(label("今日概覽", 12f, "#9EA5B5"), margins(top = 20, bottom = 8))
            addView(label("點選指標，探索日／週／月紀錄", 12f, "#767E91"), margins(bottom = 16))
            addView(metricDashboard())
            addView(sectionTitle("手錶功能與使用方式", "部分項目需在手錶端啟動；此處提供狀態或操作說明"), margins(top = 26, bottom = 10))
            addView(featureRow("℃", "體表溫度", "顯示手錶前景量測與 Health Connect 同步趨勢", "#FF6F91") {
                startActivity(Intent(this@MainActivity, MetricDetailActivity::class.java).putExtra("metric", "temperature"))
            }, margins(bottom = 10))
            addView(featureRow("↗", "海拔／爬升／樓層", "運動期間由氣壓計與 Health Connect 有效事件計算", "#56AEFF") {
                AlertDialog.Builder(this@MainActivity).setTitle("海拔與樓層").setMessage("請先在手錶啟動運動工作階段；手機只顯示已同步事件，不會把 GPS 高度誤當成樓層。").setPositiveButton("知道了", null).show()
            }, margins(bottom = 10))
            addView(featureRow("◎", "指南針／返航導航", "旋轉向量、陀螺儀與磁力計由手錶低功耗前景功能處理", "#7082FF") {
                AlertDialog.Builder(this@MainActivity).setTitle("指南針與返航").setMessage("請從手錶端開啟指南針或返航導航；手機不會在背景持續讀取磁力計與 GPS。").setPositiveButton("知道了", null).show()
            }, margins(bottom = 10))
            addView(featureRow("!", "跌倒風險／姿勢分析", "只在運動工作階段分析高衝擊，並以手錶震動提示", "#FFA452") {
                AlertDialog.Builder(this@MainActivity).setTitle("跌倒風險偵測").setMessage("這是運動安全提醒，不是醫療診斷；請以手錶端運動工作階段啟用。").setPositiveButton("知道了", null).show()
            }, margins(bottom = 10))
            addView(featureRow("✦", "手勢控制／環境光", "手勢與自動亮度只在手錶前景啟用，夜間降低取樣避免耗電", "#AC9BFF") {
                AlertDialog.Builder(this@MainActivity).setTitle("手勢與環境光").setMessage("請在手錶的「手勢與夜間省電設定」開啟。環境光目前只調整手錶 App 內亮度，不強制修改系統亮度。").setPositiveButton("知道了", null).show()
            }, margins(bottom = 10))
            addView(actionButton("立即重新估算靜止心率") { estimateRestingHeartRateNow() }, margins(top = 10))
            addView(sectionTitle("同步明細", "Health Connect 完整來源與統計"), margins(top = 26, bottom = 10))
            addView(metricsStatus)
            addView(sectionTitle("手錶工具", "控制、診斷與無線安裝"), margins(top = 30, bottom = 10))
            addView(actionButton("健康結論（計算＋臨床對照）") {
                startActivity(Intent(this@MainActivity, HealthConclusionActivity::class.java))
            }, margins(bottom = 10))
            addView(actionButton("健康指標總覽") {
                startActivity(Intent(this@MainActivity, VitalsHomeActivity::class.java))
            }, margins(bottom = 10))
            addView(actionButton("瀏覽分類") {
                startActivity(Intent(this@MainActivity, HealthBrowseActivity::class.java))
            }, margins(bottom = 10))
            addView(actionButton("晶片功能與資料（雙端對照）") {
                startActivity(Intent(this@MainActivity, ChipFeatureHubActivity::class.java))
            }, margins(bottom = 10))
            addView(actionButton("工程 Debug｜全鏈路問題檢查") {
                startActivity(
                    Intent(this@MainActivity, EngineeringDebugActivity::class.java)
                        .putExtra(EngineeringDebugActivity.EXTRA_QUERY_WATCH, true)
                )
            }, margins(bottom = 10))
            addView(actionButton("他錶功能對照（Apple／Galaxy／Pixel）") {
                startActivity(Intent(this@MainActivity, CompetitorParityActivity::class.java))
            }, margins(bottom = 10))
            addView(actionButton("光學血壓（研究分析＋建議）") {
                startActivity(Intent(this@MainActivity, OpticalBpActivity::class.java))
            }, margins(bottom = 10))
            addView(actionButton("目前未提供的功能（血糖／睡眠分期）") {
                startActivity(Intent(this@MainActivity, UnavailableFeatureActivity::class.java).putExtra("feature", "glucose"))
            }, margins(bottom = 10))
            addView(actionButton("臨床參考實驗室（PPG／ECG／BIOZ／體溫／全掃描）") {
                startActivity(Intent(this@MainActivity, ClinicalLabActivity::class.java))
            }, margins(bottom = 10))
            addView(actionButton("測試手錶控制通道") { sendWatchCommand("/control/ping") })
            addView(actionButton("重新啟動手錶背景同步") { sendWatchCommand("/control/register-passive") }, margins(top = 10))
            addView(actionButton("啟動手錶體表溫度量測") { sendWatchCommand("/control/temperature-now") }, margins(top = 10))
            addView(actionButton("啟動手錶 SpO₂ 感測") { sendWatchCommand("/control/oxygen-now") }, margins(top = 10))
            addView(actionButton("啟動 PPG 原始波形擷取") { sendWatchCommand("/control/ppg-now") }, margins(top = 10))
            addView(actionButton("啟動手錶光學血壓研究量測") { sendWatchCommand("/control/optical-bp-now") }, margins(top = 10))
            addView(actionButton("啟動 ECG 研究量測") { sendWatchCommand("/control/ecg-now") }, margins(top = 10))
            addView(actionButton("啟動 BioZ 原始波形擷取") { sendWatchCommand("/control/bioz-now") }, margins(top = 10))
            addView(actionButton("檢查離腕／佩戴狀態") { sendWatchCommand("/control/off-body-now") }, margins(top = 10))
            addView(actionButton("完整掃描全部手錶感測器（約 2 分鐘）") { sendWatchCommand("/control/sensor-suite") }, margins(top = 10))
            addView(actionButton("取得手錶同步狀態") { sendWatchCommand("/control/status") }, margins(top = 10))
            if (BuildConfig.DEBUG) {
                addView(actionButton("選擇 APK 並傳送到手錶") {
                    filePicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                })
                addView(label("傳送完成後，在手錶 App 按「安裝收到的 APK」。系統仍會要求確認。", 12f, "#8290AD"), margins(top = 8))
            }
            addView(sectionTitle("外部量測", "只輸入可信裝置的讀值"), margins(top = 30, bottom = 10))
            addView(actionButton("輸入體重（kg）") { showWeightDialog() }, margins(bottom = 10))
            addView(actionButton("輸入血壓（外部血壓計）") { showBloodPressureDialog() })
            addView(actionButton("輸入靜止心率（外部量測）") {
                showSingleValueDialog("靜止心率", "bpm") { ManualEntryWriter(this@MainActivity).writeRestingHeartRate(it.toLong()) }
            }, margins(top = 10))
            addView(actionButton("輸入 HRV RMSSD（外部量測）") {
                showSingleValueDialog("HRV RMSSD", "ms") { ManualEntryWriter(this@MainActivity).writeHrvRmssd(it) }
            }, margins(top = 10))
            addView(actionButton("輸入呼吸速率（外部量測）") {
                showSingleValueDialog("呼吸速率", "次／分鐘") { ManualEntryWriter(this@MainActivity).writeRespiratoryRate(it) }
            }, margins(top = 10))
            addView(actionButton("輸入血氧 SpO₂（外部量測）") {
                showSingleValueDialog("血氧 SpO₂", "%") { ManualEntryWriter(this@MainActivity).writeOxygenSaturation(it) }
            }, margins(top = 10))
            addView(actionButton("輸入睡眠時數（結束於現在）") {
                showSingleValueDialog("睡眠時數", "小時") { ManualEntryWriter(this@MainActivity).writeSleepDuration(it) }
            }, margins(top = 10))
            addView(label("血壓與血氧只能輸入可信裝置讀值；睡眠紀錄不會推算階段。", 12f, "#8290AD").apply {
                setPadding(0, dp(8), 0, 0)
            })
            addView(sectionTitle("同步流程", "資料留在你的裝置與 Health Connect"), margins(top = 30, bottom = 10))
            addView(flowCard("①", "手錶量測", "Wear OS Health Services"), margins(bottom = 10))
            addView(flowCard("②", "安全傳輸", "Google Wearable Data Layer"), margins(bottom = 10))
            addView(flowCard("③", "寫入健康資料", "心率・血氧・步數・距離・卡路里・運動"))
            addView(label("資料只在手錶、手機與 Health Connect 間流動。", 13f, "#8290AD").apply {
                gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0)
            })
            addView(label("Xiaomi 手錶健康中心 · 本機健康資料管理", 12f, "#75D5C8").apply {
                gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
            })
        }
        val toolsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(30))
            addView(pageHeading("裝置", "CONNECTED CARE", "同步、管理，安心掌握。"), margins(bottom = 22))
            addView(syncRing, margins(bottom = 14))
            addView(featureRow("距離", "同步狀態", "查看上次同步、連線回報與錯誤", "#90B8F2") {
                AlertDialog.Builder(this@MainActivity).setTitle("同步狀態").setMessage(status.text).setPositiveButton("關閉", null).show()
            }, margins(bottom = 12))
            addView(grant, margins(bottom = 16))
            addView(actionButton("管理手機通知權限") {
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    !getSharedPreferences("notification_permission_ui", MODE_PRIVATE).getBoolean("requested", false)
                ) {
                    getSharedPreferences("notification_permission_ui", MODE_PRIVATE).edit().putBoolean("requested", true).apply()
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
                }
            }, margins(bottom = 16))
            addView(actionButton("設定生理演算法基本資料") { showPhysiologyProfileDialog() }, margins(bottom = 16))
        }
        val exerciseContent = exercisePage()
        val profileContent = profilePage()
        var toolGroup = toolsPanel
        while (healthContent.childCount > 4) {
            val child = healthContent.getChildAt(4)
            healthContent.removeView(child)
            if(child.tag is String) {
                val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
                toolsPanel.addView(actionButton("${child.tag}  ⌄") {
                    content.visibility = if(content.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }, margins(top = 14))
                toolsPanel.addView(content, margins(top = 10))
                toolGroup = content
            } else if(child === metricsStatus) {
                toolGroup.addView(actionButton("查看來源與統計") {
                    AlertDialog.Builder(this@MainActivity).setTitle("近 7 天同步明細").setMessage(metricsStatus.text).setPositiveButton("關閉", null).show()
                })
            } else toolGroup.addView(child)
        }
        deviceScroll = ScrollView(this).apply { isFillViewport = true; visibility = View.GONE; addView(toolsPanel) }
        exerciseScroll = ScrollView(this).apply { isFillViewport = true; visibility = View.GONE; addView(exerciseContent) }
        profileScroll = ScrollView(this).apply { isFillViewport = true; visibility = View.GONE; addView(profileContent) }
        todayScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(todayContent)
        }
        healthScroll = ScrollView(this).apply {
            isFillViewport = true
            visibility = android.view.View.GONE
            addView(healthContent)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(HealthVisuals.background)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets)
                    .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            addView(FrameLayout(context).apply {
                addView(todayScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(healthScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(deviceScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(exerciseScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(profileScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bottomNavigation(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply {
                setMargins(dp(18), dp(6), dp(18), dp(10))
            })
        })
        selectPage(savedInstanceState?.getInt("page") ?: 0)
        recoverPendingWearData()
        handleControlIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleControlIntent(intent)
    }

    private fun handleControlIntent(intent: Intent?) {
        val path = intent?.data?.takeIf { it.scheme == "xiaomihealthbridge" && it.host == "control" }
            ?.path?.takeIf { it.isNotBlank() } ?: return
        sendWatchCommand("/control$path")
    }

    private fun recoverPendingWearData() {
        SensorSuiteDump.write(applicationContext)
        Wearable.getDataClient(this).dataItems
            .addOnSuccessListener { items ->
                try {
                    WearDataEventDispatchPolicy.dispatch(
                        events = items,
                        shouldProcess = { true },
                        process = { WearDataProcessor.process(applicationContext, it, lifecycleScope) },
                        onFailure = { error ->
                            android.util.Log.w("WearRecovery", "pending_item_failed type=${error.javaClass.simpleName}; continuing recovery")
                        }
                    )
                } finally {
                    items.release()
                }
            }
            .addOnFailureListener { error ->
                getSharedPreferences(HeartRateDataListenerService.PREFS, MODE_PRIVATE).edit()
                    .putString(HeartRateDataListenerService.KEY_LAST_ERROR, "待處理資料讀取失敗：${error.message}")
                    .apply()
            }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        clearResolvedPermissionError()
    }

    private fun clearResolvedPermissionError() {
        val prefs = getSharedPreferences(HeartRateDataListenerService.PREFS, MODE_PRIVATE)
        val error = prefs.getString(HeartRateDataListenerService.KEY_LAST_ERROR, null) ?: return
        if (!error.contains("permission", ignoreCase = true) && !error.contains("權限")) return
        lifecycleScope.launch {
            val granted = runCatching {
                HealthConnectClient.getOrCreate(this@MainActivity).permissionController.getGrantedPermissions()
            }.getOrElse {
                if (it is CancellationException) throw it
                return@launch
            }
            if (granted.containsAll(permissions)) {
                prefs.edit().remove(HeartRateDataListenerService.KEY_LAST_ERROR).apply()
                refreshStatus()
            }
        }
    }

    private fun refreshStatus() {
        val available = HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE
        val prefs = getSharedPreferences(HeartRateDataListenerService.PREFS, MODE_PRIVATE)
        val lastReceived = prefs.getLong(HeartRateDataListenerService.KEY_LAST_RECEIVED, 0)
        val lastHealthConnectWrite = prefs.getLong(HeartRateDataListenerService.KEY_LAST_HEALTH_CONNECT_WRITE, 0)
        val pendingHeartRateSamples = HeartRateWriter(this).pendingSampleCount()
        val error = prefs.getString(HeartRateDataListenerService.KEY_LAST_ERROR, null)
        val controlStatus = prefs.getString("control_status", null)
        val controlStatusTime = prefs.getLong("control_status_time", 0)
        status.text = buildString {
            append(if (available) "●  Health Connect 已就緒" else "○  Health Connect 不可用")
            append("\n\n手錶心率最近收到\n")
            append(if (lastReceived == 0L) "尚未收到手錶資料" else DateFormat.getDateTimeInstance().format(Date(lastReceived)))
            append("\n\nHealth Connect 最近確認寫入\n")
            append(if (lastHealthConnectWrite == 0L) "尚無本版本確認的寫入紀錄" else DateFormat.getDateTimeInstance().format(Date(lastHealthConnectWrite)))
            if (pendingHeartRateSamples > 0) append("\n待寫入 Health Connect：$pendingHeartRateSamples 筆")
            if (error != null) append("\n\n⚠ $error")
            if (controlStatus != null) {
                val isCurrent = !ControlStatusFreshness.isStale(
                    controlStatusTime, System.currentTimeMillis(), SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS
                )
                append(if (isCurrent) "\n\n手錶控制回報\n$controlStatus" else "\n\n上次手錶控制回報（已過期）\n$controlStatus")
                if (controlStatusTime > 0) append("\n${DateFormat.getDateTimeInstance().format(Date(controlStatusTime))}")
            }
        }
        syncRing.text = if (available) "●  Health Connect 可使用" else "○  Health Connect 尚未就緒"
        val interactive = getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
        if (available && HealthConnectAggregatePolicy.shouldQuery(interactive)) loadSevenDaySummary()
        if (available && HealthConnectAggregatePolicy.shouldQuery(interactive)) loadAlgorithmicInsights()
    }

    private fun loadAlgorithmicInsights(force: Boolean = false) {
        if (!HealthConnectAggregatePolicy.shouldQuery(
                getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
            )) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastInsightsLoadMillis < REFRESH_DEBOUNCE_MILLIS) return
        lastInsightsLoadMillis = now
        lifecycleScope.launch {
            insightsStatus.text = "正在分析心率、活動、睡眠與運動…"
            try {
                val insights = AlgorithmicInsightsEngine(this@MainActivity).calculate()
                insightsStatus.text = insights.display()
                WatchInsightsPublisher.publish(this@MainActivity, insights)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                insightsStatus.text = "分析失敗：${error.message ?: "Health Connect 暫時無法讀取"}"
            }
        }
    }

    private fun estimateRestingHeartRateNow() {
        lifecycleScope.launch {
            RestingHeartRateEstimator(this@MainActivity).update(System.currentTimeMillis())
                .onSuccess { value ->
                    toast(value?.let { "已寫入靜止心率 $it bpm" }
                        ?: "目前活動或樣本不足；請安靜配戴約 8 分鐘後再試")
                    loadSevenDaySummary(force = true)
                }
                .onFailure { toast("靜止心率估算失敗：${it.message}") }
        }
    }

    private fun sendFileToWatch(uri: android.net.Uri) {
        val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: run {
            toast("無法開啟檔案"); return
        }
        val displayName = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: "transfer-${System.currentTimeMillis()}.apk"
        val request = com.google.android.gms.wearable.PutDataMapRequest.create("/file-transfer/${System.currentTimeMillis()}").apply {
            dataMap.putString("name", displayName)
            dataMap.putAsset("file", Asset.createFromFd(descriptor))
            dataMap.putLong("sent_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        toast("正在傳送 $displayName…")
        Wearable.getDataClient(this).putDataItem(request)
            .addOnSuccessListener { toast("已送出，請在手錶確認接收") }
            .addOnFailureListener { toast("傳送失敗：${it.message}") }
            .addOnCompleteListener { descriptor.close() }
    }

    private fun sendWatchCommand(path: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                val node = WearCommandTargetPolicy.uniqueTarget(nodes)
                if (node == null) {
                    toast(WearCommandTargetPolicy.failureMessage(nodes.size))
                    return@addOnSuccessListener
                }
                Wearable.getMessageClient(this).sendMessage(node.id, path, byteArrayOf())
                    .addOnSuccessListener { toast("指令已送到 ${node.displayName}") }
                    .addOnFailureListener { toast("控制失敗：${it.message}") }
            }
            .addOnFailureListener { toast("無法取得手錶：${it.message}") }
    }

    private fun loadSevenDaySummary(force: Boolean = false) {
        if (!HealthConnectAggregatePolicy.shouldQuery(
                getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
            )) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastSummaryLoadMillis < REFRESH_DEBOUNCE_MILLIS) return
        lastSummaryLoadMillis = now
        lifecycleScope.launch {
            val (client, granted) = runCatching {
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                client to client.permissionController.getGrantedPermissions()
            }.getOrElse {
                if (it is CancellationException) throw it
                stepsSummary.text = summaryText("步數", "權限查詢失敗")
                oxygenSummary.text = summaryText("血氧", "權限查詢失敗")
                sleepSummary.text = summaryText("睡眠時段", "權限查詢失敗")
                heartRateSummary.text = summaryText("心率", "權限查詢失敗")
                val cardStates = HealthSummaryReadStatePolicy.permissionQueryFailureCards()
                fun clearCard(card: MetricCardView, key: String) {
                    val state = cardStates.getValue(key)
                    card.text = metricText(state.title, state.value, state.detail, state.trend)
                }
                clearCard(weightCard, "weight")
                clearCard(stepsCard, "steps")
                clearCard(caloriesCard, "calories")
                clearCard(sleepCard, "sleep")
                clearCard(heartRateCard, "heart_rate")
                clearCard(restingHeartRateCard, "resting_heart_rate")
                clearCard(oxygenCard, "oxygen")
                clearCard(distanceCard, "distance")
                clearCard(temperatureCard, "temperature")
                clearCard(elevationCard, "elevation")
                clearCard(floorsCard, "floors")
                clearCard(hydrationCard, "hydration")
                metricsStatus.text = "Health Connect 權限清單查詢失敗；摘要卡已清除前次資料。本次未判定各類權限是否已授予。原因：${it.message ?: "未知錯誤"}"
                return@launch
            }
            val endTime = Instant.now()
            val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            val filter = TimeRangeFilter.between(todayStart, endTime)
            val heartRate24hStart = endTime.minusMillis(HeartRatePresentationPolicy.HOME_HISTORY_WINDOW_MILLIS)
            val heartRate24hFilter = TimeRangeFilter.between(heartRate24hStart, endTime)
            val historyStart = endTime.minus(7, ChronoUnit.DAYS)
            val historyFilter = TimeRangeFilter.between(historyStart, endTime)
            val readErrors = linkedSetOf<String>()
            val missingPermissions = linkedSetOf<String>()
            val emptyReads = linkedSetOf<String>()
            val failedReadLabels = linkedSetOf<String>()
            suspend fun <T> readSafely(label: String, permission: String, query: suspend () -> T): T? {
                if (permission !in granted) {
                    missingPermissions += label
                    return null
                }
                return try {
                    query().also { result ->
                        if ((result as? Collection<*>)?.isEmpty() == true) emptyReads += label
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failedReadLabels += label
                    val description = HealthConnectReadFailurePolicy.describe(label, error)
                    readErrors += description
                    getSharedPreferences("health_summary_query_diagnostics", MODE_PRIVATE).edit()
                        .putString("last_failed_metric", label)
                        .putString("last_failed_exception", error::class.java.name.take(200))
                        .putString("last_failed_reason", description.take(1000))
                        .putLong("last_failed_at_ms", System.currentTimeMillis())
                        .apply()
                    null
                }
            }
            fun unavailableState(label: String): String = HealthConnectMetricReadStatePolicy.label(
                HealthConnectMetricReadStatePolicy.classify(label, missingPermissions, emptyReads, failedReadLabels)
            )
            fun aggregateUnavailableReason(label: String, total: Double?, origins: List<String>): String {
                if (total == null) return "Health Connect：${unavailableState(label)}"
                if (HealthConnectAggregatePolicy.acceptsPlatformTotal(total, origins)) return "Health Connect 平台彙總"
                return when (HealthConnectAggregatePolicy.platformTotalRejection(total, origins)) {
                    HealthConnectAggregatePolicy.PlatformTotalRejection.MISSING_TOTAL -> "平台彙總未回傳數值"
                    HealthConnectAggregatePolicy.PlatformTotalRejection.INVALID_TOTAL -> "平台彙總數值無效"
                    HealthConnectAggregatePolicy.PlatformTotalRejection.MISSING_SOURCE -> "平台彙總未提供有效來源"
                    null -> "Health Connect：${unavailableState(label)}"
                }
            }
            try {
                val rawHeartRates = readSafely("心率", HealthPermission.getReadPermission(HeartRateRecord::class)) {
                    client.readAllRecords(HeartRateRecord::class, heartRate24hFilter)
                }.orEmpty()
                val heartRateMeasurements = rawHeartRates.map { record ->
                    val values = record.samples.map { it.beatsPerMinute.toDouble() }
                    val average = values.average()
                    SourceIntervalMeasurement(record.metadata.dataOrigin.packageName, record.startTime, record.endTime, average)
                }
                val heartRateAssessment = SourceIntervalAggregation.assess(heartRateMeasurements)
                val heartRateSummarySamples = rawHeartRates.flatMap { record ->
                    record.samples.filter { HealthConnectSampleTimePolicy.isWithinHalfOpenRange(it.time, heartRate24hStart, endTime) }
                        .map { sample ->
                            HeartRateHomeSummaryPolicy.Sample(
                                friendlySource(record.metadata.dataOrigin.packageName), sample.time,
                                sample.beatsPerMinute.toDouble()
                            )
                        }
                }
                val todayHeartRateRange = HeartRateDailySummaryPolicy.summarizeWindow(
                    heartRateSummarySamples.map {
                        HeartRateDailySummaryPolicy.Sample(it.time.toEpochMilli(), it.bpm, it.source)
                    },
                    todayStart.toEpochMilli(),
                    endTime.toEpochMilli()
                )
                val stepRecords = readSafely("步數", HealthPermission.getReadPermission(StepsRecord::class)) {
                    client.readAllRecords(StepsRecord::class, historyFilter)
                        .filter { it.startTime >= historyStart && it.endTime <= endTime }
                }.orEmpty()
                val distanceRecords = readSafely("距離", HealthPermission.getReadPermission(DistanceRecord::class)) {
                    client.readAllRecords(DistanceRecord::class, historyFilter)
                        .filter { it.startTime >= historyStart && it.endTime <= endTime }
                }.orEmpty()
                val calorieRecords = readSafely("活動卡路里", HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)) {
                    client.readAllRecords(ActiveCaloriesBurnedRecord::class, historyFilter)
                        .filter { it.startTime >= historyStart && it.endTime <= endTime }
                }.orEmpty()
                val stepTrendAssessment = SourceIntervalAggregation.assess(stepRecords.map {
                    SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.count.toDouble())
                })
                val distanceTrendAssessment = SourceIntervalAggregation.assess(distanceRecords.map {
                    SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.distance.inMeters)
                })
                val calorieTrendAssessment = SourceIntervalAggregation.assess(calorieRecords.map {
                    SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.energy.inKilocalories)
                })
                val stepTrendValues = stepRecords.takeIf { stepTrendAssessment.eligible }.orEmpty().sortedBy { it.startTime }.map { it.count.toDouble() }
                val distanceTrendValues = distanceRecords.takeIf { distanceTrendAssessment.eligible }.orEmpty().sortedBy { it.startTime }.map { it.distance.inMeters }
                val calorieTrendValues = calorieRecords.takeIf { calorieTrendAssessment.eligible }.orEmpty().sortedBy { it.startTime }.map { it.energy.inKilocalories }
                suspend fun <T : Any> aggregateAcrossOrigins(
                    label: String,
                    permission: String,
                    metric: AggregateMetric<T>
                ): Pair<T?, List<String>> {
                    if (permission !in granted) {
                        missingPermissions += label
                        return null to emptyList()
                    }
                    if (!HealthConnectAggregatePolicy.shouldQuery(
                            getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
                        )) return null to emptyList()
                    return try {
                        val result = client.aggregate(
                            AggregateRequest(metrics = setOf(metric), timeRangeFilter = filter)
                        )
                        result[metric] to result.dataOrigins.map { it.packageName }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failedReadLabels += label
                        readErrors += HealthConnectReadFailurePolicy.describe("$label 彙總", error)
                        null to emptyList()
                    }
                }
                val (aggregateSteps, aggregateStepOrigins) = aggregateAcrossOrigins(
                    "步數", HealthPermission.getReadPermission(StepsRecord::class), StepsRecord.COUNT_TOTAL
                )
                val (aggregateDistance, aggregateDistanceOrigins) = aggregateAcrossOrigins(
                    "距離", HealthPermission.getReadPermission(DistanceRecord::class), DistanceRecord.DISTANCE_TOTAL
                )
                val (aggregateCalories, aggregateCalorieOrigins) = aggregateAcrossOrigins(
                    "活動卡路里", HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class), ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                )
                val stepsAggregateAccepted = HealthConnectAggregatePolicy.acceptsPlatformTotal(aggregateSteps?.toDouble(), aggregateStepOrigins)
                val distanceAggregateAccepted = HealthConnectAggregatePolicy.acceptsPlatformTotal(aggregateDistance?.inMeters, aggregateDistanceOrigins)
                val caloriesAggregateAccepted = HealthConnectAggregatePolicy.acceptsPlatformTotal(aggregateCalories?.inKilocalories, aggregateCalorieOrigins)
                val steps = aggregateSteps?.takeIf { stepsAggregateAccepted }
                val latestDailySteps = RecentDailyMetricFallback.latestDailyTotal(
                    stepRecords.map { it.startTime to it.count },
                    java.time.ZoneId.systemDefault(),
                    java.time.LocalDate.now()
                )
                val distance = aggregateDistance?.inMeters?.takeIf { distanceAggregateAccepted }
                val calories = aggregateCalories?.inKilocalories?.takeIf { caloriesAggregateAccepted }
                val exercises = readSafely("運動工作階段", HealthPermission.getReadPermission(ExerciseSessionRecord::class)) {
                    client.readAllRecords(ExerciseSessionRecord::class, filter).size
                }
                val weightRecords = readSafely("體重", HealthPermission.getReadPermission(WeightRecord::class)) {
                    client.readAllRecords(WeightRecord::class, historyFilter)
                }.orEmpty()
                val latestWeight = weightRecords.maxByOrNull { it.time }
                val weightTrendRecords = HealthConnectAggregatePolicy.recordsForOrigin(
                    weightRecords, latestWeight?.metadata?.dataOrigin?.packageName
                ) { it.metadata.dataOrigin.packageName }.sortedBy { it.time }
                val bloodPressureRecords = readSafely("血壓", HealthPermission.getReadPermission(BloodPressureRecord::class)) {
                    client.readAllRecords(BloodPressureRecord::class, filter)
                }
                val bloodPressureNow = System.currentTimeMillis()
                val latestBloodPressure = bloodPressureRecords.orEmpty()
                    .filter { record ->
                        BloodPressureEvidencePolicy.isValid(
                            BloodPressureEvidencePolicy.Evidence(
                                record.systolic.inMillimetersOfMercury,
                                record.diastolic.inMillimetersOfMercury,
                                record.time.toEpochMilli(), record.metadata.dataOrigin.packageName,
                                BloodPressureEvidencePolicy.Origin.HEALTH_CONNECT
                            ), bloodPressureNow
                        )
                    }.maxWithOrNull(compareBy<BloodPressureRecord> { it.time }
                        .thenBy { it.metadata.dataOrigin.packageName })
                if (bloodPressureRecords != null) {
                    val bpCache = getSharedPreferences(BloodPressureEvidencePolicy.HC_PREFS, MODE_PRIVATE).edit().clear()
                    latestBloodPressure?.let { record ->
                        bpCache.putString("systolic", record.systolic.inMillimetersOfMercury.toString())
                            .putString("diastolic", record.diastolic.inMillimetersOfMercury.toString())
                            .putLong("measured_at", record.time.toEpochMilli())
                            .putString("source", record.metadata.dataOrigin.packageName)
                    }
                    bpCache.apply()
                }
                val latestRestingHeartRate = readSafely("靜止心率", HealthPermission.getReadPermission(RestingHeartRateRecord::class)) {
                    client.readAllRecords(RestingHeartRateRecord::class, filter)
                }.orEmpty()
                    .maxByOrNull { it.time }
                val latestHrv = readSafely("HRV", HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)) {
                    client.readAllRecords(HeartRateVariabilityRmssdRecord::class, filter)
                }.orEmpty()
                    .maxByOrNull { it.time }
                val latestRespiratoryRate = readSafely("呼吸速率", HealthPermission.getReadPermission(RespiratoryRateRecord::class)) {
                    client.readAllRecords(RespiratoryRateRecord::class, filter)
                }.orEmpty()
                    .maxByOrNull { it.time }
                val oxygenRecords = readSafely("血氧", HealthPermission.getReadPermission(OxygenSaturationRecord::class)) {
                    client.readAllRecords(OxygenSaturationRecord::class, historyFilter)
                }.orEmpty()
                val latestOxygen = oxygenRecords.maxByOrNull { it.time }
                val oxygenTrendRecords = HealthConnectAggregatePolicy.recordsForOrigin(
                    oxygenRecords, latestOxygen?.metadata?.dataOrigin?.packageName
                ) { it.metadata.dataOrigin.packageName }.sortedBy { it.time }
                latestOxygen?.let { record ->
                    val referencePrefs = RawSensorStore.open(this@MainActivity)
                    HealthConclusionEngine.persistSpO2Reference(
                        referencePrefs,
                        record.percentage.value.toFloat(),
                        record.time.toEpochMilli(),
                        record.metadata.dataOrigin.packageName
                    )
                }
                val temperatureRecords = readSafely("體表溫度", HealthPermission.getReadPermission(BodyTemperatureRecord::class)) {
                    client.readAllRecords(BodyTemperatureRecord::class, historyFilter)
                        .filter { it.metadata.dataOrigin.packageName != packageName }
                }.orEmpty()
                val latestTemperature = temperatureRecords.maxByOrNull { it.time }
                val watchFeature = getSharedPreferences(WearDataProcessor.FEATURE_PREFS, MODE_PRIVATE)
                val watchTemperature = DeviceMetricFallback.temperatureC(watchFeature, System.currentTimeMillis())
                val watchTemperatureUnitUnverified = watchFeature.contains("temperature") &&
                    watchFeature.getString("temperature_unit_status", "unverified") !=
                    DeviceMetricFallback.VERIFIED_CELSIUS_UNIT_STATUS
                val elevationRecords = readSafely("爬升", HealthPermission.getReadPermission(ElevationGainedRecord::class)) {
                    client.readAllRecords(ElevationGainedRecord::class, filter)
                }
                val floorRecords = readSafely("樓層", HealthPermission.getReadPermission(FloorsClimbedRecord::class)) {
                    client.readAllRecords(FloorsClimbedRecord::class, filter)
                }
                val recentElevationRecords = if (elevationRecords.isNullOrEmpty()) {
                    readSafely("爬升", HealthPermission.getReadPermission(ElevationGainedRecord::class)) {
                        client.readAllRecords(ElevationGainedRecord::class, historyFilter)
                    }.orEmpty()
                } else emptyList()
                val recentFloorRecords = if (floorRecords.isNullOrEmpty()) {
                    readSafely("樓層", HealthPermission.getReadPermission(FloorsClimbedRecord::class)) {
                        client.readAllRecords(FloorsClimbedRecord::class, historyFilter)
                    }.orEmpty()
                } else emptyList()
                val hydrationRecords = readSafely("補水", HealthPermission.getReadPermission(HydrationRecord::class)) {
                    client.readAllRecords(HydrationRecord::class, filter)
                }
                val (elevationAggregate, elevationAggregateOrigins) = aggregateAcrossOrigins(
                    "爬升", HealthPermission.getReadPermission(ElevationGainedRecord::class), ElevationGainedRecord.ELEVATION_GAINED_TOTAL
                )
                val (floorAggregate, floorAggregateOrigins) = aggregateAcrossOrigins(
                    "樓層", HealthPermission.getReadPermission(FloorsClimbedRecord::class), FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL
                )
                val (hydrationAggregate, hydrationAggregateOrigins) = aggregateAcrossOrigins(
                    "補水", HealthPermission.getReadPermission(HydrationRecord::class), HydrationRecord.VOLUME_TOTAL
                )
                val elevationMeasurements = elevationRecords.orEmpty().map {
                    SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.elevation.inMeters)
                }
                val floorMeasurements = floorRecords.orEmpty().map {
                    SourceIntervalMeasurement(it.metadata.dataOrigin.packageName, it.startTime, it.endTime, it.floors.toDouble())
                }
                val elevationAssessment = SourceIntervalAggregation.assess(elevationMeasurements)
                val floorAssessment = SourceIntervalAggregation.assess(floorMeasurements)
                val elevationAccepted = HealthConnectAggregatePolicy.acceptsDeduplicatedActivityTotal(elevationAggregate?.inMeters, elevationAggregateOrigins)
                val floorAccepted = HealthConnectAggregatePolicy.acceptsDeduplicatedActivityTotal(floorAggregate?.toDouble(), floorAggregateOrigins)
                val elevationMeters = elevationAggregate?.inMeters?.takeIf { elevationAccepted }
                val floors = floorAggregate?.takeIf { floorAccepted }
                val latestRecentElevation = recentElevationRecords.maxByOrNull { it.endTime }
                val latestRecentFloors = recentFloorRecords.maxByOrNull { it.endTime }
                val deviceFloorChange = DeviceMetricFallback.pressureFloorChange(
                    RawSensorStore.open(this@MainActivity), System.currentTimeMillis()
                )
                val validHydrationRecords = hydrationRecords.orEmpty().filter { it.volume.inMilliliters.isFinite() && it.volume.inMilliliters > 0.0 }
                val hydrationRecordOrigins = validHydrationRecords.map { it.metadata.dataOrigin.packageName }
                val hydrationRecordTotal = hydrationRecordOrigins.takeIf { it.isNotEmpty() }
                    ?.let { validHydrationRecords.sumOf { record -> record.volume.inMilliliters } }
                val hydrationMilliliters = hydrationAggregate?.inMilliliters?.takeIf {
                    HealthConnectAggregatePolicy.acceptsSingleOriginRecordAggregate(
                        it, hydrationAggregateOrigins, hydrationRecordTotal, hydrationRecordOrigins
                    )
                }
                val sleepSessions = readSafely("睡眠", HealthPermission.getReadPermission(SleepSessionRecord::class)) {
                    client.readAllRecords(SleepSessionRecord::class, historyFilter)
                        .let { sessions ->
                            HealthConnectAggregatePolicy.completeIntervalsWithinRange(
                                sessions, historyStart, endTime, { it.startTime }, { it.endTime }
                            )
                        }
                }.orEmpty()
                val (aggregateSleepDuration, aggregateSleepOrigins) = aggregateAcrossOrigins(
                    "睡眠", HealthPermission.getReadPermission(SleepSessionRecord::class),
                    SleepSessionRecord.SLEEP_DURATION_TOTAL
                )
                val aggregateSleepMinutes = aggregateSleepDuration?.toMillis()?.div(60_000.0)
                val sleepAggregateAccepted = HealthConnectAggregatePolicy.acceptsPlatformTotal(aggregateSleepMinutes, aggregateSleepOrigins)
                val sleepSessionMeasurements = sleepSessions.map {
                    SourceIntervalMeasurement(
                        it.metadata.dataOrigin.packageName,
                        it.startTime,
                        it.endTime,
                        java.time.Duration.between(it.startTime, it.endTime).toMinutes().toDouble()
                    )
                }
                val sleepSessionAssessment = SourceIntervalAggregation.assess(sleepSessionMeasurements)
                val sleepSessionMinutes = sleepSessionMeasurements.takeIf { sleepSessionAssessment.eligible }
                    ?.sumOf { it.value }
                val sleepUnionMinutes = sleepSessions.map { it.startTime to it.endTime }
                    .sortedBy { it.first }
                    .fold(mutableListOf<Pair<java.time.Instant, java.time.Instant>>()) { merged, interval ->
                        val previous = merged.lastOrNull()
                        if (previous == null || interval.first.isAfter(previous.second)) merged += interval
                        else if (interval.second.isAfter(previous.second)) merged[merged.lastIndex] = previous.first to interval.second
                        merged
                    }
                    .sumOf { java.time.Duration.between(it.first, it.second).toMinutes().toDouble() }
                    .takeIf { it > 0.0 }
                val sleepHours = aggregateSleepMinutes?.takeIf { sleepAggregateAccepted }?.div(60.0)
                    ?: (sleepSessionMinutes ?: sleepUnionMinutes)?.div(60.0)
                val sleepValueSource = if (sleepAggregateAccepted) "Health Connect 平台彙總" else if (sleepSessionMinutes != null) "近 7 天完整睡眠區間加總（非平台彙總）" else if (sleepUnionMinutes != null) "近 7 天合併重疊後的睡眠區間（非平台彙總）" else aggregateUnavailableReason("睡眠", aggregateSleepMinutes, aggregateSleepOrigins)
                getSharedPreferences("health_summary_query_diagnostics", MODE_PRIVATE).edit()
                    .putLong("query_started_at_ms", endTime.toEpochMilli())
                    .putLong("today_start_ms", todayStart.toEpochMilli())
                    .putLong("history_start_ms", historyStart.toEpochMilli())
                    .putInt("oxygen_today_records", oxygenRecords.size)
                    .putString("oxygen_read_state", if (oxygenRecords.isNotEmpty()) "RECORDS_RECEIVED" else unavailableState("血氧"))
                    .putInt("sleep_complete_history_records", sleepSessions.size)
                    .putString("sleep_read_state", if (sleepSessions.isNotEmpty()) "COMPLETE_INTERVAL_RECORDS_RECEIVED" else unavailableState("睡眠"))
                    .putString("sleep_aggregate_state", aggregateUnavailableReason("睡眠", aggregateSleepMinutes, aggregateSleepOrigins))
                    .putBoolean("sleep_aggregate_accepted", sleepAggregateAccepted)
                    .putBoolean("sleep_session_fallback_used", !sleepAggregateAccepted && (sleepSessionMinutes != null || sleepUnionMinutes != null))
                    .putString("sleep_session_fallback_hours", (sleepSessionMinutes ?: sleepUnionMinutes)?.div(60.0)?.toString() ?: "")
                    .putInt("sleep_aggregate_source_count", aggregateSleepOrigins.distinct().size)
                    .putString("read_failures", readErrors.joinToString("；").take(2000))
                    .putString("missing_permissions", missingPermissions.joinToString("、").take(1000))
                    .apply()
                val aggregateSourceSummary = listOf(
                    "步數" to aggregateStepOrigins,
                    "距離" to aggregateDistanceOrigins,
                    "卡路里" to aggregateCalorieOrigins,
                    "睡眠" to aggregateSleepOrigins,
                    "爬升" to elevationAggregateOrigins,
                    "樓層" to floorAggregateOrigins,
                    "補水" to hydrationAggregateOrigins
                ).mapNotNull { (label, origins) ->
                    origins.distinct().takeIf { it.isNotEmpty() }?.let { sources -> "$label：${sources.joinToString("、") { friendlySource(it) }}" }
                }.ifEmpty { listOf("本次沒有彙總來源") }.joinToString("；")
                val sleepTrendAssessment = sleepSessionAssessment
                val sleepTrendValues = sleepSessions.takeIf { sleepTrendAssessment.eligible }.orEmpty().sortedBy { it.startTime }.map {
                    java.time.Duration.between(it.startTime, it.endTime).toMinutes().toDouble()
                }
                val heartSampleCount = heartRateSummarySamples.count { it.bpm.isFinite() && it.bpm > 0.0 }
                val nowMillis = System.currentTimeMillis()
                val heartSummary = HeartRateHomeSummaryPolicy.resolve(
                    heartRateSummarySamples,
                    heartRateAssessment,
                    java.time.Instant.ofEpochMilli(nowMillis)
                )
                val rawHeartRatePrefs = RawSensorStore.open(this@MainActivity)
                heartRateSummarySamples.asSequence()
                    .filter { it.source.isNotBlank() && it.bpm.isFinite() && it.bpm in 30.0..220.0 &&
                        it.time.toEpochMilli() in (nowMillis - HeartRatePresentationPolicy.HOME_HISTORY_WINDOW_MILLIS)..nowMillis }
                    .maxByOrNull { it.time }
                    ?.let { reading ->
                    rawHeartRatePrefs.edit()
                        .putFloat("health_connect_heart_rate_bpm", reading.bpm.toFloat())
                        .putLong("health_connect_heart_rate_time", reading.time.toEpochMilli())
                        .putString("health_connect_heart_rate_origin", reading.source)
                        .apply()
                }
                val watchHealthServicesTime = watchFeature.getLong("whs_heart_rate_timestamp", 0L)
                val watchHealthServicesBpm = HeartRatePresentationPolicy.visibleHealthServicesBpm(
                    watchFeature.getFloat("whs_heart_rate", Float.NaN),
                    watchHealthServicesTime,
                    watchFeature.getString("whs_heart_rate_timestamp_source", null),
                    nowMillis,
                    HeartRatePresentationPolicy.HOME_HISTORY_WINDOW_MILLIS
                )
                val watchRawHeartRateTime = rawHeartRatePrefs.getLong("heart_rate_raw_time", 0L)
                val watchRawHeartRateBpm = HeartRatePresentationPolicy.visibleRawSensorBpm(
                    rawHeartRatePrefs.getFloat("heart_rate_raw_formula_pulse_bpm", Float.NaN)
                        .takeIf { it.isFinite() } ?: rawHeartRatePrefs.getFloat("heart_rate_raw_average", Float.NaN),
                    rawHeartRatePrefs.getString("heart_rate_raw_status", "").orEmpty(),
                    watchRawHeartRateTime,
                    rawHeartRatePrefs.getString("heart_rate_raw_timestamp_source", null),
                    nowMillis
                )
                val latestWatchHeartRate = listOfNotNull(
                    watchHealthServicesBpm?.let { it.toDouble() to watchHealthServicesTime },
                    watchRawHeartRateBpm?.let { it.toDouble() to watchRawHeartRateTime }
                ).maxByOrNull { it.second }
                val heartRateCardSummary = HeartRateHomeSummaryPolicy.withRecentWatchReading(
                    heartSummary,
                    listOf(
                        HeartRateHomeSummaryPolicy.WatchCandidate(
                            watchFeature.getFloat("whs_heart_rate", Float.NaN),
                            watchHealthServicesTime,
                            "whs_heart_rate",
                            watchFeature.getString("whs_heart_rate_timestamp_source", null),
                            watchFeature.getString("whs_heart_rate_status", "").orEmpty()
                        ),
                        HeartRateHomeSummaryPolicy.WatchCandidate(
                            rawHeartRatePrefs.getFloat("heart_rate_raw_formula_pulse_bpm", Float.NaN)
                                .takeIf { it.isFinite() } ?: rawHeartRatePrefs.getFloat("heart_rate_raw_average", Float.NaN),
                            watchRawHeartRateTime,
                            "heart_rate_raw",
                            rawHeartRatePrefs.getString("heart_rate_raw_timestamp_source", null),
                            rawHeartRatePrefs.getString("heart_rate_raw_status", "").orEmpty()
                        )
                    ),
                    nowMillis
                )
                heartRateCard.text = metricText(
                    "心率",
                    heartRateCardSummary.value,
                    heartRateCardSummary.detail,
                    TrendSampleEncoder.encode(heartRateCardSummary.trendValues.asSequence(), heartRateCardSummary.trendValues.size)
                )
                restingHeartRateCard.text = metricText(
                    "靜止心率",
                    latestRestingHeartRate?.beatsPerMinute?.toString() ?: "— —",
                    latestRestingHeartRate?.let { reading ->
                        val reference = HeartRateRestingReferencePolicy.assess(
                            reading.beatsPerMinute.toDouble(),
                            UserProfileStore(this@MainActivity).read()?.age
                        )
                        "${reference.label} · ${friendlySource(reading.metadata.dataOrigin.packageName)} · ${HeartRatePresentationPolicy.homeTimestampLabel(reading.time.toEpochMilli(), nowMillis)} · 不作疾病判斷"
                    } ?: "等待有效靜息心率紀錄",
                    latestRestingHeartRate?.let { sparkline(listOf(it.beatsPerMinute.toDouble())) } ?: EMPTY_TREND
                )
                oxygenCard.text = metricText(
                    "血氧濃度 (SpO₂)",
                    latestOxygen?.let { "%.0f%%".format(it.percentage.value) } ?: "— —",
                    latestOxygen?.let {
                        "${friendlySource(it.metadata.dataOrigin.packageName)} · 最近紀錄 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date.from(it.time))}；非即時量測"
                    } ?: "今日 Health Connect：${unavailableState("血氧")}",
                    sparkline(oxygenTrendRecords.map { it.percentage.value })
                )
                weightCard.text = metricText(
                    "體重",
                    latestWeight?.let { "%.1f".format(it.weight.inKilograms) } ?: "— —",
                    if (latestWeight == null) "kg · Health Connect：${unavailableState("體重")}" else "kg · ${friendlySource(latestWeight.metadata.dataOrigin.packageName)}",
                    sparkline(weightTrendRecords.map { it.weight.inKilograms })
                )
                if (latestWeight == null) {
                    UserProfileStore(this@MainActivity).read()?.let { profile ->
                        weightCard.text = metricText("體重（個人設定）", "%.1f".format(profile.weightKg),
                            "kg · 使用你儲存的個人資料；非本次量測，更新日期未記錄", EMPTY_TREND)
                    }
                }
                stepsCard.text = metricText("步數", steps?.let { "%,d".format(it) } ?: "— —", "步 · ${aggregateUnavailableReason("步數", aggregateSteps?.toDouble(), aggregateStepOrigins)}；7 日原始紀錄趨勢${trendStatusSuffix(stepTrendAssessment)}", sparkline(stepTrendValues))
                caloriesCard.text = metricText("消耗的熱量", calories?.let { "%,.0f".format(it) } ?: "— —", "大卡 · ${aggregateUnavailableReason("活動卡路里", aggregateCalories?.inKilocalories, aggregateCalorieOrigins)}；7 日原始紀錄趨勢${trendStatusSuffix(calorieTrendAssessment)}", sparkline(calorieTrendValues))
                sleepCard.text = metricText(
                    "睡眠時段", sleepHours?.let { "%.1f".format(it) } ?: "— —", "小時 · Health Connect session 時長（未依睡眠階段核算實睡時數）· $sleepValueSource；7 日完整 session 趨勢${trendStatusSuffix(sleepTrendAssessment)}",
                    sparkline(sleepTrendValues)
                )
                distanceCard.text = metricText("距離", distance?.let { "%.2f".format(it / 1000) } ?: "— —", "km · ${aggregateUnavailableReason("距離", aggregateDistance?.inMeters, aggregateDistanceOrigins)}；7 日原始紀錄趨勢${trendStatusSuffix(distanceTrendAssessment)}", sparkline(distanceTrendValues))
                temperatureCard.text = metricText(
                    "體表溫度",
                    latestTemperature?.let { "%.1f".format(it.temperature.inCelsius) }
                        ?: watchTemperature?.let { "%.1f".format(it) } ?: "— —",
                    when {
                        latestTemperature != null -> "°C · 最近 7 天"
                        watchTemperature != null -> "°C · 已驗證裝置值 · 最近 24 小時"
                        watchTemperatureUnitUnverified -> "已收到裝置原始溫度值；單位未驗證，不換算 °C"
                        else -> "Health Connect：${unavailableState("體表溫度")}"
                    },
                    sparkline(temperatureRecords.sortedBy { it.time }.map { it.temperature.inCelsius }.ifEmpty {
                        watchTemperature?.toDouble()?.let(::listOf).orEmpty()
                    })
                )
                elevationCard.text = metricText(
                    if (elevationMeters == null && latestRecentElevation != null) "最近爬升" else "爬升",
                    elevationMeters?.let { "%.0f".format(it) }
                        ?: latestRecentElevation?.let { "%.0f".format(it.elevation.inMeters) } ?: "— —",
                    if (elevationMeters != null) "m · 今日 Health Connect 彙總"
                    else if (latestRecentElevation != null) "m · 近 7 日最近紀錄"
                    else "m · ${aggregateUnavailableReason("爬升", elevationAggregate?.inMeters, elevationAggregateOrigins)}",
                    sparkline(elevationMeasurements.takeIf { elevationAssessment.eligible }?.map { it.value }.orEmpty())
                )
                floorsCard.text = metricText(
                    when {
                        floors != null -> "樓層"
                        latestRecentFloors != null -> "最近樓層"
                        deviceFloorChange != null -> "樓層變化（估算）"
                        else -> "樓層"
                    },
                    floors?.let { "%.0f".format(it) }
                        ?: latestRecentFloors?.let { "%.1f".format(it.floors) }
                        ?: deviceFloorChange?.let { "%+.1f".format(it) } ?: "— —",
                    if (floors != null) "層 · 今日 Health Connect 彙總"
                    else if (latestRecentFloors != null) "層 · 近 7 日最近紀錄"
                    else if (deviceFloorChange != null) "層 · 本次氣壓變化 ÷ 3 m；非實際樓梯計數"
                    else "層 · ${aggregateUnavailableReason("樓層", floorAggregate?.toDouble(), floorAggregateOrigins)}",
                    sparkline(floorMeasurements.takeIf { floorAssessment.eligible }?.map { it.value }.orEmpty())
                )
                if (floors == null && elevationMeters != null && elevationMeters.isFinite() && elevationMeters >= 0.0) {
                    floorsCard.text = metricText("樓層（推估）", "%.1f".format(elevationMeters / 3.0),
                        "層 · 當日有效爬升 ÷ 假設每層 3 m；含戶外爬升，非實際樓梯層數", EMPTY_TREND)
                }
                val hydrationStatus = when {
                    hydrationMilliliters != null -> "Health Connect 單一來源手動紀錄彙總"
                    hydrationAggregate != null && HealthConnectAggregatePolicy.singleOriginOrNull(hydrationAggregateOrigins) == null -> "多來源總和未去重；為避免重複計算已隱藏"
                    hydrationAggregate != null -> "原始紀錄與彙總不一致或無法驗證"
                    else -> "Health Connect：${unavailableState("補水")}"
                }
                hydrationCard.text = metricText("補水（手動）", hydrationMilliliters?.let { "%.0f".format(it) } ?: "— —", "mL · $hydrationStatus", sparkline(validHydrationRecords.map { it.volume.inMilliliters }))
                fun summaryUnavailable(label: String): String = when {
                    label in missingPermissions -> "尚未授權"
                    label in failedReadLabels -> "查詢失敗"
                    label in emptyReads -> "查詢期間無紀錄"
                    else -> "資料需核對"
                }
                val stepSummaryValue = when {
                    steps != null -> "%,d · 今日".format(steps)
                    latestDailySteps != null -> "%,d · ${latestDailySteps.date}".format(latestDailySteps.total)
                    else -> summaryUnavailable("步數")
                }
                val oxygenSummaryValue = latestOxygen?.let {
                    val periodLabel = if (it.time.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()) "今日" else "${DateFormat.getDateInstance(DateFormat.SHORT).format(Date.from(it.time))}"
                    "%.0f%% · %s".format(it.percentage.value, periodLabel)
                } ?: summaryUnavailable("血氧")
                stepsSummary.text = summaryText("步數", stepSummaryValue)
                oxygenSummary.text = summaryText("血氧", oxygenSummaryValue)
                sleepSummary.text = summaryText("睡眠時段", sleepHours?.let { "%.1f 小時".format(it) } ?: summaryUnavailable("睡眠"))
                val latestHeartRateLabel = when {
                        heartRateCardSummary.detail.startsWith("近 5 分鐘手錶讀值") && latestWatchHeartRate != null ->
                            "${heartRateCardSummary.value} · 手錶 ${HeartRatePresentationPolicy.homeTimestampLabel(latestWatchHeartRate.second, nowMillis)}"
                        heartSummary.measuredAt != null -> "${heartSummary.value} bpm · ${HeartRatePresentationPolicy.homeTimestampLabel(heartSummary.measuredAt.toEpochMilli(), nowMillis)} · 24 小時"
                        latestWatchHeartRate != null -> "${"%.0f".format(latestWatchHeartRate.first)} bpm · 手錶 ${HeartRatePresentationPolicy.homeTimestampLabel(latestWatchHeartRate.second, nowMillis)}"
                        else -> summaryUnavailable("心率")
                    }
                val todayHeartRateLabel = todayHeartRateRange?.let {
                    "今日最低／最高 ${"%.0f".format(it.minimumBpm)}–${"%.0f".format(it.maximumBpm)} bpm · ${it.sampleCount} 筆${if (it.sourceCount > 1) " · ${it.sourceCount} 個來源" else ""}"
                } ?: "今日最低／最高：本日無有效紀錄"
                heartRateSummary.text = summaryText("心率", "$latestHeartRateLabel\n$todayHeartRateLabel")
                metricsStatus.text = buildString {
                    append("今日 Health Connect 平台彙總 · 心率查詢近 24 小時\n")
                    append("規則 ${HealthConnectAggregatePolicy.RULE_VERSION} · 本次查詢來源：$aggregateSourceSummary\n")
                    append("心率樣本  $heartSampleCount 筆\n")
                    append("步數  ${steps?.let { "%,d".format(it) } ?: "— —"} 步\n")
                    append("距離  ${distance?.let { "%.2f".format(it / 1000) } ?: "— —"} km\n")
                    append("活動卡路里  ${calories?.let { "%.0f".format(it) } ?: "— —"} kcal\n")
                    append("運動工作階段  ${exercises?.toString() ?: "— —"} 次")
                    append("\n體重  ${latestWeight?.let { "%.1f kg".format(it.weight.inKilograms) } ?: unavailableState("體重")}")
                    append("\n血壓  ${latestBloodPressure?.let {
                        "%.0f/%.0f mmHg".format(it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury)
                    } ?: "Health Connect：${unavailableState("血壓")}"}")
                    append("\n靜止心率  ${latestRestingHeartRate?.let { "${it.beatsPerMinute} bpm" } ?: unavailableState("靜止心率")}")
                    append("\nHRV RMSSD  ${latestHrv?.let { "%.1f ms".format(it.heartRateVariabilityMillis) } ?: unavailableState("HRV")}")
                    append("\n呼吸速率  ${latestRespiratoryRate?.let { "%.1f 次／分鐘".format(it.rate) } ?: unavailableState("呼吸速率")}")
                    append("\n血氧  ${latestOxygen?.let {
                        "%.1f%%（來源：%s；%s）".format(
                            it.percentage.value,
                            friendlySource(it.metadata.dataOrigin.packageName),
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date.from(it.time))
                        )
                    } ?: unavailableState("血氧")}")
                    append("\n睡眠時段（Health Connect session 時長，未依睡眠階段核算實睡時數）  ${sleepHours?.let { "%.1f 小時".format(it) } ?: "無可用平台彙總"}")
                    if (missingPermissions.isNotEmpty()) append("\n尚未授權：${missingPermissions.joinToString("、")}")
                    if (readErrors.isNotEmpty()) append("\n讀取失敗：${readErrors.joinToString("、")}")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                metricsStatus.text = "健康資料摘要讀取失敗：${error::class.simpleName ?: "未知錯誤"}"
                stepsSummary.text = summaryText("步數", "查詢失敗")
                oxygenSummary.text = summaryText("血氧", "查詢失敗")
                sleepSummary.text = summaryText("睡眠時段", "查詢失敗")
                heartRateSummary.text = summaryText("心率", "查詢失敗")
                val states = HealthSummaryReadStatePolicy.dataQueryFailureCards()
                listOf(weightCard to "weight", stepsCard to "steps", caloriesCard to "calories",
                    sleepCard to "sleep", heartRateCard to "heart_rate", restingHeartRateCard to "resting_heart_rate",
                    oxygenCard to "oxygen", distanceCard to "distance", temperatureCard to "temperature",
                    elevationCard to "elevation", floorsCard to "floors", hydrationCard to "hydration"
                ).forEach { (card, key) ->
                    val state = states.getValue(key)
                    card.text = metricText(state.title, state.value, state.detail, state.trend)
                }
            }
        }
    }

    private fun showWeightDialog() {
        val input = numericInput("例如 70.5")
        AlertDialog.Builder(this).setTitle("手動輸入體重（kg）").setView(input)
            .setNegativeButton("取消", null).setPositiveButton("寫入") { _, _ ->
                val value = input.text.toString().toDoubleOrNull()
                if (value == null) toast("請輸入有效數字") else lifecycleScope.launch {
                    ManualEntryWriter(this@MainActivity).writeWeight(value)
                        .onSuccess { toast("體重已寫入 Health Connect"); loadSevenDaySummary(force = true) }
                        .onFailure { toast(it.message ?: "寫入失敗") }
                }
            }.show()
    }

    private fun showPhysiologyProfileDialog() {
        val saved = UserProfileStore(this).read()
        val age = numericInput("年齡").apply { setText(saved?.age?.toString().orEmpty()) }
        val height = numericInput("身高（cm）").apply { setText(saved?.heightCm?.let { "%.1f".format(it) }.orEmpty()) }
        val weight = numericInput("體重（kg）").apply { setText(saved?.weightKg?.let { "%.1f".format(it) }.orEmpty()) }
        val sex = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("未指定", "男性", "女性"))
            setSelection(when(saved?.sex) { PhysiologyAlgorithms.Sex.MALE -> 1; PhysiologyAlgorithms.Sex.FEMALE -> 2; else -> 0 })
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(age); addView(sex); addView(height); addView(weight)
        }
        AlertDialog.Builder(this).setTitle("生理演算法基本資料")
            .setMessage("只保存在此手機，用於 BMR、心率區間與恢復估算；不會上傳到本 App 的伺服器。")
            .setView(fields).setNegativeButton("取消", null).setPositiveButton("儲存") { _, _ ->
                val profile = UserProfileStore.Profile(
                    age.text.toString().toIntOrNull() ?: 0,
                    when(sex.selectedItemPosition) { 1 -> PhysiologyAlgorithms.Sex.MALE; 2 -> PhysiologyAlgorithms.Sex.FEMALE; else -> PhysiologyAlgorithms.Sex.UNSPECIFIED },
                    height.text.toString().toDoubleOrNull() ?: 0.0,
                    weight.text.toString().toDoubleOrNull() ?: 0.0
                )
                if(profile.age !in 13..100 || profile.heightCm !in 100.0..230.0 || profile.weightKg !in 25.0..350.0) {
                    toast("請確認年齡、身高與體重範圍")
                } else {
                    UserProfileStore(this).save(profile)
                    toast("基本資料已儲存於手機")
                    loadAlgorithmicInsights(force = true)
                }
            }.show()
    }

    private fun showBloodPressureDialog() {
        val systolic = numericInput("收縮壓，例如 120")
        val diastolic = numericInput("舒張壓，例如 80")
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0)
            addView(systolic); addView(diastolic)
        }
        AlertDialog.Builder(this).setTitle("輸入外部血壓計讀值（mmHg）").setMessage("請勿輸入由心率推算的數值。")
            .setView(fields).setNegativeButton("取消", null).setPositiveButton("寫入") { _, _ ->
                val upper = systolic.text.toString().toDoubleOrNull()
                val lower = diastolic.text.toString().toDoubleOrNull()
                if (upper == null || lower == null) toast("請輸入有效數字") else lifecycleScope.launch {
                    getSharedPreferences(OpticalBpActivity.CUFF_PREFS, MODE_PRIVATE).edit()
                        .putFloat("manual_cuff_sbp", upper.toFloat())
                        .putFloat("manual_cuff_dbp", lower.toFloat())
                        .putLong("manual_cuff_time", System.currentTimeMillis())
                        .apply()
                    ManualEntryWriter(this@MainActivity).writeBloodPressure(upper, lower)
                        .onSuccess { toast("血壓已寫入 Health Connect"); loadSevenDaySummary(force = true) }
                        .onFailure { toast(it.message ?: "寫入失敗") }
                }
            }.show()
    }

    private fun showSingleValueDialog(
        title: String,
        unit: String,
        writer: suspend (Double) -> Result<Unit>
    ) {
        val input = numericInput("輸入 $title（$unit）")
        AlertDialog.Builder(this).setTitle("手動輸入$title").setMessage("僅輸入外部可信裝置的量測值，本 App 不會推算。")
            .setView(input).setNegativeButton("取消", null).setPositiveButton("寫入") { _, _ ->
                val value = input.text.toString().toDoubleOrNull()
                if (value == null) toast("請輸入有效數字") else lifecycleScope.launch {
                    writer(value).onSuccess { toast("$title 已寫入 Health Connect"); loadSevenDaySummary(force = true) }
                        .onFailure { toast(it.message ?: "寫入失敗") }
                }
            }.show()
    }

    private fun numericInput(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun actionButton(textValue: String, click: () -> Unit) = Button(this).apply {
        text = "$textValue   ›"; textSize = 14f; isAllCaps = false; setTextColor(Color.parseColor("#DFE7F7"))
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(dp(18), dp(14), dp(18), dp(14)); minHeight = dp(54)
        background = HealthVisuals.interactive(context)
        stateListAnimator = null
        setOnClickListener { click() }
        installTouchFeedback(this)
    }

    private fun metricDashboard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(metricRow(heartRateCard, restingHeartRateCard))
        addView(metricRow(oxygenCard, weightCard), margins(top = 10))
        addView(metricRow(stepsCard, caloriesCard), margins(top = 10))
        addView(metricRow(sleepCard, distanceCard), margins(top = 10))
        addView(metricRow(temperatureCard, elevationCard), margins(top = 10))
        addView(metricRow(floorsCard, hydrationCard), margins(top = 10))
        addView(featureRow("AI", "查看健康分析", "集中查看健康趨勢、演算法結果與生活建議", "#5AC8FA") {
            startActivity(Intent(this@MainActivity, AiHealthAnalysisActivity::class.java))
        }, margins(top = 22))
        post { for (index in 0 until childCount) getChildAt(index).apply {
            alpha = 0f; translationY = dp(14).toFloat()
            animate().alpha(1f).translationY(0f).setStartDelay(index * 70L).setDuration(360).start()
        } }
    }

    private fun todayOverview() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = HealthVisuals.panel(context)
        listOf(
            OverviewMetric(heartRateSummary, "heart_rate", "心率", "#EF5364"),
            OverviewMetric(stepsSummary, "steps", "步數", "#EAC879"),
            OverviewMetric(oxygenSummary, "oxygen", "血氧", "#49D4AB"),
            OverviewMetric(sleepSummary, "sleep", "睡眠", "#AC9BFF")
        ).forEachIndexed { index, metric ->
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
                addView(MetricGlyphView(context, metric.title, Color.parseColor(metric.color)), LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
                addView(metric.summary, LinearLayout.LayoutParams(0, -2, 1f))
                addView(label("›", 22f, "#697286"))
                foreground = getDrawable(android.R.drawable.list_selector_background)
                setOnClickListener { startActivity(android.content.Intent(this@MainActivity, MetricDetailActivity::class.java).putExtra("metric", metric.id)) }
            }, LinearLayout.LayoutParams(-1, -2))
            if (index < 3) addView(View(context).apply { setBackgroundColor(Color.parseColor("#30333C")) },
                LinearLayout.LayoutParams(-1, 1).apply { marginStart = dp(62); marginEnd = dp(18) })
        }
    }

    private data class OverviewMetric(val summary: TextView, val id: String, val title: String, val color: String)

    private fun summaryChip(title: String, value: String, color: String) = TextView(this).apply {
        text = summaryText(title, value)
        textSize = 16f
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.WHITE)
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun summaryText(title: String, value: String): CharSequence =
        android.text.SpannableString("$title\n$value").apply {
            setSpan(android.text.style.RelativeSizeSpan(.72f), 0, title.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.ForegroundColorSpan(HealthVisuals.secondary), 0, title.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.StyleSpan(Typeface.BOLD), title.length + 1, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun darken(color: String): String {
        val parsed = Color.parseColor(color)
        return String.format(
            "#%02X%02X%02X",
            (Color.red(parsed) * .72).toInt(),
            (Color.green(parsed) * .72).toInt(),
            (Color.blue(parsed) * .72).toInt()
        )
    }

    private fun metricRow(first: MetricCardView, second: MetricCardView) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(7) })
        addView(second, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(7) })
    }

    private fun metricCard(title: String, value: String, detail: String, accent: String, trend: String) =
        MetricCardView(metricIcon(title) + "  " + title, accent).apply { text = metricText(metricIcon(title) + "  " + title, value, detail, trend) }

    private fun metricIcon(title: String) = when {
        title.contains("心率") -> "♥"
        title.contains("血氧") -> "◉"
        title.contains("體重") -> "⚖"
        title.contains("步數") -> "⌁"
        title.contains("熱量") -> "◆"
        title.contains("睡眠") -> "☾"
        title.contains("距離") -> "↗"
        else -> "•"
    }

    private fun wireMetricDetails() {
        listOf(
            heartRateCard to "heart_rate", restingHeartRateCard to "resting_heart_rate",
            oxygenCard to "oxygen", weightCard to "weight", stepsCard to "steps",
            caloriesCard to "calories", sleepCard to "sleep", distanceCard to "distance"
        ).forEach { (card, metric) ->
            card.setOnClickListener {
                startActivity(android.content.Intent(this, MetricDetailActivity::class.java).putExtra("metric", metric))
            }
        }
    }

    private fun trendStatusSuffix(assessment: SourceIntervalAggregation.Assessment): String = when (assessment.rejection) {
        null -> ""
        SourceIntervalAggregation.Rejection.NO_RECORDS -> " · 尚無趨勢紀錄"
        SourceIntervalAggregation.Rejection.MULTIPLE_DATA_ORIGINS -> " · 多來源，趨勢暫不合併"
        SourceIntervalAggregation.Rejection.OVERLAPPING_INTERVALS -> " · 區間重疊，趨勢暫不合併"
        SourceIntervalAggregation.Rejection.INVALID_INTERVAL -> " · 時段資料無效，趨勢不計"
        SourceIntervalAggregation.Rejection.INVALID_VALUE -> " · 數值無效，趨勢不計"
    }

    private fun metricText(title: String, value: String, detail: String, trend: String) =
        "$title\n\n$value\n$trend\n$detail"

    private fun sparkline(values: List<Double>): String = TrendSampleEncoder.encode(values).ifEmpty { EMPTY_TREND }

    private inner class MetricCardView(title: String, accent: String) : LinearLayout(this) {
        private val accentColor = Color.parseColor(accent)
        private val titleView = label(title, 14f, "#D9DCE3")
        private val valueView = label("— —", 28f, "#FFFFFF")
        private val trendView = SparklineView(accentColor)
        private val detailView = label("", 12f, "#9298A4")

        var text: CharSequence = ""
            set(value) {
                field = value
                val lines = value.toString().lines()
                titleView.text = lines.firstOrNull().orEmpty().replace("血氧濃度 (SpO₂)", "血氧").replace("消耗的熱量", "活動熱量").let { if (it.isBlank()) it else "$it  ›" }
                valueView.text = lines.getOrNull(2).orEmpty()
                trendView.setEncoded(lines.getOrNull(3).orEmpty())
                detailView.text = lines.drop(4).joinToString("\n")
            }

        init {
            orientation = VERTICAL
            isClickable = true
            isFocusable = true
            foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(40, 255, 255, 255)), null, rounded("#FFFFFF", 28))
            setPadding(dp(18), dp(18), dp(18), dp(17))
            background = HealthVisuals.panel(context, 24, true)
            elevation = dp(2).toFloat()
            minimumHeight = dp(184)
            titleView.typeface = Typeface.create("sans", Typeface.NORMAL)
            valueView.typeface = Typeface.create("sans", Typeface.BOLD)
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(MetricGlyphView(context, title, accentColor), LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(7) })
                addView(titleView, LayoutParams(0, -2, 1f))
            })
            addView(valueView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(trendView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(10) })
            addView(detailView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
    }

    private inner class SparklineView(private val accentColor: Int) : View(this) {
        private var points = emptyList<Float>()
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = dp(2).toFloat()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(28, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            style = Paint.Style.FILL
        }
        private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34363B")
            strokeWidth = dp(1).toFloat()
        }

        fun setEncoded(encoded: String) {
            val values = SensorSuiteCsvParser.parseFloats(encoded)
            val minimum = values.minOrNull() ?: 0f
            val range = (values.maxOrNull() ?: 0f) - minimum
            points = values.map { if (range == 0f) .5f else (it - minimum) / range }
            invalidate()
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val center = height * .72f
            canvas.drawLine(0f, center, width.toFloat(), center, baselinePaint)
            if (points.isEmpty()) {
                return
            }
            if (points.size == 1) {
                canvas.drawCircle(width / 2f, height / 2f, dp(3).toFloat(), Paint(linePaint).apply { style = Paint.Style.FILL })
                return
            }
            val step = if (points.size == 1) 0f else width.toFloat() / (points.size - 1)
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = index * step
                val y = height * (.82f - point * .64f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fill = Path(path).apply {
                lineTo(width.toFloat(), height.toFloat())
                lineTo(0f, height.toFloat())
                close()
            }
            canvas.drawPath(fill, fillPaint)
            canvas.drawPath(path, linePaint)
            val lastX = (points.size - 1) * step
            val lastY = height * (.82f - points.last() * .64f)
            canvas.drawCircle(lastX, lastY, dp(3).toFloat(), Paint(linePaint).apply { style = Paint.Style.FILL })
        }
    }

    private fun heroCard() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(22), dp(18), dp(4), dp(18))
        background = HealthVisuals.panel(context, 30, true)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("WATCH 2 PRO", 10f, "#AABBD7").apply { letterSpacing = .12f })
            addView(label("你的健康，\n清晰可見。", 25f, "#F5F5F7").apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(0, dp(10), 0, dp(8))
            })
            addView(label("手錶 × Health Connect", 11f, "#939DAE"))
            addView(label("查看裝置  →", 13f, "#9BC6FF").apply { setPadding(0, dp(18), 0, 0) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(WatchPortrait(context), LinearLayout.LayoutParams(dp(142), dp(196)))
        contentDescription = "Watch 2 Pro，查看裝置與同步狀態"
        foreground = getDrawable(android.R.drawable.list_selector_background)
        setOnClickListener { selectPage(2) }
    }

    private fun quickActions() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(quickActionRow(
            "⌁  測試連線" to { sendWatchCommand("/control/ping") },
            "↻  修復同步" to { sendWatchCommand("/control/register-passive") }
        ))
        addView(quickActionRow(
            "℃  量測體溫" to { sendWatchCommand("/control/temperature-now") },
            "◎  取得狀態" to { sendWatchCommand("/control/status") }
        ), margins(top = 10))
        addView(quickActionRow(
            "◉  量測 SpO₂" to { sendWatchCommand("/control/oxygen-now") },
            "↗  開始步行" to { sendWatchCommand("/control/exercise/start") }
        ), margins(top = 10))
    }

    private fun quickActionRow(
        first: Pair<String, () -> Unit>,
        second: Pair<String, () -> Unit>
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(quickActionButton(first.first, first.second), LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            marginEnd = dp(5)
        })
        addView(quickActionButton(second.first, second.second), LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            marginStart = dp(5)
        })
    }

    private fun quickActionButton(textValue: String, click: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 13f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = gradient("#304963", "#18283C", 18, "#456581")
        elevation = dp(2).toFloat()
        setOnClickListener { click() }
        installTouchFeedback(this)
    }

    private fun exercisePage() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(40))
        addView(pageHeading("運動", "MOVE WITH PURPOSE", "把每一次訓練變成可理解的進步。"), margins(bottom = 20))
        addView(quickActionRow("▶  開始步行" to { sendWatchCommand("/control/exercise/start") }, "■  停止運動" to { sendWatchCommand("/control/exercise/stop") }), margins(bottom = 18))
        addView(sectionTitle("運動類型", "選擇一個模式，手錶會回報可取得的資料"), margins(bottom = 10))
        addView(featureRow("跑", "戶外跑步", "GPS、配速、心率區間與運動路線", "#25D366") { sendWatchCommand("/control/exercise/start") }, margins(bottom = 10))
        addView(featureRow("走", "健走", "步數、距離、卡路里與訓練時間", "#A8D400") { sendWatchCommand("/control/exercise/start") }, margins(bottom = 10))
        addView(featureRow("騎", "戶外騎行", "GPS 路線、速度與爬升資料", "#35A8FF") { sendWatchCommand("/control/exercise/start") }, margins(bottom = 18))
        addView(sectionTitle("訓練指標", "資料不足時會清楚標示，不以猜測填補"), margins(bottom = 10))
        addView(featureRow("圖", "GPS 路線與訓練分析", "軌跡回放、心率疊圖、海拔、配速、分段與爬升效率", "#35A8FF") {
            startActivity(Intent(this@MainActivity, WorkoutAnalysisActivity::class.java))
        }, margins(bottom = 10))
        addView(flowCard("01", "訓練負荷", "依運動時間、心率區間與活動熱量估算"), margins(bottom = 10))
        addView(flowCard("02", "恢復時間", "本機分析 EPOC 與下一次訓練強度建議"), margins(bottom = 10))
        addView(flowCard("03", "心率區間", "燃脂、心肺、耐力與極限區間摘要"))
    }

    private fun profilePage() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(40))
        addView(pageHeading("我的", "YOUR HEALTH PROFILE", "設定你的資料，讓健康分析更貼近你。"), margins(bottom = 20))
        addView(cardLike("個人資料", "年齡、性別、身高體重與生理基本資料", "#8E7CFF") { showPhysiologyProfileDialog() }, margins(bottom = 10))
        addView(cardLike("健康趨勢分析", "本機規則與統計整理已同步資料；不作疾病診斷", "#FF6F8F") { startActivity(Intent(this@MainActivity, AiHealthAnalysisActivity::class.java)) }, margins(bottom = 10))
        addView(cardLike("資料與權限", "Health Connect 權限、來源與隱私說明", "#35A8FF") { selectPage(2) }, margins(bottom = 10))
        addView(cardLike("手機通知", "例行提醒可自行開啟；跌倒／SOS 緊急提醒保留", "#36C98F") {
            val enabled = MobileSafetyAlerts.areRoutineAlertsEnabled(this@MainActivity)
            AlertDialog.Builder(this@MainActivity)
                .setTitle("手機健康通知")
                .setMessage("例行提醒包含心率等非緊急提示。關閉可避免一般通知打擾；手錶端量測與資料同步不受影響。跌倒／SOS 緊急提醒不受此設定影響。")
                .setPositiveButton(if (enabled) "關閉例行提醒" else "開啟例行提醒") { _, _ ->
                    MobileSafetyAlerts.setRoutineAlertsEnabled(this@MainActivity, !enabled)
                    selectPage(4)
                }
                .setNegativeButton("取消", null)
                .show()
        }, margins(bottom = 10))
        addView(cardLike("關於本 App", "Xiaomi Watch Health Center 1.3.0", "#36C98F") { startActivity(Intent(this@MainActivity, PrivacyPolicyActivity::class.java)) })
    }

    private fun cardLike(title: String, subtitle: String, accent: String, click: () -> Unit) = featureRow("◆", title, subtitle, accent, click)

    private fun bottomNavigation() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(5), dp(5), dp(5), dp(5))
        background = HealthVisuals.panel(context, 26, true)
        todayNavigation = navigationButton("摘要", true) {
            showPage(today = true)
        }
        healthNavigation = navigationButton("健康", false) {
            showPage(today = false)
        }
        addView(todayNavigation, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(healthNavigation, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        deviceNavigation = navigationButton("裝置", false) { selectPage(2) }
        addView(deviceNavigation, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        exerciseNavigation = navigationButton("運動", false) { selectPage(3) }
        addView(exerciseNavigation, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        profileNavigation = navigationButton("我的", false) { selectPage(4) }
        addView(profileNavigation, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun showPage(today: Boolean) {
        selectPage(if(today) 0 else 1)
    }

    private fun selectPage(index: Int) {
        selectedPage = index.coerceIn(0, 4)
        listOf(todayScroll, healthScroll, deviceScroll, exerciseScroll, profileScroll).forEachIndexed { page, scroll ->
            scroll.visibility = if(page == selectedPage) View.VISIBLE else View.GONE
        }
        listOf(todayNavigation, healthNavigation, deviceNavigation, exerciseNavigation, profileNavigation).forEachIndexed { page, button -> styleNavigation(button, page == selectedPage) }
        if(android.animation.ValueAnimator.areAnimatorsEnabled()) {
            listOf(todayScroll, healthScroll, deviceScroll, exerciseScroll, profileScroll)[selectedPage].apply {
                alpha = .4f; translationY = dp(7).toFloat()
                animate().alpha(1f).translationY(0f).setDuration(220).start()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", selectedPage)
        super.onSaveInstanceState(outState)
    }

    private fun navigationButton(textValue: String, selected: Boolean, click: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isAllCaps = false
        stateListAnimator = null
        styleNavigation(this, selected)
        setOnClickListener { click() }
        installTouchFeedback(this)
    }

    private fun styleNavigation(button: Button, selected: Boolean) {
        button.setTextColor(Color.parseColor(if (selected) "#F3F6FF" else "#8F97A9"))
        button.background = if (selected) gradient("#444D62", "#30384A", 21, "#586078") else rounded("#00000000", 21)
        button.isSelected = selected
        button.alpha = 1f
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun View.pressFeedback() {
        animate().scaleX(.97f).scaleY(.97f).setDuration(70).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(130).start()
        }.start()
    }

    private fun installTouchFeedback(view: View) {
        view.isClickable = true
        view.isFocusable = true
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    if(android.animation.ValueAnimator.areAnimatorsEnabled()) touched.animate().scaleX(.98f).scaleY(.98f).alpha(.82f).setDuration(80).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    touched.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()
                }
            }
            false
        }
    }

    private fun friendlySource(packageName: String) = MetricDetailPolicy.healthConnectImportLabel(packageName)
        ?: when {
            packageName == this.packageName -> "Health Bridge"
            packageName.contains("xiaomi", true) || packageName.contains("mi.health", true) -> "Xiaomi / Mi Fitness"
            packageName.contains("fitbit", true) -> "Google Fitbit"
            else -> packageName.substringAfterLast('.')
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color)); cornerRadius = dp(radius).toFloat()
    }
    private fun gradient(start: String, end: String, radius: Int, stroke: String? = null) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(start), Color.parseColor(end))
    ).apply {
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
    }
    private fun margins(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun label(value: String, size: Float, color: String) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.parseColor(color))
    }
    private fun sectionTitle(title: String, detail: String) = LinearLayout(this).apply {
        tag = title
        orientation = LinearLayout.VERTICAL
        addView(label(title, 20f, "#F5F5F7").apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        addView(label(detail, 11f, "#8E96A8").apply { setPadding(0, dp(5), 0, 0) })
    }
    private fun flowCard(number: String, title: String, detail: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15)); background = HealthVisuals.panel(context, 18)
        addView(label(number, 20f, "#FF6C76").apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 16f, "#FFFFFF")); addView(label(detail, 13f, "#8290AD"))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun pageHeading(title: String, eyebrow: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(eyebrow, 10f, "#8693A9").apply { letterSpacing = .15f })
        addView(label(title, 32f, "#FAFBFF").apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(8), 0, dp(5))
        })
        addView(label(subtitle, 13f, "#A1A8B8"))
    }

    private fun featureRow(icon: String, title: String, detail: String, color: String, action: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(18), dp(16), dp(18))
        background = HealthVisuals.interactive(context, 22)
        addView(MetricGlyphView(context, icon, Color.parseColor(color)), LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(12) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 16f, "#F4F6FB").apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
            addView(label(detail, 11f, "#969EB0").apply { setPadding(0, dp(5), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(label("›", 22f, "#8D99AF"))
        setOnClickListener { action() }
    }

    companion object {
        private const val REFRESH_DEBOUNCE_MILLIS = 30_000L
        private const val EMPTY_TREND = "·  ·  ·  ·  ·"
    }
}
