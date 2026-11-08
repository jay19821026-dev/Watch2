package com.jaysato.xiaomihealthbridge

import android.graphics.Color
import android.graphics.Typeface
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AiHealthAnalysisActivity : ComponentActivity() {
    private lateinit var content: LinearLayout
    private var hydrationSummary = "補水紀錄尚未讀取"
    private var analysisJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(40))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(HealthVisuals.background)
            addView(content)
        })
        showLoading()
        loadAnalysis()
    }

    private fun showLoading() {
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = "‹  返回"; textSize = 16f; setTextColor(HealthVisuals.blue)
            setPadding(0, dp(8), 0, dp(18)); setOnClickListener { finish() }
        })
        content.addView(text("本機健康分析", 31f, Color.WHITE, true))
        content.addView(text("LOCAL RULE-BASED ANALYSIS", 11f, Color.rgb(126, 174, 255), true).apply { letterSpacing = .12f })
        content.addView(ProgressBar(this).apply { isIndeterminate = true }, lp(top = 48))
        content.addView(text("正在於手機內分析健康趨勢…", 14f, HealthVisuals.secondary), lp(top = 12))
    }

    private fun loadAnalysis() {
        analysisJob?.cancel()
        analysisJob = lifecycleScope.launch {
            try {
                val analysis = AiHealthAnalysisPipeline.load(
                    readInsights = { AlgorithmicInsightsEngine(this@AiHealthAnalysisActivity).calculate() },
                    readMeasurements = { HealthConclusionEngine.build(this@AiHealthAnalysisActivity) }
                )
                hydrationSummary = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    AiHydrationSummary.read(this@AiHealthAnalysisActivity)
                } ?: "補水資料讀取逾時；其他分析繼續。"
                render(
                    analysis.insights,
                    analysis.explanation,
                    analysis.measurementReport,
                    analysis.engineAnalysis,
                    analysis.warnings
                )
                if (analysis.insightsAvailable) {
                    try {
                        WatchInsightsPublisher.publish(this@AiHealthAnalysisActivity, analysis.insights)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (syncError: Exception) {
                        Log.w("AiHealthAnalysis", "Watch insight publication failed", syncError)
                        content.addView(text("手機分析已完成；手錶同步暫時失敗。", 13f, HealthVisuals.secondary), lp(top = 8))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("AiHealthAnalysis", "Local health analysis failed", error)
                getSharedPreferences("ai_analysis_diagnostics", MODE_PRIVATE).edit()
                    .putLong("failed_at", System.currentTimeMillis())
                    .putString("exception_class", error.javaClass.name)
                    .putString("message", error.message.orEmpty().take(512))
                    .apply()
                renderError(error.message ?: "無法讀取健康資料")
            }
        }
    }

    private fun render(
        insights: AlgorithmicInsightsEngine.Insights,
        result: LocalAiHealthAnalyzer.Result,
        measurementReport: HealthConclusionEngine.Report,
        engineAnalysis: LocalHealthAnalysisEngine.Analysis,
        pipelineWarnings: List<String> = emptyList()
    ) {
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = "‹  返回"; textSize = 16f; setTextColor(HealthVisuals.blue)
            setPadding(0, dp(8), 0, dp(18)); setOnClickListener { finish() }
        })
        content.addView(text("健康趨勢分析", 31f, Color.WHITE, true))
        content.addView(text("看懂數據，照顧今天的自己", 15f, HealthVisuals.secondary), lp(bottom = 12))
        content.addView(text("本機規則與統計分析 · 健康參考，非疾病診斷", 12f, HealthVisuals.secondary), lp(top = 6, bottom = 20))
        val daily = DailyHealthReport.from(insights, java.time.Instant.now(), java.time.ZoneId.systemDefault())
        content.addView(panel().apply {
            addView(text("每日資料報告 · ${daily.date}", 20f, Color.WHITE, true))
            addView(text(daily.explanation, 14f, HealthVisuals.secondary), lp(top = 8))
            daily.observations.forEach { addView(text("• $it", 14f, Color.WHITE), lp(top = 8)) }
            if (daily.missing.isNotEmpty()) addView(Button(context).apply {
                text = "查看尚未取得的資料"; isAllCaps = false
                setOnClickListener { showDetail("尚未取得的資料", daily.missing.joinToString("\n")) }
            }, lp(top = 12))
        }, lp(bottom = 14))
        if (pipelineWarnings.isNotEmpty()) {
            content.addView(panel().apply {
                addView(text("部分分析暫不可用", 16f, Color.rgb(255, 190, 77), true))
                pipelineWarnings.forEach { warning ->
                    addView(text(warning, 12f, HealthVisuals.secondary), lp(top = 5))
                }
                addView(text("其他已完成的單項結果仍保留；沒有以合成數值替代。", 12f, Color.WHITE), lp(top = 8))
            }, lp(bottom = 12))
        }
        val lead = AnalysisLeadSummary.from(measurementReport.items)
        content.addView(panel().apply {
            addView(text("目前結論", 16f, HealthVisuals.secondary, true))
            addView(text(lead.headline, 21f, Color.WHITE, true), lp(top = 8))
            lead.displayLines.forEach { addView(text(it, 16f, Color.WHITE), lp(top = 8)) }
            addView(text("下一步：${lead.nextAction}", 13f, HealthVisuals.blue), lp(top = 14))
            addView(Button(context).apply {
                text = "查看各項健康結果"; isAllCaps = false
                setOnClickListener {
                    startActivity(Intent(this@AiHealthAnalysisActivity, HealthConclusionActivity::class.java))
                }
            }, lp(top = 8))
        }, lp(bottom = 14))

        val color = when (result.level) {
            LocalAiHealthAnalyzer.Level.INSUFFICIENT -> HealthVisuals.secondary
            LocalAiHealthAnalyzer.Level.STABLE -> Color.rgb(57, 211, 151)
            LocalAiHealthAnalyzer.Level.WATCH -> Color.rgb(255, 190, 77)
            LocalAiHealthAnalyzer.Level.ATTENTION -> Color.rgb(255, 101, 119)
        }
        content.addView(panel().apply {
            addView(text(if (result.score != null) "恢復觀察 · 規則估分" else "已記錄的其他資料", 13f, HealthVisuals.secondary))
            result.score?.let { addView(text(it.toString(), 54f, color, true)) }
            if (result.score != null) addView(text(result.headline, 18f, Color.WHITE, true))
            insights.latestObservedRestingHeartRateBpm?.let {
                addView(text("靜息心率  ${"%.0f".format(it)} bpm\n${insights.latestObservedRestingHeartRateAt?.atZone(java.time.ZoneId.systemDefault())?.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm")) ?: "時間未知"}", 23f, Color.WHITE), lp(top = 8))
            }
            insights.latestObservedHrvMs?.let {
                addView(text("HRV  ${"%.1f".format(it)} ms\n${insights.latestObservedHrvAt?.atZone(java.time.ZoneId.systemDefault())?.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm")) ?: "時間未知"}", 23f, Color.WHITE), lp(top = 8))
            }
            insights.sleepSessionHours?.let {
                addView(text("已記錄睡眠區間 ${"%.1f".format(it)} 小時（非分期實睡時長）", 16f, Color.WHITE), lp(top = 8))
            }
            insights.historicalSleepSessionHours?.let {
                addView(text("7 日睡眠紀錄區間  ${"%.1f".format(it)} 小時", 16f, Color.WHITE).apply {
                    setOnClickListener { showDetail("睡眠紀錄區間", "近 7 日的紀錄區間合計，不是今日睡眠或實睡時長。") }
                    contentDescription = "近 7 日睡眠紀錄區間，點擊查看時長限制"
                }, lp(top = 8))
            }
            addView(text("資料可信度：${insights.confidence}", 12f, HealthVisuals.secondary), lp(top = 8))
            if (insights.readiness != null) {
                addView(text(
                    "研究推估：壓力 ${ResearchScoreDisplay.format(insights.stress)} · 身體能量 ${ResearchScoreDisplay.format(insights.bodyEnergy)}\n" +
                        "恢復 ${ResearchScoreDisplay.format(insights.recovery)} · 準備度 ${ResearchScoreDisplay.format(insights.readiness)}",
                    16f, Color.WHITE
                ), lp(top = 8))
                addView(text("研究推估 · 信心低；計算方式見詳細資料", 12f, HealthVisuals.secondary), lp(top = 6))
            }
        }, lp(bottom = 14))
        content.addView(panel().apply {
            addView(text("本機規則檢查面向", 20f, Color.WHITE, true))
            addView(text("不同分析面向各自檢查可用資料，再由整合器比對；不是多個生成式 AI，也不是醫療診斷。", 12f, HealthVisuals.secondary), lp(top = 5, bottom = 8))
            result.teamReviews.forEach { review ->
                addView(text("${review.role}  ·  信心${review.confidence}", 15f, Color.rgb(237, 240, 247), true), lp(top = 12))
                addView(text(review.finding, 13f, HealthVisuals.secondary), lp(top = 4))
            }
            addView(text(result.agreement, 13f, Color.rgb(178, 196, 226)).apply { setLineSpacing(dp(3).toFloat(), 1f) }, lp(top = 14))
        }, lp(bottom = 14))
        content.addView(section("資料觀察", "依可取得的壓力、睡眠、恢復與活動負荷規則整理", result.observations, "✦"), lp(bottom = 14))
        content.addView(panel().apply {
            addView(text("Health Connect｜近 7 日活動觀測", 19f, Color.WHITE, true))
            addView(text("平台彙總基本總量；空白日保留為無紀錄，不補 0。這些值不會放寬訓練負荷、ANS、TRIMP 或步態計算條件。", 12f, HealthVisuals.secondary), lp(top = 5, bottom = 10))
            addView(text(insights.stepsObservation.display("步數", "步", java.time.ZoneId.systemDefault()), 13f, Color.rgb(228, 232, 241)).apply { setLineSpacing(dp(3).toFloat(), 1f) })
            addView(text(insights.activeCaloriesObservation.display("活動卡路里", "kcal", java.time.ZoneId.systemDefault()), 13f, Color.rgb(228, 232, 241)).apply { setLineSpacing(dp(3).toFloat(), 1f) }, lp(top = 12))
            addView(text(
                RecordedTotalCaloriesPolicy.display(
                    insights.recordedTotalCaloriesState,
                    insights.recordedTotalCaloriesKcal,
                    insights.recordedTotalCaloriesOrigin,
                    insights.recordedTotalCaloriesRecordCount,
                    insights.recordedTotalCaloriesExcludedBoundaryRecords
                ), 13f, Color.rgb(228, 232, 241)
            ).apply { setLineSpacing(dp(3).toFloat(), 1f) }, lp(top = 12))
        }, lp(bottom = 14))
        content.addView(section("今天可以這樣做", "優先提供可執行、低風險的生活建議", result.actions, "✓"), lp(bottom = 14))
        content.addView(panel().apply {
            addView(text("健康數據", 21f, Color.WHITE, true))
            addView(text("點選卡片查看來源、波形狀態與建議", 12f, HealthVisuals.secondary), lp(top = 4, bottom = 12))
            val items = measurementReport.items
            items.chunked(2).forEach { group ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                group.forEach { item ->
                    val display = ConsumerMetricPresentation.from(item)
                    val accent = when (item.level) {
                        HealthConclusionEngine.Level.ATTENTION -> Color.rgb(255, 137, 147)
                        HealthConclusionEngine.Level.WATCH -> Color.rgb(255, 205, 115)
                        HealthConclusionEngine.Level.GOOD -> Color.rgb(113, 231, 190)
                        HealthConclusionEngine.Level.RESEARCH -> HealthVisuals.blue
                        else -> Color.rgb(182, 190, 205)
                    }
                    row.addView(metricTile(display.title, display.value, display.state, accent) {
                        showDetail(display.title, "${display.value} · ${display.state}\n\n${ConsumerMetricPresentation.explanation(item)}")
                    }, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
                }
                if (group.size == 1) row.addView(LinearLayout(context), LinearLayout.LayoutParams(0, 1, 1f))
                addView(row, lp(top = 8))
            }
            insights.oxygenSampleSummary?.let { oxygen ->
                addView(text("近 7 日血氧紀錄 ${"%.1f–%.1f".format(oxygen.minimumPercent, oxygen.maximumPercent)}%", 15f, Color.WHITE), lp(top = 10))
            }
        }, lp(bottom = 14))
        content.addView(panel().apply {
            addView(text("飲水與補水", 20f, Color.WHITE, true))
            addView(text(hydrationSummary, 16f, Color.WHITE), lp(top = 8))
        }, lp(bottom = 14))
        val availableHealthPrompts = HealthRiskScreening.evaluate(this@AiHealthAnalysisActivity).filter { screen ->
            screen.state !in setOf("目前不可判讀", "需要新鮮袖帶讀值", "本次無效，請重測", "本次未取得結果")
        }
        if (availableHealthPrompts.isNotEmpty()) content.addView(panel().apply {
            addView(text("本次健康提示", 20f, Color.WHITE, true))
            availableHealthPrompts.forEach { screen ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = HealthVisuals.interactive(context, 16)
                    addView(text("${screen.condition}  ›", 16f, Color.WHITE, true))
                    addView(text(screen.state, 13f, HealthVisuals.blue), lp(top = 5))
                    setOnClickListener { showDetail(screen.condition, "${screen.state}\n\n${screen.explanation}\n\n${screen.nextStep}") }
                }, lp(top = 8))
            }
        }, lp(bottom = 14))
        content.addView(panel().apply {
            val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
            addView(text("演算法與資料細節　⌄", 17f, Color.WHITE, true).apply {
                isClickable = true
                setOnClickListener {
                    details.visibility = if (details.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
                    this.text = if (details.visibility == android.view.View.VISIBLE) "演算法與資料細節　⌃" else "演算法與資料細節　⌄"
                }
            })
            addView(details, lp(top = 10))
            details.addView(text("探索性規則計分使用近期同來源 HRV、個人基準與睡眠紀錄，信心低。個別研究／訓練估算依各自輸入計算。", 12f, HealthVisuals.secondary), lp(top = 5))
            details.addView(text("規則版本 ${ResearchCompositeModel.VERSION}：恢復採睡眠／HRV 50/50 權重，能量採睡眠／HRV 壓力規則 60/40，準備度為兩者平均。睡眠支持＝記錄時長／8 小時；紀錄可能含清醒，未經臨床驗證。", 12f, HealthVisuals.secondary), lp(top = 5))
            if (insights.sleepInferred) details.addView(text("目前睡眠時數是低心率樣本跨度，不是睡眠紀錄；只作探索顯示。", 12f, HealthVisuals.secondary), lp(top = 5))
            details.addView(text("睡眠時段相似度只比較相鄰且都有睡眠紀錄的日期；缺紀錄日不當成無睡眠。這是探索性規則分，不是臨床睡眠規律指標。", 12f, HealthVisuals.secondary), lp(top = 5))
            details.addView(text("TRIMP 僅納入至少 2 筆運動心率樣本的運動；用樣本算術平均心率、同日運動前最近靜息心率與年齡估算最大心率。這不是時間加權或臨床訓練處方。", 12f, HealthVisuals.secondary), lp(top = 5))
addView(text("HRV  ${if (insights.hrvAvailable) "已有實測" else "無有效 Health Connect 紀錄"}\nSpO₂  ${if (insights.oxygenAvailable) "已有實測" else "無實測紀錄"}\n睡眠  ${insights.sleepHours?.let { "%.1f 小時".format(it) } ?: "無可用睡眠時長"}\n複合壓力／身體電量／恢復／準備度  研究模型已接入；結果依近期配對資料計算，詳見上方\n睡眠規律度  ${insights.sleepRegularity?.let { "$it/100" } ?: "資料不足"}\n社交時差  ${insights.socialJetlagMinutes?.let { "$it 分鐘" } ?: "資料不足"}\n已記錄睡眠時段差額  ${insights.recordedSleepDurationGapMinutes?.let { "${it / 60} 小時 ${it % 60} 分" } ?: "資料不足（缺夜不估算）"}\n近 7 日有效血氧樣本範圍（n=${insights.oxygenSampleSummary?.validSampleCount ?: 0}；非夜間專項）  ${insights.oxygenSampleSummary?.let { "%.1f–%.1f%%".format(it.minimumPercent, it.maximumPercent) } ?: "資料不足"}\n近 7 日有效樣本中低於 90% 的比例（描述性；非夜間專項）  ${insights.oxygenSampleSummary?.below90Percent?.let { "%.1f%%".format(it) } ?: "至少需要 3 個有效樣本"}\n體溫偏移  ${insights.temperatureDeviation?.let { "%+.1f °C".format(it) } ?: "資料不足"}\n平均步長  ${insights.gaitEfficiency?.let { "%.2f m/步".format(it) } ?: "資料不足"}\nTRIMP  ${insights.trimp?.let { "%.0f".format(it) } ?: "資料不足"}（納入 ${insights.trimpIncludedSessionCount}/${insights.trimpCandidateSessionCount} 場）\n心率儲備加權活動分鐘（估算；非 MET）  ${insights.heartRateReserveEquivalentMinutes ?: "資料不足"}\nVO₂max：未估算；缺少實測最大心率及本裝置配對運動測試\n有步數資料日的步數一致性（樣本日規則分；非完整週）  ${insights.activityRegularity?.let { "$it/100" } ?: "需要至少 3 個可安全歸屬日"}\n靜息心率偏移  ${insights.restingDeviation?.let { "%+.0f bpm".format(it) } ?: "資料不足"}\nBanister TRIMP 粗估相對負荷（AU；依時間配對、最大心率年齡估算、非個人化校準）  ${insights.trainingLoad ?: "尚無足夠資料"}（納入 ${insights.trimpIncludedSessionCount}/${insights.trimpCandidateSessionCount} 場）\n活動卡路里僅作活動紀錄，不用作訓練負荷代理", 15f, Color.rgb(217, 222, 233)).apply { setLineSpacing(dp(8).toFloat(), 1f) }, lp(top = 12))
            if (insights.sleepHours == null && insights.sleepSessionHours != null) {
                addView(text("睡眠時段  ${"%.1f 小時".format(insights.sleepSessionHours)}（session 時段，非實際睡眠時長）", 13f, HealthVisuals.secondary), lp(top = 8))
            }
            if (insights.unavailableInputs.isNotEmpty()) {
                addView(text("未取得：${insights.unavailableInputs.joinToString("、")}", 12f, HealthVisuals.secondary), lp(top = 10))
            }
            if (insights.emptyInputs.isNotEmpty()) {
                addView(text("無資料的查詢區間：${insights.emptyInputs.joinToString("、")}", 12f, HealthVisuals.secondary), lp(top = 6))
            }
            for (index in childCount - 1 downTo 2) {
                val detailRow = getChildAt(index)
                removeViewAt(index)
                details.addView(detailRow)
            }
        }, lp(bottom = 14))
        val technicalPanels = (0 until content.childCount).map { content.getChildAt(it) }.filter { child ->
            val title = (child as? LinearLayout)?.getChildAt(0) as? TextView
            AiHealthAnalysisUiPolicy.isTechnicalDetailsPanel(title?.text?.toString())
        }
        val technicalDetails = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
        technicalPanels.forEach { content.removeView(it); technicalDetails.addView(it) }
        content.addView(Button(this).apply {
            text = "查看分析依據與其他資料"
            setOnClickListener {
                technicalDetails.visibility = if (technicalDetails.visibility == android.view.View.GONE) android.view.View.VISIBLE else android.view.View.GONE
                text = if (technicalDetails.visibility == android.view.View.VISIBLE) "收起分析依據與其他資料" else "查看分析依據與其他資料"
            }
        }, lp(bottom = 8))
        content.addView(technicalDetails)
        content.addView(text("重要提醒", 14f, Color.rgb(255, 151, 164), true))
        content.addView(text("本機分析只整理健康趨勢與日常建議，不是醫療診斷，也不能偵測或排除疾病。若有胸痛、呼吸困難、昏厥、持續心悸或其他不適，請立即尋求醫療協助。", 13f, HealthVisuals.secondary).apply { setLineSpacing(dp(4).toFloat(), 1f) }, lp(top = 8, bottom = 18))
        content.addView(Button(this).apply {
            text = "重新分析"; textSize = 15f; setTextColor(Color.WHITE); isAllCaps = false
            background = HealthVisuals.interactive(context, 20)
            setOnClickListener { showLoading(); loadAnalysis() }
        }, LinearLayout.LayoutParams(-1, dp(58)))
    }

    private fun section(title: String, subtitle: String, rows: List<String>, icon: String) = panel().apply {
        addView(text(title, 20f, Color.WHITE, true))
        addView(text(subtitle, 12f, HealthVisuals.secondary), lp(top = 4, bottom = 12))
        rows.forEach { addView(text("$icon  $it", 15f, Color.rgb(228, 232, 241)).apply { setLineSpacing(dp(4).toFloat(), 1f) }, lp(top = 9)) }
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.START
        setPadding(dp(20), dp(20), dp(20), dp(20)); background = HealthVisuals.panel(context, 24, true)
    }

    private fun metricTile(title: String, value: String, state: String, accent: Int, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(16), dp(14), dp(16))
        minimumHeight = dp(150)
        background = HealthVisuals.interactive(context, 18)
        addView(text(title, 13f, HealthVisuals.secondary, true))
        addView(text(value, if (value.length > 16) 17f else 24f, Color.WHITE, true), lp(top = 12))
        addView(text("●  $state", 12f, accent), lp(top = 10))
        contentDescription = "$title，$value，$state；點選查看詳細資料"
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun showDetail(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("了解", null).show()
    }

    private fun renderError(message: String) {
        content.removeAllViews()
        content.addView(text("無法完成分析", 26f, Color.WHITE, true))
        content.addView(text(message, 14f, HealthVisuals.secondary), lp(top = 12, bottom = 20))
        content.addView(Button(this).apply { text = "返回"; setOnClickListener { finish() } })
        content.addView(Button(this).apply {
            text = "重新分析"
            setOnClickListener { showLoading(); loadAnalysis() }
        })
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun lp(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top); bottomMargin = dp(bottom)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
