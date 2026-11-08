package com.jaysato.xiaomihealthbridge

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import java.util.concurrent.Executors

/**
 * Self-contained walking workout UX (Apple Fitness / Galaxy / Fitbit style):
 * live timer + HR + start / pause / end, then sync route + session to phone.
 */
class WearWorkoutActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var timerView: TextView
    private lateinit var hrView: TextView
    private lateinit var metaView: TextView
    private lateinit var startEndButton: Button
    private lateinit var pauseButton: Button

    private var active = false
    private var paused = false
    private var startMillis = 0L
    private var pauseStarted = 0L
    private var totalPaused = 0L
    private var lastBpm = 0L
    private var lastBpmTimestamp = 0L
    private var lastWorkoutHeartRateSyncElapsed = 0L
    private var lastWorkoutHeartRateSaveElapsed = 0L
    private var lastWorkoutHeartRateSavedTimestamp = 0L
    private var lastWorkoutHeartRateMeasurementTimestamp = 0L
    private val heartRates = mutableListOf<Long>()
    private val routePoints = mutableListOf<String>()
    private val workoutMetricCursors = mutableMapOf<String, WorkoutIntervalMetricPolicy.Interval>()
    private val workoutMetricTotals = mutableMapOf("steps" to 0.0, "distance" to 0.0, "calories" to 0.0)
    private data class PendingWorkoutMetric(
        val metric: String,
        val value: Double,
        val interval: WorkoutIntervalMetricPolicy.Interval,
        val request: PutDataRequest,
        var attempts: Int = 0,
        var retry: Runnable? = null
    )
    private val workoutMetricQueues = mutableMapOf<String, java.util.ArrayDeque<PendingWorkoutMetric>>()
    private val workoutMetricPendingKeys = mutableSetOf<String>()
    private val workoutMetricInFlight = mutableSetOf<String>()
    private var ending = false
    private val endedCallbackTimeout = Runnable {
        if (ending) {
            ending = false
            android.util.Log.w(TAG, "Workout ENDED callback timeout")
            runCatching { HealthServices.getClient(this).exerciseClient.clearUpdateCallbackAsync(callback) }
        }
    }
    private var gpsFix = false
    private var walkingTypes: Set<DataType<*, *>> = emptySet()

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            timerView.text = formatElapsed()
            mainHandler.postDelayed(this, 1_000L)
        }
    }

    private val callback: ExerciseUpdateCallback = object : ExerciseUpdateCallback {
        override fun onRegistered() = Unit
        override fun onRegistrationFailed(throwable: Throwable) {
            runOnUiThread { metaView.text = "運動回呼失敗：${throwable.message}" }
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val boot = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
            val state = update.exerciseStateInfo.state.toString()
            val endedUpdate = state.contains("ENDED")
            update.latestMetrics.getData(DataType.HEART_RATE_BPM)
                .maxByOrNull { it.getTimeInstant(boot) }?.let { point ->
                val bpm = point.value.toLong()
                val measurementTimestamp = point.getTimeInstant(boot).toEpochMilli()
                if (!WearDataSyncPolicy.isWorkoutHeartRateSampleEligible(
                        active, startMillis, lastWorkoutHeartRateMeasurementTimestamp, measurementTimestamp
                    )
                ) return@let
                lastWorkoutHeartRateMeasurementTimestamp = measurementTimestamp
                lastBpm = bpm
                lastBpmTimestamp = measurementTimestamp
                if (!paused) heartRates += bpm
                runOnUiThread {
                    hrView.text = "$bpm"
                    metaView.text = "${zone(bpm)} · ${if (gpsFix) "GPS 已定位" else "定位／記錄中"}"
                }
                publishWorkoutHeartRate(bpm, measurementTimestamp)
            }
            runOnUiThread {
                paused = state.contains("PAUSED")
                pauseButton.text = if (paused) "繼續" else "暫停"
            }
            update.latestMetrics.getData(DataType.LOCATION).forEach { point ->
                val loc = point.value
                val time = point.getTimeInstant(boot).toEpochMilli()
                if (routePoints.size < 2_000 && time >= startMillis) {
                    routePoints += "$time,${loc.latitude},${loc.longitude},${loc.altitude}"
                    if (!gpsFix) {
                        gpsFix = true
                        runOnUiThread { metaView.text = "${zone(lastBpm)} · GPS 已定位" }
                    }
                }
            }
            if (WorkoutMetricDeliveryPolicy.shouldProcessMetrics(active, ending, endedUpdate)) {
                syncWorkoutIntervalMetrics(update, boot)
            }
            if (endedUpdate) {
                active = false
                paused = false
                ending = false
                mainHandler.removeCallbacks(endedCallbackTimeout)
                runCatching { HealthServices.getClient(this@WearWorkoutActivity).exerciseClient.clearUpdateCallbackAsync(callback) }
            }
        }

        override fun onLapSummaryReceived(summary: ExerciseLapSummary) = Unit
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = "WORKOUT"; textSize = 10f; letterSpacing = .14f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(86, 174, 255))
            })
            addView(TextView(context).apply {
                text = "步行運動"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setPadding(0, dp(4), 0, dp(2))
            })
            addView(TextView(context).apply {
                text = "00:00"; textSize = 40f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setPadding(0, dp(8), 0, 0)
            }.also { timerView = it })
            addView(TextView(context).apply {
                text = "—"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.rgb(255, 80, 70))
            }.also { hrView = it })
            addView(TextView(context).apply {
                text = "bpm · 開始後顯示區間"; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(160, 170, 190)); setPadding(0, 0, 0, dp(10))
            }.also { metaView = it })
            addView(Button(context).apply {
                text = "開始"; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE)
                background = pill("#236A82", "#174759")
                setOnClickListener { if (active) endWorkout() else startWorkout() }
            }.also { startEndButton = it }, LinearLayout.LayoutParams(-1, dp(50)))
            addView(Button(context).apply {
                text = "暫停"; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
                background = pill("#49494F", "#2B2B30"); isEnabled = false
                setOnClickListener { togglePause() }
            }.also { pauseButton = it }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
            addView(TextView(context).apply {
                text = "對齊 Apple Fitness／Galaxy／Fitbit：本頁直接開始／暫停／結束並同步手機。\n運動中不適請停止並就醫。"
                textSize = 11f; setTextColor(Color.rgb(255, 190, 120)); setPadding(0, dp(12), 0, 0)
            })
        }
        WearUi.installScrollable(this, content)
        loadCapabilities()
        if (intent.getBooleanExtra(EXTRA_AUTO_START, false)) {
            content.post { startWorkout() }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(tick)
        mainHandler.removeCallbacks(endedCallbackTimeout)
        if (active || ending) {
            runCatching {
                HealthServices.getClient(this).exerciseClient.endExerciseAsync()
                HealthServices.getClient(this).exerciseClient.clearUpdateCallbackAsync(callback)
            }
        }
        bg.shutdownNow()
        super.onDestroy()
    }

    private fun loadCapabilities() {
        val future = HealthServices.getClient(this).exerciseClient.getCapabilitiesAsync()
        future.addListener({
            runCatching {
                val caps = future.get()
                walkingTypes = caps.typeToCapabilities[ExerciseType.WALKING]?.supportedDataTypes.orEmpty()
            }.onFailure {
                runOnUiThread { metaView.text = "無法讀取運動能力：${it.message}" }
            }
        }, { r -> mainHandler.post(r) })
    }

    private fun startWorkout() {
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.ACCESS_FINE_LOCATION),
                41
            )
            metaView.text = "請授權後再按開始"
            return
        }
        if (walkingTypes.isEmpty()) {
            metaView.text = "此韌體未回報步行運動支援"
            return
        }
        val requested = setOf(
            DataType.HEART_RATE_BPM, DataType.STEPS, DataType.DISTANCE, DataType.CALORIES, DataType.LOCATION
        ).filter { it in walkingTypes }.toSet()
        val gps = DataType.LOCATION in requested &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val config = ExerciseConfig.builder(ExerciseType.WALKING)
            .setDataTypes(if (gps) requested else requested - DataType.LOCATION)
            .setIsGpsEnabled(gps)
            .build()
        val client = HealthServices.getClient(this).exerciseClient
        client.setUpdateCallback(callback)
        val future = client.startExerciseAsync(config)
        future.addListener({
            runCatching { future.get() }.onSuccess {
                active = true
                paused = false
                startMillis = System.currentTimeMillis()
                pauseStarted = 0L
                totalPaused = 0L
                heartRates.clear()
                routePoints.clear()
                workoutMetricCursors.clear()
                workoutMetricTotals.keys.forEach { workoutMetricTotals[it] = 0.0 }
                workoutMetricQueues.clear()
                workoutMetricPendingKeys.clear()
                workoutMetricInFlight.clear()
                ending = false
                gpsFix = false
                lastBpm = 0L
                lastBpmTimestamp = 0L
                lastWorkoutHeartRateSyncElapsed = 0L
                lastWorkoutHeartRateSaveElapsed = 0L
                lastWorkoutHeartRateSavedTimestamp = 0L
                lastWorkoutHeartRateMeasurementTimestamp = 0L
                runOnUiThread {
                    startEndButton.text = "結束並同步"
                    pauseButton.isEnabled = true
                    pauseButton.text = "暫停"
                    metaView.text = if (gps) "GPS 定位中 · 請到開闊處" else "步行記錄中"
                    hrView.text = "—"
                    timerView.text = "00:00"
                }
                mainHandler.removeCallbacks(tick)
                mainHandler.post(tick)
            }.onFailure {
                runOnUiThread { metaView.text = "啟動失敗：${it.message}" }
            }
        }, { r -> mainHandler.post(r) })
    }

    private fun togglePause() {
        if (!active) return
        val client = HealthServices.getClient(this).exerciseClient
        val wasPaused = paused
        pauseButton.isEnabled = false
        val future = if (wasPaused) client.resumeExerciseAsync() else client.pauseExerciseAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess {
                val now = System.currentTimeMillis()
                if (wasPaused) {
                    if (pauseStarted > 0) totalPaused += now - pauseStarted
                    pauseStarted = 0L
                    paused = false
                } else {
                    pauseStarted = now
                    paused = true
                }
                runOnUiThread {
                    pauseButton.isEnabled = true
                    pauseButton.text = if (paused) "繼續" else "暫停"
                }
            }.onFailure {
                runOnUiThread {
                    pauseButton.isEnabled = true
                    metaView.text = "暫停控制失敗：${it.message}"
                }
            }
        }, { r -> mainHandler.post(r) })
    }

    private fun endWorkout() {
        if (!active) return
        ending = true
        mainHandler.removeCallbacks(tick)
        val client = HealthServices.getClient(this).exerciseClient
        val future = client.endExerciseAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess {
                val end = System.currentTimeMillis()
                if (paused && pauseStarted > 0) totalPaused += end - pauseStarted
                val avg = heartRates.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
                if (lastBpm > 0L && lastBpmTimestamp > 0L) {
                    publishWorkoutHeartRate(lastBpm, lastBpmTimestamp, force = true)
                }
                val request = PutDataMapRequest.create("/health/exercise/$end").apply {
                    dataMap.putString("metric", "exercise")
                    dataMap.putDouble("value", 1.0)
                    dataMap.putLong("start", startMillis)
                    dataMap.putLong("end", end)
                    dataMap.putStringArray("route_points", routePoints.toTypedArray())
                }.asPutDataRequest().setUrgent()
                val syncTask = Wearable.getDataClient(this).putDataItem(request)
                WearHealthStore.recordExercise(
                    this, startMillis, end, avg, zone(avg), totalPaused, routePoints.toList()
                )
                active = false
                paused = false
                if (ending) {
                    mainHandler.removeCallbacks(endedCallbackTimeout)
                    mainHandler.postDelayed(endedCallbackTimeout, ENDED_CALLBACK_TIMEOUT_MILLIS)
                }
                runOnUiThread {
                    startEndButton.text = "再次開始"
                    pauseButton.isEnabled = false
                    timerView.text = formatElapsed(end)
                    hrView.text = if (avg > 0) avg.toString() else "—"
                    metaView.text = "已儲存手錶 · 正在同步手機 · ${zone(avg)}"
                }
                syncTask
                    .addOnSuccessListener {
                        WearHealthStore.markSync(this, true, "運動與路線已同步")
                        runOnUiThread { metaView.text = "已同步 · ${zone(avg)} · ${if (routePoints.isEmpty()) "未記錄路線" else "路線已記錄"}" }
                    }
                    .addOnFailureListener { error ->
                        WearHealthStore.markSync(this, false, "運動已儲存；手機同步失敗")
                        android.util.Log.w(TAG, "Workout Data Layer sync failed: ${error.javaClass.simpleName}")
                        runOnUiThread { metaView.text = "已儲存手錶 · 手機同步失敗 · ${zone(avg)}" }
                    }
            }.onFailure {
                ending = false
                runOnUiThread { metaView.text = "結束失敗：${it.message}" }
            }
        }, { r -> mainHandler.post(r) })
    }

    private fun publishWorkoutHeartRate(bpm: Long, timestamp: Long, force: Boolean = false) {
        val elapsed = SystemClock.elapsedRealtime()
        if (WearDataSyncPolicy.shouldSaveWorkoutHeartRate(
                lastWorkoutHeartRateSavedTimestamp, timestamp,
                lastWorkoutHeartRateSaveElapsed, elapsed, force
            )
        ) {
            WearHealthStore.recordHeartRate(this, bpm, timestamp)
            lastWorkoutHeartRateSaveElapsed = elapsed
            lastWorkoutHeartRateSavedTimestamp = timestamp
        }
        if (force || WearDataSyncPolicy.shouldSendPreview(lastWorkoutHeartRateSyncElapsed, elapsed)) {
            val payload = HealthServicesHeartRatePayload.create(bpm, timestamp) ?: return
            val request = PutDataMapRequest.create("/heart-rate/${payload.eventTimestampMillis}").apply {
                dataMap.putLong("bpm", payload.bpm)
                dataMap.putLong("timestamp", payload.eventTimestampMillis)
                dataMap.putString("timestamp_source", payload.timestampSource)
            }.asPutDataRequest()
            Wearable.getDataClient(this).putDataItem(request)
                .addOnFailureListener { error ->
                    android.util.Log.w(TAG, "Workout HR preview sync failed: ${error.javaClass.simpleName}")
                }
            lastWorkoutHeartRateSyncElapsed = elapsed
        }
    }

    private fun syncWorkoutIntervalMetrics(update: ExerciseUpdate, boot: Instant) {
        val now = System.currentTimeMillis()
        update.latestMetrics.getData(DataType.STEPS).sortedBy { it.getStartInstant(boot) }.forEach { point ->
            acceptWorkoutMetric("steps", point.value.toDouble(), point.getStartInstant(boot).toEpochMilli(), point.getEndInstant(boot).toEpochMilli(), now)
        }
        update.latestMetrics.getData(DataType.DISTANCE).sortedBy { it.getStartInstant(boot) }.forEach { point ->
            acceptWorkoutMetric("distance", point.value, point.getStartInstant(boot).toEpochMilli(), point.getEndInstant(boot).toEpochMilli(), now)
        }
        update.latestMetrics.getData(DataType.CALORIES).sortedBy { it.getStartInstant(boot) }.forEach { point ->
            acceptWorkoutMetric("calories", point.value, point.getStartInstant(boot).toEpochMilli(), point.getEndInstant(boot).toEpochMilli(), now)
        }
    }

    private fun acceptWorkoutMetric(metric: String, value: Double, start: Long, end: Long, now: Long) {
        val queue = workoutMetricQueues.getOrPut(metric) { java.util.ArrayDeque() }
        val previous = queue.peekLast()?.interval ?: workoutMetricCursors[metric]
        val decision = WorkoutIntervalMetricPolicy.evaluate(metric, value, start, end, startMillis, now, previous)
        if (!decision.accepted) {
            if (decision.rejection != WorkoutIntervalMetricPolicy.Rejection.DUPLICATE) {
                android.util.Log.w(TAG, "Workout metric rejected: $metric ${decision.rejection}")
            }
            return
        }

        if (queue.size >= WorkoutMetricDeliveryPolicy.MAX_PENDING_PER_METRIC) {
            android.util.Log.e(TAG, "Workout metric queue full: $metric")
            runOnUiThread { metaView.text = "同步佇列已滿：$metric" }
            return
        }
        val path = WorkoutMetricDeliveryPolicy.intervalPath(metric, start, end)
        if (!workoutMetricPendingKeys.add(path)) return
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putString("metric", metric)
            dataMap.putDouble("value", value)
            dataMap.putLong("start", start)
            dataMap.putLong("end", end)
        }.asPutDataRequest()
        queue.add(PendingWorkoutMetric(metric, value, WorkoutIntervalMetricPolicy.Interval(start, end), request))
        sendNextWorkoutMetric(metric)
    }

    private fun sendNextWorkoutMetric(metric: String) {
        if (!workoutMetricInFlight.add(metric)) return
        val queue = workoutMetricQueues[metric]
        val pending = queue?.peekFirst()
        if (pending == null) {
            workoutMetricInFlight.remove(metric)
            return
        }
        pending.attempts++
        Wearable.getDataClient(this).putDataItem(pending.request)
            .addOnSuccessListener {
                if (workoutMetricQueues[metric]?.peekFirst() !== pending) return@addOnSuccessListener
                workoutMetricQueues[metric]?.removeFirst()
                workoutMetricPendingKeys.remove(WorkoutMetricDeliveryPolicy.intervalPath(metric, pending.interval.startMillis, pending.interval.endMillis))
                workoutMetricCursors[metric] = pending.interval
                workoutMetricTotals[metric] = (workoutMetricTotals[metric] ?: 0.0) + pending.value
                pending.retry?.let(mainHandler::removeCallbacks)
                workoutMetricInFlight.remove(metric)
                updateWorkoutMetricSummary()
                sendNextWorkoutMetric(metric)
            }
            .addOnFailureListener { error ->
                val delay = WorkoutMetricDeliveryPolicy.retryDelayAfterFailure(pending.attempts)
                if (delay != null) {
                    android.util.Log.w(TAG, "Workout $metric interval attempt ${pending.attempts} failed; retrying")
                    val retry = Runnable {
                        pending.retry = null
                        workoutMetricInFlight.remove(metric)
                        sendNextWorkoutMetric(metric)
                    }
                    pending.retry = retry
                    mainHandler.postDelayed(retry, delay)
                } else {
                    android.util.Log.e(TAG, "Workout $metric interval exhausted retries: ${error.javaClass.simpleName}")
                    workoutMetricQueues[metric]?.remove(pending)
                    workoutMetricPendingKeys.remove(WorkoutMetricDeliveryPolicy.intervalPath(metric, pending.interval.startMillis, pending.interval.endMillis))
                    workoutMetricInFlight.remove(metric)
                    runOnUiThread { metaView.text = "$metric 同步失敗，請重試運動同步" }
                    sendNextWorkoutMetric(metric)
                }
            }
    }

    private fun updateWorkoutMetricSummary() {
        val steps = workoutMetricTotals["steps"]?.toLong() ?: 0L
        val distance = workoutMetricTotals["distance"] ?: 0.0
        val calories = workoutMetricTotals["calories"] ?: 0.0
        runOnUiThread {
            metaView.text = "步數 $steps · ${distance.toInt()} m · 總熱量 ${calories.toInt()} kcal"
        }
    }

    private fun formatElapsed(now: Long = System.currentTimeMillis()): String {
        if (startMillis <= 0L) return "00:00"
        var pauseExtra = totalPaused
        if (paused && pauseStarted > 0) pauseExtra += now - pauseStarted
        val sec = ((now - startMillis - pauseExtra).coerceAtLeast(0L) / 1000L).toInt()
        return "%02d:%02d".format(sec / 60, sec % 60)
    }

    private fun zone(bpm: Long) = when {
        bpm <= 0 -> "無心率"
        bpm < 100 -> "暖身區"
        bpm < 130 -> "燃脂區"
        bpm < 160 -> "有氧區"
        else -> "高強度區"
    }

    private fun pill(a: String, b: String) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(a), Color.parseColor(b))
    ).apply { cornerRadius = dp(8).toFloat() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_AUTO_START = "auto_start"
        private const val TAG = "WearWorkoutActivity"
        private const val ENDED_CALLBACK_TIMEOUT_MILLIS = 5_000L
    }
}
