package com.jaysato.xiaomihealthbridge

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.reflect.KClass

class MetricDetailActivity : ComponentActivity() {
    companion object {
        private const val RECORD_PAGE_SIZE = 500
        private const val MAX_RECORD_PAGES = 40
        private const val MAX_RECORDS = 20_000
        private const val MAX_POINTS = 100_000
        private const val RAW_INTERVAL_PREVIEW_LIMIT = 24
        private const val AGGREGATED_HEALTH_CONNECT_SOURCE = "health_connect_aggregated_steps"
    }

    private data class Point(
        val time: Instant,
        val value: Double,
        val source: String,
        val intervalStart: Instant? = null,
        val intervalEnd: Instant? = null,
        val sleepSession: SleepSessionValue? = null,
        val recordingMethod: Int? = null
    )
    private val zone = ZoneId.systemDefault()
    private val muted = Color.rgb(149, 159, 178)
    private var date = LocalDate.now()
    private var period = 0
    private var request: Job? = null
    private lateinit var body: LinearLayout
    private lateinit var detailScroll: ScrollView
    private lateinit var dateButton: TextView
    private lateinit var tabs: LinearLayout
    private lateinit var metric: String
    private var source: String? = null
    private var oxygenMethod = OxygenRecordingMethodPolicy.Method.ALL
    private var points = emptyList<Point>()
    private var excludedBoundaryIntervals = 0
    private var refreshOnResume = false
    private var start = Instant.EPOCH
    private var end = Instant.EPOCH
    private val historyPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { refresh() }
    private val accent get() = Color.parseColor(when(metric) {
        "oxygen" -> "#45DBB4"; "temperature" -> "#FF9B60"; "steps" -> "#F5C85D"; "distance" -> "#69B8FF"
        "sleep" -> "#B498FF"; "weight" -> "#69D6E9"; "calories" -> "#FF9B60"; else -> "#FF607E"
    })
    private val title get() = mapOf("heart_rate" to "心率", "resting_heart_rate" to "靜止心率", "oxygen" to "血氧",
        "steps" to "步數", "distance" to "距離", "sleep" to "睡眠", "calories" to "活動熱量", "weight" to "體重", "temperature" to "腕部體溫紀錄")[metric] ?: "健康"
    private val unit get() = when(metric) {
        "oxygen" -> "%"; "temperature" -> "°C"; "steps" -> "步"; "distance" -> "km"; "sleep" -> "小時"
        "calories" -> "kcal"; "weight" -> "kg"; else -> "次/分"
    }
    private val cumulative get() = MetricDetailPolicy.isCumulative(metric)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun number(value: Double) = when(metric) {
        "distance", "sleep", "weight" -> "%,.1f".format(value)
        else -> "%,.0f".format(value)
    }
    private fun time(value: Instant) = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(zone).format(value)
    private fun name(value: String): String {
        @Suppress("DEPRECATION")
        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(value, 0)).toString()
        } catch (_: Exception) {
            null
        }
        return MetricDetailPolicy.displaySource(value, packageName, appLabel)
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        metric = intent.getStringExtra("metric") ?: "heart_rate"
        date = state?.getString("date")?.let(LocalDate::parse) ?: date
        period = state?.getInt("period") ?: 0
        source = state?.getString("source")
        oxygenMethod = state?.getString("oxygenMethod")?.let {
            runCatching { OxygenRecordingMethodPolicy.Method.valueOf(it) }.getOrNull()
        } ?: OxygenRecordingMethodPolicy.Method.ALL
        val root = column().apply { setBackgroundColor(HealthVisuals.background) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets)
                .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        header.addView(button("‹", "返回") { finish() }.apply { background = HealthVisuals.interactive(context, 22); textSize = 25f }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val heading = column()
        heading.addView(text(title, 20, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("↻", "重新讀取") { refresh() }.apply { background = HealthVisuals.interactive(context, 22); textSize = 22f }, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)
        val dates = LinearLayout(this).apply { gravity = Gravity.CENTER }
        dates.addView(button("‹", "上一個期間") { move(-1) }, LinearLayout.LayoutParams(dp(32), dp(48)))
        dateButton = button("", "選擇日期") {
            DatePickerDialog(this, { _, year, month, day ->
                date = LocalDate.of(year, month + 1, day); refresh()
            }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }
        dateButton.setTextColor(muted)
        dateButton.textSize = 13f
        dates.addView(dateButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        dates.addView(button("›", "下一個期間") { move(1) }, LinearLayout.LayoutParams(dp(32), dp(48)))
        heading.addView(dates)
        tabs = LinearLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4)); background = HealthVisuals.panel(context, 18)
        }
        listOf("日", "週", "月", "年").forEachIndexed { index, label ->
            tabs.addView(button(label, label + "檢視") { period = index; refresh() }, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        root.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(20), dp(8), dp(20), dp(8)) })
        body = column().apply { setPadding(dp(20), dp(16), dp(20), dp(32)) }
        detailScroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(body) }
        root.addView(detailScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        root.requestApplyInsets()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (refreshOnResume) {
            refreshOnResume = false
            refresh()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("date", date.toString()); outState.putInt("period", period); outState.putString("source", source)
        outState.putString("oxygenMethod", oxygenMethod.name)
        super.onSaveInstanceState(outState)
    }
    private fun move(direction: Long) {
        val next = when (period) {
            1 -> date.plusWeeks(direction)
            2 -> date.plusMonths(direction)
            3 -> date.plusYears(direction)
            else -> date.plusDays(direction)
        }
        if (!next.isAfter(LocalDate.now())) { date = next; refresh() }
    }
    private fun refresh() {
        request?.cancel()
        detailScroll.scrollTo(0, 0)
        val first = when (period) {
            1 -> date.minusDays(date.dayOfWeek.value - 1L)
            2 -> date.withDayOfMonth(1)
            3 -> date.withDayOfYear(1)
            else -> date
        }
        val last = when (period) {
            1 -> first.plusWeeks(1)
            2 -> first.plusMonths(1)
            3 -> first.plusYears(1)
            else -> first.plusDays(1)
        }
        start = first.atStartOfDay(zone).toInstant(); end = last.atStartOfDay(zone).toInstant()
        dateButton.text = if (period == 0) "$date  ▾" else "$first — ${last.minusDays(1)}  ▾"
        for (index in 0 until tabs.childCount) {
            tabs.getChildAt(index).apply {
                isSelected = index == period
                background = surface(if (isSelected) Color.rgb(65, 72, 90) else Color.TRANSPARENT, 14)
                (this as TextView).setTextColor(if(isSelected) Color.WHITE else muted)
            }
        }
        body.removeAllViews()
        if (period == 3) addHistoryAccessNotice()
        body.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(36), dp(36)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        body.addView(text("正在讀取此期間的健康紀錄…", 14, muted))
        request = lifecycleScope.launch {
            try {
                val limit = minOf(end, Instant.now())
                val queried = readPoints(limit)
                currentCoroutineContext().ensureActive()
                val intersectingIntervals = queried.filter { point ->
                    val intervalStart = point.intervalStart
                    val intervalEnd = point.intervalEnd
                    intervalStart != null && intervalEnd != null && intervalStart < limit && intervalEnd > start
                }
                val inRange = if (metric == "sleep") {
                    queried.mapNotNull { point ->
                        val session = point.sleepSession ?: return@mapNotNull null
                        val clipped = MetricDetailAggregation.clipSleepSession(session, start, limit)
                            ?: return@mapNotNull null
                        point.copy(
                            time = clipped.end.minusNanos(1),
                            value = MetricDetailAggregation.sleepDisplayHours(clipped),
                            intervalStart = clipped.start,
                            intervalEnd = clipped.end,
                            sleepSession = clipped
                        )
                    }
                } else queried.filter { point ->
                    if (point.intervalStart == null || point.intervalEnd == null) {
                        MetricPointIntegrityPolicy.isInHalfOpenRange(point.time, start, limit)
                    } else {
                        point.intervalStart >= start && point.intervalEnd <= limit
                    }
                }
                excludedBoundaryIntervals = if (metric == "sleep") {
                    intersectingIntervals.count { point ->
                        point.intervalStart!! < start || point.intervalEnd!! > limit
                    }
                } else {
                    (intersectingIntervals.size - inRange.count {
                        it.intervalStart != null && it.intervalEnd != null
                    }).coerceAtLeast(0)
                }
                points = inRange.sortedBy { it.time }
                currentCoroutineContext().ensureActive()
                render()
            } catch (limit: MetricHistoryLimitException) {
                body.removeAllViews()
                if (period == 3) addHistoryAccessNotice()
                val card = card("期間資料超過安全上限")
                card.addView(text("為避免用部分資料冒充完整結果，本次不顯示統計或圖表。請縮小查詢期間後重試。", 15, muted))
                card.addView(button("縮小期間重試", "切換到較短期間") {
                    period = when (period) { 3 -> 2; 2 -> 1; 1 -> 0; else -> 0 }
                    refresh()
                })
            } catch (invalid: MetricDataIntegrityException) {
                body.removeAllViews()
                if (period == 3) addHistoryAccessNotice()
                val card = card("這段資料無法比較")
                card.addView(text("這段紀錄有重複或時間不一致的資料，暫時不能產生可靠圖表。請切換日期或資料來源後重試。", 15, muted))
                card.addView(button("重試", "重新讀取資料") { refresh() })
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                body.removeAllViews()
                if (period == 3) addHistoryAccessNotice()
                val denied = error is SecurityException
                val card = card(if (denied) "缺少健康資料權限" else "Health Connect 讀取失敗")
                card.addView(text(
                    if (denied) "此資料類型未授權讀取。" else "資料提供者或查詢發生錯誤；這不代表資料為 0。",
                    15, muted
                ))
                if (denied) card.addView(button("開啟健康資料權限", "前往 Health Connect 設定") {
                    val intent = android.content.Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                    try {
                        refreshOnResume = true
                        startActivity(intent)
                    } catch (_: android.content.ActivityNotFoundException) {
                        refreshOnResume = false
                        Toast.makeText(this@MetricDetailActivity, "找不到 Health Connect 設定頁", Toast.LENGTH_SHORT).show()
                    } catch (_: SecurityException) {
                        refreshOnResume = false
                        Toast.makeText(this@MetricDetailActivity, "系統拒絕開啟 Health Connect 設定", Toast.LENGTH_SHORT).show()
                    }
                })
                card.addView(button("重試", "重新讀取資料") { refresh() })
                card.addView(button("查看原因", "展開錯誤原因") {
                    android.app.AlertDialog.Builder(this@MetricDetailActivity).setTitle("讀取失敗")
                        .setMessage(if (denied) "這項健康資料尚未授權。請開啟健康資料權限後重試。" else "健康資料服務暫時無法提供這項紀錄，請稍後重試。")
                        .setPositiveButton("關閉", null).show()
                })
            }
        }
    }
    private fun addHistoryAccessNotice() {
        val notice = card("年度資料範圍")
        notice.addView(text(
            "年檢視只呈現 Health Connect 實際允許讀取的紀錄。未授權完整歷史時，系統可能限制為首次授權前 30 天；缺少的年份不會補值。",
            14, muted
        ))
        notice.addView(button("檢查完整歷史存取", "選用：授權讀取 Health Connect 完整歷史") {
            lifecycleScope.launch {
                try {
                    if (HealthConnectClient.getSdkStatus(this@MetricDetailActivity) != HealthConnectClient.SDK_AVAILABLE) {
                        Toast.makeText(this@MetricDetailActivity, "Health Connect 目前不可用", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val client = HealthConnectClient.getOrCreate(this@MetricDetailActivity)
                    val featureStatus = client.features.getFeatureStatus(
                        HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY
                    )
                    if (featureStatus != HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
                        Toast.makeText(this@MetricDetailActivity, "此 Health Connect 版本不支援完整歷史讀取", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val permission = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
                    if (permission in client.permissionController.getGrantedPermissions()) {
                        Toast.makeText(this@MetricDetailActivity, "已授權完整歷史讀取", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    historyPermissionLauncher.launch(setOf(permission))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Toast.makeText(this@MetricDetailActivity, "暫時無法檢查歷史資料權限，請稍後重試。", Toast.LENGTH_LONG).show()
                }
            }
        })
        body.addView(notice)
    }

    private suspend fun <T : Record> records(type: KClass<T>, queryEnd: Instant): List<T> {
        val client = HealthConnectClient.getOrCreate(this)
        val result = collectMetricRecordPages(
            maxPages = MAX_RECORD_PAGES,
            maxRecords = MAX_RECORDS
        ) { token ->
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(start, queryEnd),
                    pageSize = RECORD_PAGE_SIZE,
                    pageToken = token
                )
            )
            MetricRecordPage(response.records, response.pageToken)
        }
        if (!HealthConnectRecordIdentityPolicy.isValid(result.map { it.metadata.id })) {
            throw InvalidHealthConnectRecordIdentityException()
        }
        return result
    }
    private fun interval(from: Instant, until: Instant, value: Double, origin: String) =
        listOf(Point(from, value, origin, from, until)).also {
            MetricRecordIntervalPolicy.validate(from, until)
        }
    private suspend fun readPoints(queryEnd: Instant): List<Point> {
        if (metric == "steps") return readAggregatedStepPoints(queryEnd)
        val result = when(metric) {
        "heart_rate" -> buildList {
            records(HeartRateRecord::class, queryEnd).forEach { record ->
                val samplesInRange = MetricPointIntegrityPolicy.heartRateSamplesInRange(record.samples, start, queryEnd)
                if (size + samplesInRange.size > MAX_POINTS) throw MetricHistoryLimitException()
                addAll(samplesInRange.map { Point(it.time, it.beatsPerMinute.toDouble(), record.metadata.dataOrigin.packageName) })
            }
        }
        "resting_heart_rate" -> records(RestingHeartRateRecord::class, queryEnd).map { Point(it.time, it.beatsPerMinute.toDouble(), it.metadata.dataOrigin.packageName) }
        "oxygen" -> records(OxygenSaturationRecord::class, queryEnd).map {
            Point(it.time, it.percentage.value, it.metadata.dataOrigin.packageName, recordingMethod = it.metadata.recordingMethod)
        }
        "temperature" -> records(BodyTemperatureRecord::class, queryEnd)
            .filter {
                it.metadata.dataOrigin.packageName != packageName &&
                    it.measurementLocation == BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_WRIST
            }
            .map { Point(it.time, it.temperature.inCelsius, it.metadata.dataOrigin.packageName) }
        "weight" -> records(WeightRecord::class, queryEnd).map { Point(it.time, it.weight.inKilograms, it.metadata.dataOrigin.packageName) }
        "steps" -> records(StepsRecord::class, queryEnd)
            .flatMap { interval(it.startTime, it.endTime, it.count.toDouble(), it.metadata.dataOrigin.packageName) }
        "distance" -> records(DistanceRecord::class, queryEnd)
            .flatMap { interval(it.startTime, it.endTime, it.distance.inKilometers, it.metadata.dataOrigin.packageName) }
        "calories" -> records(ActiveCaloriesBurnedRecord::class, queryEnd)
            .flatMap { interval(it.startTime, it.endTime, it.energy.inKilocalories, it.metadata.dataOrigin.packageName) }
        "sleep" -> records(SleepSessionRecord::class, queryEnd).map {
            MetricRecordIntervalPolicy.validate(it.startTime, it.endTime)
            val awake = it.stages.filter { stage ->
                    stage.stage == SleepSessionRecord.STAGE_TYPE_AWAKE ||
                        stage.stage == SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED ||
                        stage.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
                }.map { stage -> SleepAwakeInterval(stage.startTime, stage.endTime) }
                val allStages = it.stages.map { stage -> SleepAwakeInterval(stage.startTime, stage.endTime) }
                val knownStageTypes = it.stages.all { stage ->
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
                val session = SleepSessionValue(it.startTime, it.endTime, awake, it.stages.isNotEmpty(), allStages, knownStageTypes)
                Point(it.endTime, MetricDetailAggregation.sleepDisplayHours(session), it.metadata.dataOrigin.packageName,
                    it.startTime, it.endTime, session)
        }
        else -> emptyList()
        }
        if (result.size > MAX_POINTS) throw MetricHistoryLimitException()
        if (result.any { point ->
                !MetricPointIntegrityPolicy.isValid(
                    MetricPointEvidence(point.time, point.value, point.source, point.intervalStart != null),
                    start,
                    queryEnd
                )
            }
        ) {
            throw MetricDataIntegrityException("資料含有無效數值、空白來源或超出所選期間的點狀時間戳；為避免錯誤圖表，本次不顯示統計。")
        }
        return result
    }

    private suspend fun readAggregatedStepPoints(queryEnd: Instant): List<Point> {
        val client = HealthConnectClient.getOrCreate(this)
        val grouped: List<Pair<Instant, Long>> = if (period == 0) {
            client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, queryEnd),
                    timeRangeSlicer = Duration.ofHours(1)
                )
            ).mapNotNull { row -> row.result[StepsRecord.COUNT_TOTAL]?.let { row.startTime.atZone(zone).toInstant() to it } }
        } else {
            val localRange = HealthConnectPeriodRangePolicy.localRange(start, queryEnd, zone)
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(localRange.first, localRange.second),
                    timeRangeSlicer = Period.ofDays(1)
                )
            ).mapNotNull { row -> row.result[StepsRecord.COUNT_TOTAL]?.let { row.startTime.atZone(zone).toInstant() to it } }
        }
        if (grouped.size > MAX_POINTS) throw MetricHistoryLimitException()
        return grouped.map { (bucketStart, count) ->
            Point(bucketStart, count.toDouble(), AGGREGATED_HEALTH_CONNECT_SOURCE)
        }
    }
    private fun render() {
        body.removeAllViews()
        if (period == 3) addHistoryAccessNotice()
        val origins = points.map { it.source }.distinct().sorted()
        source = MetricDetailPolicy.selectSource(source, origins)
        val sourcePoints = points.filter { it.source == source }
        val methodOptions = listOf(OxygenRecordingMethodPolicy.Method.ALL) + sourcePoints
            .map { OxygenRecordingMethodPolicy.classify(it.recordingMethod) }.distinct()
        val methodButton = if (metric == "oxygen") button("${oxygenMethod.label}  ▾", "選擇血氧量測方式") {
            android.app.AlertDialog.Builder(this).setTitle("血氧量測方式")
                .setSingleChoiceItems(methodOptions.map { it.label }.toTypedArray(), methodOptions.indexOf(oxygenMethod)) { dialog, index ->
                    oxygenMethod = methodOptions[index]; dialog.dismiss(); render()
                }.setNegativeButton("關閉", null).show()
        } else null
        val selected = sourcePoints.filter { point ->
            metric != "oxygen" || oxygenMethod == OxygenRecordingMethodPolicy.Method.ALL ||
                OxygenRecordingMethodPolicy.classify(point.recordingMethod) == oxygenMethod
        }
        val sourceButton = button("${source?.let(::name) ?: "尚無來源"}  ▾", "選擇資料來源") {
            android.app.AlertDialog.Builder(this).setTitle("資料來源 · 不混加不同裝置")
                .setSingleChoiceItems(origins.map(::name).toTypedArray(), origins.indexOf(source)) { dialog, index ->
                    source = origins[index]; dialog.dismiss(); render()
                }.setNegativeButton("關閉", null).show()
        }
        if (selected.isEmpty()) {
            body.addView(sourceButton)
            methodButton?.let { body.addView(it, LinearLayout.LayoutParams(-1, dp(48))) }
            val empty = card("此期間沒有紀錄")
            empty.addView(text("這段時間沒有符合篩選條件的紀錄。請切換日期或資料來源查看。", 16, muted))
            empty.addView(button("查看本週", "切換到本週") { period = 1; refresh() })
            return
        }
        if (cumulative && metric == "sleep" && !MetricDetailAggregation.sleepSessionsAreNonOverlapping(
                selected.mapNotNull { it.sleepSession }
            )
        ) {
            body.addView(sourceButton)
            val overlap = card("睡眠紀錄重複")
            overlap.addView(text("這段期間的睡眠紀錄有重疊，無法可靠合計時數。請檢查資料來源。", 15, muted))
            val intervalDetails = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = android.view.View.GONE
            }
            overlap.addView(button("查看紀錄時間", "展開原始紀錄") {
                intervalDetails.visibility = if (intervalDetails.visibility == android.view.View.VISIBLE)
                    android.view.View.GONE else android.view.View.VISIBLE
            })
            overlap.addView(intervalDetails)
            val preview = selected.sortedByDescending { it.intervalStart ?: it.time }.take(RAW_INTERVAL_PREVIEW_LIMIT)
            preview.forEach { point ->
                val intervalStart = point.intervalStart ?: return@forEach
                val intervalEnd = point.intervalEnd ?: return@forEach
                val sessionHours = Duration.between(intervalStart, intervalEnd).toMillis() / 3_600_000.0
                intervalDetails.addView(text(
                    "${time(intervalStart)}–${time(intervalEnd)}  ${"%.1f".format(sessionHours)} 小時",
                    14,
                    Color.WHITE
                ).apply { setPadding(0, dp(10), 0, 0) })
            }
            if (preview.size < selected.size) {
                intervalDetails.addView(text("顯示最新 ${preview.size} 筆，共 ${selected.size} 筆。", 12, muted)
                    .apply { setPadding(0, dp(8), 0, 0) })
            }
            body.addView(overlap, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            return
        }
        val intervalMeasurements = selected.mapNotNull { point ->
            val intervalStart = point.intervalStart ?: return@mapNotNull null
            val intervalEnd = point.intervalEnd ?: return@mapNotNull null
            SourceIntervalMeasurement(point.source, intervalStart, intervalEnd, point.value)
        }
        if (cumulative && intervalMeasurements.isNotEmpty()) {
            val assessment = SourceIntervalAggregation.assess(intervalMeasurements)
            if (!assessment.eligible) {
                body.addView(sourceButton)
                val card = card("這段期間無法可靠合計")
                val reason = when (assessment.rejection) {
                    SourceIntervalAggregation.Rejection.MULTIPLE_DATA_ORIGINS -> "紀錄來自多個資料來源。"
                    SourceIntervalAggregation.Rejection.OVERLAPPING_INTERVALS -> "同一來源的紀錄時段重疊或重複，可能重複計數。"
                    SourceIntervalAggregation.Rejection.INVALID_INTERVAL -> "紀錄時間範圍無效。"
                    SourceIntervalAggregation.Rejection.INVALID_VALUE -> "紀錄數值無效。"
                    else -> "此期間沒有可安全合計的區間紀錄。"
                }
                card.addView(text("$reason 請檢查資料來源；目前不顯示合計結果。", 15, muted))
                val intervalDetails = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = android.view.View.GONE
                }
                card.addView(button("查看紀錄時間", "展開原始紀錄") {
                    intervalDetails.visibility = if (intervalDetails.visibility == android.view.View.VISIBLE)
                        android.view.View.GONE else android.view.View.VISIBLE
                })
                card.addView(intervalDetails)
                val preview = MetricDetailAggregation.rawIntervalPreview(intervalMeasurements)
                preview.forEach { interval ->
                    intervalDetails.addView(text(
                        "${time(interval.startTime)}–${time(interval.endTime)}  ${number(interval.value)} $unit",
                        14,
                        Color.WHITE
                    ).apply { setPadding(0, dp(10), 0, 0) })
                }
                if (preview.size < intervalMeasurements.size) {
                    intervalDetails.addView(text("顯示最新 ${preview.size} 筆，共 ${intervalMeasurements.size} 筆。", 12, muted)
                        .apply { setPadding(0, dp(8), 0, 0) })
                }
                body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                return
            }
        }
        val bucketBounds: (Instant) -> Pair<Instant, Instant> = { instant ->
            val local = instant.atZone(zone)
            when (period) {
                0 -> {
                    val bucketStart = local.withMinute(0).withSecond(0).withNano(0)
                    bucketStart.toInstant() to bucketStart.plusHours(1).toInstant()
                }
                3 -> {
                    val bucketStart = local.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
                    bucketStart.toInstant() to bucketStart.plusMonths(1).toInstant()
                }
                else -> {
                    val bucketStart = local.toLocalDate().atStartOfDay(zone)
                    bucketStart.toInstant() to bucketStart.plusDays(1).toInstant()
                }
            }
        }
        val display: List<Point> = if (metric == "temperature") {
            selected.groupBy { TemperatureChartWindowPolicy.bucketStart(it.time, period, zone) }
                .map { (stamp, values) -> Point(stamp, values.map { it.value }.average(), source.orEmpty()) }
                .sortedBy { it.time }
        } else if (!cumulative) selected else if (metric == "sleep") {
            selected.groupBy { bucketBounds(it.time).first }.map { (stamp, values) ->
                Point(stamp, values.sumOf { it.value }, source.orEmpty())
            }.sortedBy { it.time }
        } else {
            val buckets = IntervalBucketAggregation.sumWholeIntervals(
                intervalMeasurements,
                bucketStart = { bucketBounds(it).first },
                bucketEnd = { bucketBounds(it).second }
            )
            selected.filter { it.intervalStart == null }.groupBy { bucketBounds(it.time).first }
                .mapValues { (_, values) -> values.sumOf { it.value } }
                .toMutableMap().apply { buckets.buckets.forEach { bucket -> this[bucket.start] = (this[bucket.start] ?: 0.0) + bucket.value } }
                .map { (stamp, amount) -> Point(stamp, amount, source.orEmpty()) }
                .sortedBy { it.time }
        }
        val hero = column().apply {
            setPadding(dp(18), dp(20), dp(18), dp(16))
            background = HealthVisuals.panel(context, 26, true)
        }
        val caption = text(if (cumulative) "期間累計" else "最新紀錄", 11, accent, true).apply { letterSpacing = .08f }
        val value = text("", 36, Color.WHITE, true)
        fun showValue(amount: Double) {
            val numeric = number(amount)
            value.text = android.text.SpannableString("$numeric $unit").apply {
                setSpan(android.text.style.RelativeSizeSpan(.45f), numeric.length, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        showValue(if(cumulative) selected.sumOf { it.value } else selected.last().value)
        val stamp = text(
            if (cumulative) "依所選來源 · ${when (period) { 0 -> "小時"; 3 -> "每月"; else -> "每日" }}分布"
            else time(selected.last().time) + if (metric == "oxygen") " · ${OxygenRecordingMethodPolicy.classify(selected.last().recordingMethod).label}" else "",
            13, muted
        )
        hero.addView(caption); hero.addView(value); hero.addView(stamp)
        if (metric == "oxygen") {
            hero.addView(text(
                selected.last().value.takeIf { it.isFinite() && it in 70.0..100.0 }
                    ?.let { ConsumerVitalsSummary.oxygen(it).detail }
                    ?: "這筆血氧讀值不在可比較範圍內，請重新量測。",
                14, Color.WHITE
            ))
        } else if (metric == "resting_heart_rate") {
            hero.addView(text(VitalsCardPresentationPolicy.heartRateReference(selected.last().value), 14, Color.WHITE))
        } else if (metric == "heart_rate") {
            hero.addView(text("${ConsumerVitalsSummary.heartRateLabel(selected.last().value)} · 成人靜息參考 60–100 bpm", 14, Color.WHITE))
        }
        if (metric == "temperature") {
            hero.addView(text(
                "這是腕部溫度紀錄，不能當作核心體溫。",
                12, muted
            ))
        }
        if (display.isNotEmpty()) {
            val chart = DetailChart(display) { point ->
                caption.text = "已選取"; showValue(point.value)
                stamp.text = time(point.time) + if (metric == "oxygen") " · ${OxygenRecordingMethodPolicy.classify(point.recordingMethod).label}" else ""
            }
            hero.addView(chart, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(22) })
            hero.addView(text("拖曳探索  ·  查看每個時間點", 10, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
        } else {
            hero.addView(text("這段時間沒有可顯示的圖表資料。", 13, muted))
        }
        if (excludedBoundaryIntervals > 0) {
            val boundaryMessage = if (metric == "sleep") "部分睡眠時段跨過所選日期，圖表只顯示範圍內的部分。"
                else "部分紀錄跨過所選日期，未納入這段期間的統計。"
            hero.addView(text(boundaryMessage, 12, muted))
        }
        if (cumulative && metric != "sleep" && intervalMeasurements.isNotEmpty()) {
            val bucketed = IntervalBucketAggregation.sumWholeIntervals(
                intervalMeasurements,
                bucketStart = { bucketBounds(it).first },
                bucketEnd = { bucketBounds(it).second }
            )
            if (bucketed.excludedCrossBucketIntervals > 0) {
                hero.addView(text("部分紀錄跨越圖表日期，總量仍已計入。", 12, muted))
            }
        }
        body.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        if (metric == "oxygen") {
            val provenance = card("血氧紀錄方式")
            methodButton?.let { provenance.addView(it, LinearLayout.LayoutParams(-1, dp(48))) }
            val counts = sourcePoints.groupingBy { OxygenRecordingMethodPolicy.classify(it.recordingMethod) }.eachCount()
            provenance.addView(text("紀錄方式：${counts.keys.joinToString("、") { it.label }}。可切換上方篩選查看各類紀錄。", 13, muted))
        }
        val overview = card("${if(period == 0) "當日" else "期間"}概覽")
        val stats = if(cumulative) listOf("累計" to number(selected.sumOf { it.value })) + if (display.isNotEmpty()) listOf(
            "有資料分段平均" to number(display.map { it.value }.average()),
            "最高分段" to number(display.maxOf { it.value }), "有資料分段" to display.size.toString()
        ) else emptyList()
            else listOf("最低" to number(selected.minOf { it.value }), "最高" to number(selected.maxOf { it.value }),
                "平均" to number(selected.map { it.value }.average()), "紀錄筆數" to selected.size.toString())
        stats.chunked(2).forEach { pair ->
            val row = LinearLayout(this)
            pair.forEach { (label, result) ->
                row.addView(column().apply {
                    setPadding(dp(4), dp(14), dp(4), dp(14))
                    addView(text(result, 25, Color.WHITE, true)); addView(text(label, 13, muted))
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            overview.addView(row)
        }
        val clinicalValue = if (cumulative) selected.sumOf { it.value } else selected.last().value
        val sleepDailyTotals = if (metric == "sleep") MetricDetailAggregation.sleepHoursByWakeDate(
            selected.mapNotNull { point -> point.sleepSession }, zone
        ) else emptyMap()
        val stepsDailyTotals = if (metric == "steps" && period != 0) {
            MetricDetailAggregation.aggregateDailyStepBuckets(
                selected.map { it.time to it.value }, zone
            )
        } else emptyMap()
        val selectedSleepSessions = selected.mapNotNull { it.sleepSession }
        val sleepStagesComplete = selectedSleepSessions.isNotEmpty() && HealthInsightInputPolicy.sleepStagesCoverSessions(
            selectedSleepSessions,
            selectedSleepSessions.minOf { it.start },
            selectedSleepSessions.maxOf { it.end }
        )
        val sleepDailyMean = MetricDetailAggregation.meanObservedDayTotal(sleepDailyTotals.values)
            ?.takeIf { metric != "sleep" || sleepStagesComplete }
        val stepsDailyMean = if (period == 0) clinicalValue else MetricDetailAggregation.meanObservedDayTotal(stepsDailyTotals.values)
        val clinical = ClinicalMetricCalibration.let {
            when (metric) {
                "oxygen" -> if (OxygenRecordingMethodPolicy.eligibleForDeviceReference(selected.map { point -> point.recordingMethod })) {
                    it.forOxygen(clinicalValue)
                } else ClinicalMetricCalibration.Result(
                    metric, clinicalValue, "不評分", null,
                    "含手動輸入或量測方式未註明的紀錄；不做裝置量測的參考評分。",
                    emptyList(), false
                )
                "heart_rate" -> it.forHeartRate(clinicalValue, resting = false)
                "resting_heart_rate" -> it.forHeartRate(clinicalValue, resting = true)
                "temperature" -> ClinicalMetricCalibration.Result(
                    metric, clinicalValue, "不評分", null,
                    "僅顯示 Health Connect 腕部位置的體溫紀錄；量測位置與方法未經本 App 校驗，不套用核心體溫參考範圍，也不視為原廠 skinTemp 日報表。",
                    emptyList(), false
                )
                "sleep" -> it.forSleepHours(sleepDailyMean)
                "steps" -> it.forSteps(stepsDailyMean)
                else -> ClinicalMetricCalibration.Result(
                    metric, clinicalValue, "不評分", null,
                    "此指標沒有適用的公開臨床評分；僅呈現來源資料",
                    ClinicalReferenceTables.compareMetric(metric, clinicalValue),
                    false
                )
            }
        }
        val clinicalCard = card("這段紀錄怎麼看")
        val plainResult = when (metric) {
            "oxygen" -> if (clinicalValue in 70.0..100.0 &&
                OxygenRecordingMethodPolicy.eligibleForDeviceReference(selected.map { it.recordingMethod }))
                "血氧 ${number(clinicalValue)}% · ${if (clinicalValue < 95.0) "低於常見範圍" else "在常見範圍內"}"
                else "這段紀錄的血氧量測方式未確認"
            "heart_rate", "resting_heart_rate" ->
                "心率 ${number(clinicalValue)} bpm · ${ConsumerVitalsSummary.heartRateLabel(clinicalValue)}${if (metric == "heart_rate") "（未確認靜息）" else ""}"
            "sleep" -> sleepDailyMean?.let { "有紀錄日平均 ${number(it)} 小時睡眠" }
                ?: "尚無可比較的睡眠時長"
            "steps" -> stepsDailyMean?.let { "有紀錄日平均 ${number(it)} 步" }
                ?: "尚無可比較的每日步數"
            "temperature" -> "已記錄腕部溫度；不與核心體溫比較"
            else -> "已記錄資料；可查看近期變化"
        }
        clinicalCard.addView(text(plainResult, 16, Color.WHITE, true))
        val referenceDetails = column().apply { visibility = View.GONE }
        clinicalCard.addView(button("查看參考依據", "展開參考依據") {
            referenceDetails.visibility = if (referenceDetails.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        clinicalCard.addView(referenceDetails)
        referenceDetails.addView(text(clinical.headline, 14, Color.rgb(220, 225, 235)).apply { setPadding(0, dp(8), 0, 0) })
        if (metric == "sleep" && period != 0) {
            referenceDetails.addView(text(
                "依醒來日歸屬；${sleepDailyTotals.size} 個有紀錄日的日均 ${sleepDailyMean?.let { "${number(it)} 小時" } ?: "無法計算"}。",
                12, muted
            ).apply { setPadding(0, dp(6), 0, 0) })
        }
        if (metric == "sleep" && !sleepStagesComplete) {
            referenceDetails.addView(text("部分睡眠階段紀錄不完整；圖表只顯示記錄的時段，可能包含清醒時間，因此不計睡眠分數。", 12, muted))
        }
        if (metric == "steps" && period != 0) {
            referenceDetails.addView(text(
                "按日合計後取 ${stepsDailyTotals.size} 個有紀錄日的平均：${stepsDailyMean?.let { "${number(it)} 步／日" } ?: "無法計算"}。",
                12, muted
            ).apply { setPadding(0, dp(6), 0, 0) })
        }
        clinical.comparisons.forEach { line ->
            referenceDetails.addView(text("· $line", 13, muted).apply { setPadding(0, dp(4), 0, 0) })
        }
        referenceDetails.addView(text(ClinicalVerdict.DISCLAIMER, 12, Color.rgb(255, 200, 120)).apply { setPadding(0, dp(10), 0, 0) })
        sourceButton.background = HealthVisuals.interactive(this)
        sourceButton.textSize = 13f
        body.addView(sourceButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(16) })
        val history = card(if(cumulative) "分段紀錄" else "量測歷史")
        val list = column()
        var shown = 0
        fun appendRows() {
            display.asReversed().drop(shown).take(12).forEach { point ->
                val methodLabel = if (metric == "oxygen") " · ${OxygenRecordingMethodPolicy.classify(point.recordingMethod).label}" else ""
                list.addView(button("${time(point.time)}     ${number(point.value)} $unit$methodLabel   ›", "查看這筆紀錄") {
                    android.app.AlertDialog.Builder(this).setTitle(title)
                        .setMessage("${number(point.value)} $unit\n${time(point.time)}\n來源：${name(point.source)}${if (metric == "oxygen") "\n量測方式：${OxygenRecordingMethodPolicy.classify(point.recordingMethod).label}" else ""}")
                        .setPositiveButton("關閉", null).show()
                }.apply { textSize = 13f; gravity = Gravity.CENTER_VERTICAL or Gravity.START; setTextColor(Color.rgb(218, 224, 236)) }, LinearLayout.LayoutParams(-1, dp(52)))
            }
            shown = minOf(shown + 12, display.size)
        }
        appendRows(); history.addView(list)
        val more = button("顯示更多紀錄", "載入更多歷史") { }
        more.setOnClickListener { appendRows(); if(shown == display.size) more.visibility = View.GONE }
        if(shown < display.size) history.addView(more)
        val about = card("資料說明")
        about.addView(text(if(metric == "sleep")
            "跨日睡眠計在醒來那天；平均值只包含有紀錄的日期。"
            else if (metric == "steps")
            "步數由 Health Connect 彙總；不同 App 的紀錄可能重疊。"
            else if(cumulative)
            "總量只計入所選資料來源；不同來源的紀錄可能重疊。"
            else "這裡顯示所選日期與來源的紀錄；空白時段沒有補入數值。", 14, muted))
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        typeface = Typeface.create("sans-serif", if(bold) Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun surface(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun blend(color: Int, amount: Float) = Color.rgb(
        (18 + Color.red(color) * amount).toInt().coerceAtMost(255),
        (22 + Color.green(color) * amount).toInt().coerceAtMost(255),
        (30 + Color.blue(color) * amount).toInt().coerceAtMost(255))
    private fun button(label: String, description: String, action: () -> Unit) = text(label, 15, accent, true).apply {
        gravity = Gravity.CENTER; minHeight = dp(48); contentDescription = description
        isFocusable = true; background = RippleDrawable(ColorStateList.valueOf(blend(accent, .25f)), surface(Color.TRANSPARENT, 14), surface(Color.WHITE, 14))
        setOnClickListener { action() }
    }
    private fun card(title: String): LinearLayout = column().apply {
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = HealthVisuals.panel(context)
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(MetricGlyphView(context, this@MetricDetailActivity.title, accent), LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
            addView(text(title, 16, Color.WHITE, true))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }
    private inner class DetailChart(private val data: List<Point>, private val select: (Point) -> Unit) : View(this@MetricDetailActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var selected: Point? = null
        private val low = if(cumulative) 0.0 else (data.minOf { it.value } - 3).coerceAtLeast(0.0)
        private val high = maxOf(low + 1, data.maxOf { it.value } + if(cumulative) data.maxOf { it.value } * .12 else 3.0).let { if(metric == "oxygen") minOf(100.0, it) else it }
        private fun plotWidth() = width - dp(40).toFloat()
        private fun plotHeight() = height - dp(32).toFloat()
        private fun x(point: Point) = (Duration.between(start, point.time).toMillis().toDouble() / Duration.between(start, end).toMillis() * plotWidth()).toFloat()
        private fun y(point: Point) = dp(8) + ((plotHeight() - dp(8)) * (1 - (point.value - low) / (high - low))).toFloat()
        init { isClickable = true; contentDescription = "互動${title}圖表；數值亦可由下方紀錄按鈕讀取" }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.textSize = dp(10).toFloat(); paint.strokeWidth = resources.displayMetrics.density * .5f
            for(index in 0..4) {
                val ypos = dp(8) + (plotHeight() - dp(8)) * index / 4
                paint.color = Color.rgb(44, 51, 65); canvas.drawLine(0f, ypos, plotWidth(), ypos, paint)
                paint.color = muted; canvas.drawText("%.0f".format(high - (high - low) * index / 4), plotWidth() + dp(5), ypos + dp(4), paint)
            }
            for(index in 0..4) {
                val fraction = index / 4.0
                val instant = start.plusMillis((Duration.between(start, end).toMillis() * fraction).toLong())
                val label = DateTimeFormatter.ofPattern(when (period) {
                    0 -> "HH:mm"; 3 -> "M月"; else -> "MM/dd"
                }).withZone(zone).format(instant)
                paint.color = muted
                canvas.drawText(label, (plotWidth() * fraction).toFloat().coerceAtMost(plotWidth() - paint.measureText(label)), height - dp(6).toFloat(), paint)
            }
            paint.color = accent; paint.strokeWidth = dp(3).toFloat(); paint.strokeCap = Paint.Cap.ROUND
            if(metric == "heart_rate" || metric == "oxygen") {
                data.groupBy { TemperatureChartWindowPolicy.bucketStart(it.time, period, zone) }.forEach { (bucketStart, bucket) ->
                    val highest = bucket.maxBy { it.value }
                    val lowest = bucket.minBy { it.value }
                    val xPosition = x(Point(bucketStart, lowest.value, lowest.source))
                    if (highest.value == lowest.value) {
                        canvas.drawCircle(xPosition, y(lowest), dp(2).toFloat(), paint)
                    } else {
                        canvas.drawLine(xPosition, y(highest), xPosition, y(lowest), paint)
                    }
                }
            } else data.forEach { point ->
                if(cumulative) {
                    val bar = (plotWidth() / when (period) {
                        0 -> 24; 1 -> 7; 3 -> 12; else -> date.lengthOfMonth()
                    }).coerceAtLeast(dp(2).toFloat())
                    paint.alpha = 180
                    canvas.drawRoundRect(x(point), y(point), minOf(plotWidth(), x(point) + bar * .65f), plotHeight(), dp(3).toFloat(), dp(3).toFloat(), paint)
                } else { paint.alpha = 220; canvas.drawCircle(x(point), y(point), dp(2).toFloat(), paint) }
            }
            paint.alpha = 255
            selected?.let {
                paint.color = Color.WHITE; paint.strokeWidth = dp(1).toFloat()
                canvas.drawLine(x(it), 0f, x(it), plotHeight(), paint)
                canvas.drawCircle(x(it), y(it), dp(5).toFloat(), paint)
                paint.color = accent; canvas.drawCircle(x(it), y(it), dp(3).toFloat(), paint)
            }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    selected = data.minByOrNull { abs(x(it) - event.x) }
                    selected?.let(select); invalidate()
                }
                MotionEvent.ACTION_UP -> { parent.requestDisallowInterceptTouchEvent(false); performClick() }
                MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }
}

internal data class MetricRecordPage<T>(val records: List<T>, val nextPageToken: String?)

internal data class MetricPointEvidence(val time: Instant, val value: Double, val source: String, val interval: Boolean)

internal object MetricPointIntegrityPolicy {
    fun isInHalfOpenRange(time: Instant, start: Instant, end: Instant): Boolean =
        !time.isBefore(start) && time.isBefore(end)

    fun heartRateSamplesInRange(samples: List<HeartRateRecord.Sample>, start: Instant, end: Instant): List<HeartRateRecord.Sample> =
        samples.filter { isInHalfOpenRange(it.time, start, end) }

    fun isValid(point: MetricPointEvidence, start: Instant, end: Instant): Boolean =
        point.value.isFinite() && point.source.isNotBlank() &&
            (point.interval || isInHalfOpenRange(point.time, start, end))
}

internal object MetricRecordIntervalPolicy {
    fun validate(start: Instant, end: Instant) {
        if (!start.isBefore(end)) {
            throw MetricDataIntegrityException("紀錄區間無效；為避免以部分資料計算，本次不顯示統計或圖表。")
        }
    }
}

internal class MetricDataIntegrityException(message: String) : IllegalStateException(message)

internal class MetricHistoryLimitException : IllegalStateException("Health Connect history exceeds the bounded query limit")

internal object MetricDetailPolicy {
    fun isCumulative(metric: String): Boolean = metric in setOf("steps", "distance", "sleep", "calories")
    fun selectSource(current: String?, origins: List<String>): String? =
        current?.takeIf { it in origins } ?: origins.sorted().firstOrNull()
    fun healthConnectImportLabel(packageName: String): String? = packageName
        .takeIf { it.startsWith("com.android.healthconnect.") }
        ?.let { "Health Connect 匯入來源 · ${"%04X".format(it.hashCode() and 0xFFFF)}" }
    fun displaySource(packageName: String, ownPackage: String, resolvedAppLabel: String?): String = when {
        packageName == "health_connect_aggregated_steps" -> "Health Connect 去重彙總"
        packageName == ownPackage -> "Health Bridge"
        packageName.contains("xiaomi", true) || packageName.contains("mi.health", true) -> "小米運動健康"
        packageName.contains("fitbit", true) -> "Google Fitbit"
        else -> healthConnectImportLabel(packageName)
            ?: resolvedAppLabel?.takeIf { it.isNotBlank() }
            ?: "Health Connect 匯入來源 · ${"%04X".format(packageName.hashCode() and 0xFFFF)}"
    }
}

internal suspend fun <T> collectMetricRecordPages(
    maxPages: Int,
    maxRecords: Int,
    fetchPage: suspend (String?) -> MetricRecordPage<T>
): List<T> {
    require(maxPages > 0 && maxRecords > 0)
    val result = ArrayList<T>()
    val pageTokenTracker = HealthConnectPageTokenTracker()
    var token: String? = null
    var pagesRead = 0
    do {
        currentCoroutineContext().ensureActive()
        if (pagesRead >= maxPages) throw MetricHistoryLimitException()
        val page = fetchPage(token)
        pagesRead++
        if (result.size + page.records.size > maxRecords) throw MetricHistoryLimitException()
        result.addAll(page.records)
        token = page.nextPageToken
        if (pageTokenTracker.isRepeated(token)) throw MetricHistoryLimitException()
        if (pagesRead >= maxPages && !token.isNullOrEmpty()) throw MetricHistoryLimitException()
    } while (!token.isNullOrEmpty())
    currentCoroutineContext().ensureActive()
    return result
}

internal data class SleepSessionValue(
    val start: Instant,
    val end: Instant,
    val awakeIntervals: List<SleepAwakeInterval> = emptyList(),
    val hasStageData: Boolean = true,
    val stageIntervals: List<SleepAwakeInterval> = emptyList(),
    val stageTypesKnown: Boolean = true
)
internal data class SleepAwakeInterval(val start: Instant, val end: Instant)

internal object TemperatureChartWindowPolicy {
    fun bucketLabel(period: Int): String = when (period) {
        0 -> "半小時"
        1, 2 -> "天"
        else -> "月"
    }

    fun bucketStart(time: Instant, period: Int, zone: ZoneId): Instant {
        val local = time.atZone(zone)
        return when (period) {
            0 -> local.withMinute((local.minute / 30) * 30).withSecond(0).withNano(0).toInstant()
            1, 2 -> local.toLocalDate().atStartOfDay(zone).toInstant()
            else -> local.toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant()
        }
    }
}

internal object MetricDetailAggregation {
    fun rawIntervalPreview(
        intervals: List<SourceIntervalMeasurement>,
        limit: Int = 24
    ): List<SourceIntervalMeasurement> {
        if (limit <= 0) return emptyList()
        return intervals.sortedByDescending { it.startTime }.take(limit)
    }

    fun aggregateDailyStepBuckets(
        buckets: List<Pair<Instant, Double>>,
        zone: ZoneId
    ): Map<LocalDate, Double> {
        if (buckets.any { (_, count) -> !count.isFinite() || count < 0.0 }) return emptyMap()
        return buckets.groupBy { (time, _) -> time.atZone(zone).toLocalDate() }
            .mapValues { (_, dailyBuckets) -> dailyBuckets.sumOf { it.second } }
    }

    fun clipSleepSession(session: SleepSessionValue, from: Instant, until: Instant): SleepSessionValue? {
        if (!from.isBefore(until) || !session.start.isBefore(until) || !session.end.isAfter(from)) return null
        val clippedStart = maxOf(session.start, from)
        val clippedEnd = minOf(session.end, until)
        fun clip(interval: SleepAwakeInterval): SleepAwakeInterval? {
            val start = maxOf(interval.start, clippedStart)
            val end = minOf(interval.end, clippedEnd)
            return SleepAwakeInterval(start, end).takeIf { start.isBefore(end) }
        }
        return session.copy(
            start = clippedStart,
            end = clippedEnd,
            awakeIntervals = session.awakeIntervals.mapNotNull(::clip),
            stageIntervals = session.stageIntervals.mapNotNull(::clip)
        )
    }

    fun sleepSessionsAreNonOverlapping(sessions: List<SleepSessionValue>): Boolean {
        val ordered = sessions.filter { it.start.isBefore(it.end) }.sortedBy { it.start }
        var latestEnd: Instant? = null
        for (session in ordered) {
            if (latestEnd?.isAfter(session.start) == true) return false
            if (latestEnd == null || session.end.isAfter(latestEnd)) latestEnd = session.end
        }
        return true
    }

    fun sleepHours(session: SleepSessionValue): Double {
        if (!session.start.isBefore(session.end)) return 0.0
        val totalMillis = Duration.between(session.start, session.end).toMillis()
        val awakeIntervals = session.awakeIntervals
            .filter { it.start.isBefore(it.end) }
            .mapNotNull { awake ->
                val start = maxOf(session.start, awake.start)
                val end = minOf(session.end, awake.end)
                (start to end).takeIf { start.isBefore(end) }
            }
            .sortedBy { it.first }
        var mergedStart: Instant? = null
        var mergedEnd: Instant? = null
        var awakeMillis = 0L
        awakeIntervals.forEach { (start, end) ->
            val currentEnd = mergedEnd
            if (mergedStart == null) {
                mergedStart = start
                mergedEnd = end
            } else if (currentEnd != null && !start.isAfter(currentEnd)) {
                mergedEnd = maxOf(currentEnd, end)
            } else {
                awakeMillis += Duration.between(mergedStart, currentEnd!!).toMillis()
                mergedStart = start
                mergedEnd = end
            }
        }
        if (mergedStart != null && mergedEnd != null) {
            awakeMillis += Duration.between(mergedStart, mergedEnd).toMillis()
        }
        return (totalMillis - awakeMillis) / 3_600_000.0
    }

    fun sleepDisplayHours(session: SleepSessionValue): Double =
        if (HealthInsightInputPolicy.sleepStagesCoverSessions(listOf(session), session.start, session.end)) {
            sleepHours(session)
        } else {
            Duration.between(session.start, session.end).toMillis() / 3_600_000.0
        }

    fun sleepHoursByWakeDate(sessions: List<SleepSessionValue>, zone: ZoneId): Map<LocalDate, Double> =
        if (!sleepSessionsAreNonOverlapping(sessions)) emptyMap() else sessions.asSequence()
            .filter { it.start.isBefore(it.end) }
            .groupBy { it.end.minusNanos(1).atZone(zone).toLocalDate() }
            .mapValues { (_, daySessions) ->
                daySessions.sumOf(::sleepDisplayHours)
            }

    fun meanObservedDayTotal(totals: Collection<Double>): Double? =
        totals.takeIf { it.isNotEmpty() && it.all { value -> value.isFinite() && value >= 0.0 } }
            ?.average()?.takeIf(Double::isFinite)

    fun observedDailyStepTotals(
        intervals: List<SourceIntervalMeasurement>,
        zone: ZoneId
    ): Map<LocalDate, Double> {
        val assessment = SourceIntervalAggregation.assess(intervals)
        if (!assessment.eligible) return emptyMap()
        val datesWithCrossMidnightIntervals = intervals.filter { measurement ->
            measurement.startTime.atZone(zone).toLocalDate() != measurement.endTime.minusNanos(1).atZone(zone).toLocalDate()
        }.flatMap { measurement ->
            val first = measurement.startTime.atZone(zone).toLocalDate()
            val last = measurement.endTime.minusNanos(1).atZone(zone).toLocalDate()
            generateSequence(first) { current -> if (current.isBefore(last)) current.plusDays(1) else null }.toList()
        }.toSet()
        val firstDate = intervals.minOf { it.startTime }.atZone(zone).toLocalDate()
        val lastDate = intervals.maxOf { it.endTime }.minusNanos(1).atZone(zone).toLocalDate()
        val allObservedDates = generateSequence(firstDate) { current ->
            if (current.isBefore(lastDate)) current.plusDays(1) else null
        }.toSet()
        val daily = IntervalBucketAggregation.sumWholeIntervals(
            intervals,
            bucketStart = { instant -> instant.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant() },
            bucketEnd = { start -> start.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant() }
        )
        val totals = daily.buckets.associate { it.start.atZone(zone).toLocalDate() to it.value }
        return allObservedDates.filterNot { it in datesWithCrossMidnightIntervals }
            .mapNotNull { date -> totals[date]?.let { date to it } }
            .toMap()
    }

    fun meanObservedDailySteps(intervals: List<SourceIntervalMeasurement>, zone: ZoneId): Double? =
        meanObservedDayTotal(observedDailyStepTotals(intervals, zone).values)
}
