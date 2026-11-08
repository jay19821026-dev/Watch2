package com.jaysato.xiaomihealthbridge

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass
import kotlin.math.*

class WorkoutAnalysisActivity : ComponentActivity() {
    private lateinit var content: LinearLayout
    private data class WorkoutMetricSample(val time: Instant, val value: Double, val origin: String)
    private data class WorkoutMetricResult(val samples: List<WorkoutMetricSample>, val status: String? = null)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(40))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(HealthVisuals.background)
            addView(content)
        })
        render()
    }

    private fun render() {
        content.addView(text("‹  返回", 16f, HealthVisuals.blue).apply { setOnClickListener { finish() } })
        content.addView(text("運動路線分析", 31f, Color.WHITE, true), lp(top = 16))
        content.addView(text("GPS · 心率 · 海拔 · 訓練效果", 12f, HealthVisuals.secondary), lp(top = 6, bottom = 20))
        val workout = WorkoutRouteStore.latest(this)
        if (workout == null) {
            content.addView(panel("尚無可回放路線", "請在手錶啟動戶外運動並完成同步。下一筆包含至少兩個 GPS 點的運動會顯示在這裡。"))
            return
        }
        content.addView(
            text("最新可用路線 · ${WorkoutRouteDisplayPolicy.recordedStart(workout.start, ZoneId.systemDefault())}", 13f, HealthVisuals.secondary),
            lp(bottom = 12)
        )
        lifecycleScope.launch {
            val (heartSamples, readError) = try {
                val client = HealthConnectClient.getOrCreate(this@WorkoutAnalysisActivity)
                val permission = HealthPermission.getReadPermission(HeartRateRecord::class)
                val granted = client.permissionController.getGrantedPermissions()
                when (HealthConnectPermissionPolicy.state(permission, granted)) {
                    HealthConnectPermissionPolicy.State.QUERY_FAILED -> throw IllegalStateException("權限狀態查詢失敗")
                    HealthConnectPermissionPolicy.State.DENIED -> throw SecurityException("未授權讀取心率")
                    HealthConnectPermissionPolicy.State.GRANTED -> Unit
                }
                val records = client.readAllRecords(
                    HeartRateRecord::class,
                    TimeRangeFilter.between(Instant.ofEpochMilli(workout.start), Instant.ofEpochMilli(workout.end + 2 * 60_000L))
                )
                records.flatMap { it.samples }.sortedBy { it.time } to null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (denied: SecurityException) {
                emptyList<HeartRateRecord.Sample>() to "沒有心率讀取權限；路線仍可查看，心率分析停用。"
            } catch (_: Exception) {
                emptyList<HeartRateRecord.Sample>() to "心率資料讀取失敗；這不代表運動期間沒有心率。"
            }
            val speed = readWorkoutMetric(workout, SpeedRecord::class) { record ->
                record.samples.map { WorkoutMetricSample(it.time, it.speed.inMetersPerSecond * 3.6, record.metadata.dataOrigin.packageName) }
            }
            val cadence = readWorkoutMetric(workout, StepsCadenceRecord::class) { record ->
                record.samples.map { WorkoutMetricSample(it.time, it.rate, record.metadata.dataOrigin.packageName) }
            }
            showWorkout(workout, heartSamples, readError, speed, cadence)
        }
    }

    private suspend fun <T : androidx.health.connect.client.records.Record> readWorkoutMetric(
        workout: WorkoutRouteStore.Workout,
        type: KClass<T>,
        samples: (T) -> List<WorkoutMetricSample>,
    ): WorkoutMetricResult {
        return try {
            val client = HealthConnectClient.getOrCreate(this)
            if (HealthPermission.getReadPermission(type) !in client.permissionController.getGrantedPermissions()) {
                return WorkoutMetricResult(emptyList(), "未授權；請在 Health Connect 允許此類資料的讀取權限。")
            }
            val start = Instant.ofEpochMilli(workout.start)
            val end = Instant.ofEpochMilli(workout.end)
            val records = client.readAllRecords(type, TimeRangeFilter.between(start, end), maxRecords = 2_000)
            WorkoutMetricResult(records.flatMap(samples).filter { !it.time.isBefore(start) && !it.time.isAfter(end) && it.value.isFinite() && it.value >= 0.0 })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: HealthConnectReadLimitException) {
            WorkoutMetricResult(emptyList(), "紀錄超過 2,000 筆，本次沒有顯示不完整的統計。")
        } catch (_: Exception) {
            WorkoutMetricResult(emptyList(), "讀取失敗；不能視為沒有資料。")
        }
    }

    private fun showWorkout(
        workout: WorkoutRouteStore.Workout,
        heartSamples: List<HeartRateRecord.Sample>,
        heartRateReadError: String?,
        speedSamples: WorkoutMetricResult,
        cadenceSamples: WorkoutMetricResult,
    ) {
        val exerciseHeartSamples = heartSamples.filter { it.time.toEpochMilli() <= workout.end }
        val distanceMeters = workout.points.zipWithNext().sumOf { (a, b) -> distance(a.latitude, a.longitude, b.latitude, b.longitude) }
        val durationMinutes = (workout.end - workout.start).coerceAtLeast(0L) / 60_000.0
        val elevationGain = workout.points.mapNotNull { it.altitude }.zipWithNext().sumOf { (a, b) -> (b - a).coerceIn(0.0, 4.0) }
        val averageHeartRate = exerciseHeartSamples.map { it.beatsPerMinute.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val profile = UserProfileStore(this).read()
        val maxHeartRate = profile?.let { 208.0 - 0.7 * it.age }
        val pace = TrainingAlgorithms.paceMinutesPerKm(distanceMeters, durationMinutes)
        val speed = TrainingAlgorithms.speedKmh(distanceMeters, durationMinutes)
        val load = if (averageHeartRate != null && maxHeartRate != null) TrainingAlgorithms.trainingLoad(durationMinutes, averageHeartRate, maxHeartRate) else 0
        val vo2 = TrainingAlgorithms.vo2Max(distanceMeters, durationMinutes, averageHeartRate, maxHeartRate)
        val climbEfficiency = TrainingAlgorithms.climbEfficiency(distanceMeters, elevationGain)
        val drift = SensorHealthAlgorithms.cardiovascularDrift(exerciseHeartSamples.map { it.beatsPerMinute.toDouble() })
        val peakHeartRate = exerciseHeartSamples.maxOfOrNull { it.beatsPerMinute.toDouble() }
        val oneMinuteHeartRate = heartSamples.filter { sample -> sample.time.toEpochMilli() in (workout.end + 45_000L)..(workout.end + 90_000L) }
            .minByOrNull { abs(it.time.toEpochMilli() - (workout.end + 60_000L)) }?.beatsPerMinute?.toDouble()
        val heartRateRecovery = if (peakHeartRate != null && oneMinuteHeartRate != null) TrainingAlgorithms.heartRateRecovery(peakHeartRate, oneMinuteHeartRate) else null

        content.addView(RouteMapView(this, workout.points, exerciseHeartSamples).apply {
            background = HealthVisuals.panel(context, 24, true)
            contentDescription = "最新運動 GPS 路線，線段顏色表示同時間心率強度"
        }, LinearLayout.LayoutParams(-1, dp(300)).apply { bottomMargin = dp(14) })
        content.addView(panel("運動摘要", buildString {
            append("距離  ${"%.2f".format(distanceMeters / 1000.0)} km\n")
            append("時間  ${"%.0f".format(durationMinutes)} 分鐘\n")
            append("平均配速  ${pace?.let(::formatPace) ?: "資料不足"}\n")
            append("GPS 推算平均速度  ${speed?.let { "%.1f km/h".format(it) } ?: "資料不足"}\n")
            append("平均心率  ${averageHeartRate?.let { "%.0f bpm".format(it) } ?: "資料不足"}")
        }), lp(bottom = 14))
        content.addView(panel("Health Connect 運動樣本", buildString {
            append(metricSummary("速度", "km/h", speedSamples))
            append("\n")
            append(metricSummary("步頻", "步／分鐘", cadenceSamples))
            append("\n僅依此路線的時間範圍查找。來源套件與時間相符仍不能單獨證明由這隻手錶量測；樣本平均不等於 GPS 推算速度。")
        }), lp(bottom = 14))
        content.addView(panel("訓練與爬升", buildString {
            append("累積爬升  ${"%.0f".format(elevationGain)} m\n")
            append("爬升效率  ${climbEfficiency?.let { "%.1f m/km".format(it) } ?: "資料不足"}\n")
            append("公里分段  ${ceil(distanceMeters / 1000.0).toInt()} 段\n")
            append("訓練負荷  ${if (load > 0) load else "資料不足"}\n")
            append("VO₂max 推估  ${vo2?.let { "%.1f ml/kg/min".format(it) } ?: "資料不足"}")
            append("\n心率漂移  ${drift?.let { "%+.1f%%".format(it) } ?: "資料不足"}")
            append("\n1 分鐘心率恢復  ${heartRateRecovery?.let { "%.0f bpm".format(it) } ?: "需要運動後持續量測"}")
        }), lp(bottom = 14))
        content.addView(text("海拔曲線", 20f, Color.WHITE, true), lp(bottom = 10))
        content.addView(SeriesChartView(this, workout.points.mapNotNull { it.altitude }, Color.rgb(86, 174, 255)), LinearLayout.LayoutParams(-1, dp(150)).apply { bottomMargin = dp(14) })
        content.addView(text(if (heartRateReadError == null) "心率曲線" else "心率資料狀態", 20f, Color.WHITE, true), lp(bottom = 10))
        if (heartRateReadError != null) {
            content.addView(text(heartRateReadError, 13f, HealthVisuals.secondary), lp(bottom = 8))
        } else if (heartSamples.isEmpty()) {
            content.addView(text("此運動時段沒有心率樣本", 13f, HealthVisuals.secondary), lp(bottom = 8))
        }
        content.addView(SeriesChartView(this, exerciseHeartSamples.map { it.beatsPerMinute.toDouble() }, Color.rgb(255, 96, 118)), LinearLayout.LayoutParams(-1, dp(150)))
        content.addView(text("VO₂max、訓練負荷與爬升效率為運動估算值，會隨 GPS 與心率資料完整度改變。", 12f, HealthVisuals.secondary), lp(top = 12))
    }

    private fun metricSummary(label: String, unit: String, result: WorkoutMetricResult): String {
        result.status?.let { return "$label：$it" }
        if (result.samples.isEmpty()) return "$label：此路線時段沒有可讀樣本。"
        return result.samples.groupBy { it.origin.ifBlank { "來源未標示" } }.toSortedMap().entries.joinToString("\n") { (origin, samples) ->
            "$label · $origin：${samples.size} 個樣本；樣本平均 ${"%.1f".format(samples.map { it.value }.average())} $unit"
        }
    }

    private fun panel(title: String, message: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = HealthVisuals.panel(context, 24, true)
        addView(text(title, 20f, Color.WHITE, true))
        addView(text(message, 15f, Color.rgb(220, 224, 234)).apply { setLineSpacing(dp(7).toFloat(), 1f) }, lp(top = 12))
    }

    private fun formatPace(value: Double): String {
        val minutes = value.toInt()
        val seconds = ((value - minutes) * 60).roundToInt().coerceIn(0, 59)
        return "%d:%02d 分/km".format(minutes, seconds)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun lp(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val radius = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}

private class RouteMapView(
    context: android.content.Context,
    private val points: List<WorkoutRouteStore.Point>,
    private val heart: List<HeartRateRecord.Sample>
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 7f; strokeCap = Paint.Cap.ROUND }
    override fun onDraw(canvas: Canvas) {
        if (points.size < 2) return
        val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
        val pad = 34f; val latRange = (maxLat - minLat).takeIf { it > 0 } ?: 1.0; val lonRange = (maxLon - minLon).takeIf { it > 0 } ?: 1.0
        fun x(point: WorkoutRouteStore.Point) = pad + ((point.longitude - minLon) / lonRange * (width - pad * 2)).toFloat()
        fun y(point: WorkoutRouteStore.Point) = height - pad - ((point.latitude - minLat) / latRange * (height - pad * 2)).toFloat()
        points.zipWithNext().forEach { (a, b) ->
            val nearest = heart.minByOrNull { abs(it.time.toEpochMilli() - b.time) }?.beatsPerMinute ?: 0
            paint.color = when { nearest >= 160 -> Color.rgb(255, 72, 92); nearest >= 130 -> Color.rgb(255, 157, 65); nearest > 0 -> Color.rgb(59, 205, 157); else -> Color.rgb(112, 178, 255) }
            canvas.drawLine(x(a), y(a), x(b), y(b), paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(x(points.first()), y(points.first()), 10f, paint)
        paint.color = Color.rgb(255, 96, 118)
        canvas.drawCircle(x(points.last()), y(points.last()), 10f, paint)
    }
}

private class SeriesChartView(context: android.content.Context, private val values: List<Double>, color: Int) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 5f; style = Paint.Style.STROKE }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(35, 255, 255, 255); strokeWidth = 1f }
    init { background = HealthVisuals.panel(context, 20) }
    override fun onDraw(canvas: Canvas) {
        repeat(3) { index -> canvas.drawLine(20f, height * (index + 1) / 4f, width - 20f, height * (index + 1) / 4f, grid) }
        if (values.size < 2) return
        val min = values.min(); val range = (values.max() - min).takeIf { it > 0 } ?: 1.0
        val path = android.graphics.Path()
        values.forEachIndexed { index, value ->
            val x = 20f + index.toFloat() / values.lastIndex * (width - 40f)
            val y = height - 20f - ((value - min) / range * (height - 40f)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, line)
    }
}
