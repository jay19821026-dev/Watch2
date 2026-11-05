package com.jaysato.xiaomihealthbridge

object LocalAiHealthAnalyzer {
    enum class Level { INSUFFICIENT, STABLE, WATCH, ATTENTION }

    data class Result(
        val level: Level,
        val score: Int?,
        val headline: String,
        val observedValueCount: Int,
        val observations: List<String>,
        val actions: List<String>,
        val teamReviews: List<TeamReview> = emptyList(),
        val agreement: String = "",
        val measurementHeadline: String = "目前沒有可判讀的量測結論",
        val measurementDetail: String = "",
        val measuredNormalCount: Int = 0,
        val measuredWatchCount: Int = 0,
        val measuredAttentionCount: Int = 0,
        val researchOnlyCount: Int = 0
    )

    data class TeamReview(val role: String, val finding: String, val confidence: String)

    fun analyze(
        insights: AlgorithmicInsightsEngine.Insights,
        measurements: List<HealthConclusionEngine.Item> = emptyList()
    ): Result {
        val stressObserved = insights.stress != null
        val sleepObserved = insights.sleepHours != null && !insights.sleepInferred
        val loadObserved = insights.trainingLoad != null
        val observedDomains = listOf(stressObserved, sleepObserved, loadObserved).count { it }
        val observedValueCount = listOf(
            insights.latestObservedRestingHeartRateBpm,
            insights.latestObservedHrvMs,
            insights.sleepHours,
            insights.sleepSessionHours,
            insights.historicalSleepSessionHours,
            insights.oxygenSampleSummary,
            insights.temperatureDeviation,
            insights.trainingLoad,
            insights.estimatedDailyCalories,
            insights.recordedTotalCaloriesKcal,
            insights.gaitEfficiency,
            insights.activityRegularity,
            insights.restingDeviation
        ).count { it != null }
        val missingRiskDomains = listOfNotNull(
            "壓力／恢復".takeIf { !stressObserved },
            "睡眠".takeIf { !sleepObserved },
            "身體能量".takeIf { insights.bodyEnergy == null }
        )
        val observations = mutableListOf<String>()
        val actions = mutableListOf<String>()
        val recoveryNotes = mutableListOf<String>()
        val sleepNotes = mutableListOf<String>()
        val activityNotes = mutableListOf<String>()
        var concern = 0

        insights.stress?.let {
            when {
                it >= 75 -> { concern += 35; recoveryNotes += "壓力估算偏高（$it/100），近期恢復可能不足"; actions += "今天避免高強度訓練，安排 10–20 分鐘放鬆或散步" }
                it >= 50 -> { concern += 18; recoveryNotes += "壓力估算中等（$it/100），留意疲勞累積"; actions += "下一次運動以低至中強度為主" }
                else -> recoveryNotes += "壓力估算目前平穩（$it/100）"
            }
        } ?: recoveryNotes.add("壓力／恢復研究模型等待近期配對資料；已取得的靜息心率與 HRV 可分別查看")

        insights.bodyEnergy?.let { energy ->
            if (energy < 30) {
                concern += 30
                recoveryNotes += "身體能量估算偏低（$energy/100）"
                actions += "優先補充睡眠、水分與正常飲食"
            } else if (energy < 55) {
                concern += 15
                recoveryNotes += "身體能量估算普通（$energy/100）"
            } else recoveryNotes += "身體能量估算良好（$energy/100）"
        } ?: recoveryNotes.add("身體能量研究模型等待近期配對輸入；不代表能量低或資料遺失")

        if (insights.sleepInferred) {
            sleepNotes += "僅為低心率樣本跨度，不代表睡眠時長；不據此判斷缺眠"
        } else insights.sleepHours?.let {
            when {
                it < 5.0 -> { concern += 30; sleepNotes += "睡眠時間明顯不足（%.1f 小時）".format(it); actions += "今晚提早就寢，避免用高強度運動補償疲勞" }
                it < 7.0 -> { concern += 12; sleepNotes += "睡眠略少（%.1f 小時）".format(it); actions += "今晚增加睡眠時間並固定作息" }
                else -> sleepNotes += "睡眠時間約 %.1f 小時；僅依時長，不代表睡眠分期或品質已驗證".format(it)
            }
        } ?: insights.sleepSessionHours?.let {
            sleepNotes += "已記錄睡眠 session 時段 %.1f 小時；這是記錄區間，不等同實際睡眠時數，分期不完整因此不計睡眠分數".format(it)
        } ?: sleepNotes.add("沒有有效睡眠時長紀錄；不推測睡眠分期")

        insights.trainingLoad?.let { load ->
            activityNotes += "近 7 日 Banister TRIMP 粗估約 $load AU；心率與運動時段及靜息基準採同來源配對，最大心率採年齡估算；未建立個人基線，不用固定門檻判定過度訓練"
        } ?: activityNotes.add("近 7 日 TRIMP 需要有效的運動時段與心率樣本")
        if (!insights.hrvAvailable) actions += "持續於睡眠或靜止時配戴，以提高 HRV 分析可信度"
        if (observedDomains < 2) actions += "確認 Health Connect 讀取權限與手錶同步；目前只顯示已取得的個別面向，不合成總分"
        if (actions.isEmpty()) actions += "維持目前作息與活動量，持續觀察長期趨勢"

        val teamReviews = listOf(
            TeamReview("恢復與自律神經", recoveryNotes.joinToString("；"), if (insights.stress != null && insights.hrvAvailable) "中" else "低"),
            TeamReview("睡眠觀察", sleepNotes.joinToString("；"), if (insights.sleepHours != null && !insights.sleepInferred) "中" else "低"),
            TeamReview("活動負荷", activityNotes.joinToString("；"), "低至中")
        )
        observations += teamReviews.map { "${it.role}：${it.finding}" }
        val highConcernReviews = listOfNotNull(
            insights.stress?.let { it >= 50 },
            if (!insights.sleepInferred) insights.sleepHours?.let { it < 7.0 } else null,
        )
        val highConcernCount = highConcernReviews.count { it }
        val agreement = when {
            highConcernCount >= 2 -> "多個已觀測規則面向同時提示需要留意；屬規則交叉檢查，不是多個 AI 的診斷共識。"
            highConcernCount == 1 -> "一個已觀測規則面向觸發提醒；建議看原始資料與近期趨勢，不宜單獨下結論。"
            missingRiskDomains.isNotEmpty() -> "目前沒有足夠資料確認高關注或低風險；缺少${missingRiskDomains.joinToString("、")}面向，不代表沒有問題。"
            else -> "目前已觀測規則面向沒有一致的高關注訊號；不代表排除疾病或保證健康。"
        }

        val score = (100 - concern).coerceIn(0, 100)
        val level = when {
            insights.confidence == "不足" -> Level.INSUFFICIENT
            observedDomains < 2 -> Level.INSUFFICIENT
            concern >= 55 -> Level.ATTENTION
            concern >= 25 -> Level.WATCH
            missingRiskDomains.isNotEmpty() -> Level.INSUFFICIENT
            else -> Level.STABLE
        }
        val headline = when (level) {
            Level.INSUFFICIENT -> when {
                observedValueCount > 0 -> "已取得 $observedValueCount 項健康觀察；整合總分仍待配對資料"
                observedDomains == 0 -> "尚無足夠健康紀錄，暫不評分"
                insights.confidence == "不足" -> "部分指標可查看；整體可信度不足，暫不合成評分"
                else -> "已有部分指標；資料不完整，暫不評分"
            }
            Level.STABLE -> "今日狀態大致穩定"
            Level.WATCH -> "有幾項恢復訊號值得留意"
            Level.ATTENTION -> "今天建議優先休息與恢復"
        }
        val normalCount = measurements.count { it.available && it.level == HealthConclusionEngine.Level.GOOD }
        val watchCount = measurements.count { it.available && it.level == HealthConclusionEngine.Level.WATCH }
        val attentionCount = measurements.count { it.available && it.level == HealthConclusionEngine.Level.ATTENTION }
        val researchCount = measurements.count { it.available && it.level == HealthConclusionEngine.Level.RESEARCH }
        val measurementHeadline = when {
            attentionCount > 0 -> "$attentionCount 項可比較量測結果需留意"
            watchCount > 0 -> "$watchCount 項可比較量測結果建議觀察"
            normalCount > 0 -> "目前 $normalCount 項可比較量測結果在參考範圍"
            researchCount > 0 -> "已取得 $researchCount 項研究訊號，尚不能分類正常／異常"
            else -> "目前沒有可判讀的量測結論"
        }
        val measurementDetail = "參考範圍內 $normalCount · 建議觀察 $watchCount · 需留意 $attentionCount · 研究訊號 $researchCount；只總結目前可判讀項目，不代表排除疾病。"
        return Result(
            level, score.takeIf { level != Level.INSUFFICIENT }, headline, observedValueCount,
            observations.distinct(), actions.distinct(), teamReviews, agreement,
            measurementHeadline, measurementDetail, normalCount, watchCount, attentionCount, researchCount
        )
    }
}
