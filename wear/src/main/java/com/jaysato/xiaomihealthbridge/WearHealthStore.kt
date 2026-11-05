package com.jaysato.xiaomihealthbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object WearHealthStore {
    private const val PREFS = "wear_health_history"
    private const val MAX_POINTS = 96
    private const val STEP_GOAL = 8_000L
    private const val CHANNEL = "daily_goals"
    private const val KEY_UNVERIFIED_OXYGEN_PURGED = "unverified_oxygen_purged_v2"

    fun recordHeartRate(context: Context, bpm: Long, time: Long = System.currentTimeMillis()) {
        append(context, "heart", "$time,$bpm")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("latest_heart", bpm).putLong("latest_heart_time", time).apply()
    }

    /** Most recent on-watch heart rate (WHS / measure), or null if missing / stale. */
    fun latestHeartBpm(context: Context, maxAgeMs: Long = 5 * 60_000L): Double? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bpm = prefs.getLong("latest_heart", 0L)
        val time = prefs.getLong("latest_heart_time", 0L)
        if (bpm !in 30L..220L || time <= 0L) return null
        if (!WearMeasurementPolicy.hasRecentSensorSuiteStatus(time, System.currentTimeMillis(), maxAgeMs)) return null
        return bpm.toDouble()
    }

    fun recordOpticalBp(
        context: Context,
        sbp: Double,
        dbp: Double,
        time: Long = System.currentTimeMillis()
    ) {
        if (!sbp.isFinite() || !dbp.isFinite()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("latest_optical_sbp", sbp.toFloat())
            .putFloat("latest_optical_dbp", dbp.toFloat())
            .putLong("latest_optical_bp_time", time)
            .apply()
    }

    /** Latest research optical BP estimate, or null if missing. */
    fun latestOpticalBp(context: Context): Pair<Double, Double>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sbp = prefs.getFloat("latest_optical_sbp", Float.NaN)
        val dbp = prefs.getFloat("latest_optical_dbp", Float.NaN)
        if (!sbp.isFinite() || !dbp.isFinite()) return null
        return sbp.toDouble() to dbp.toDouble()
    }

    fun recordOxygen(context: Context, oxygen: Double, time: Long = System.currentTimeMillis()) {
        append(context, "oxygen", "$time,${"%.1f".format(java.util.Locale.US, oxygen)}")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("latest_oxygen", oxygen.toFloat()).putLong("latest_oxygen_time", time).apply()
    }

    /** Removes local values written by legacy builds that inferred percent from vendor raw packets. */
    fun clearLegacyUnverifiedOxygen(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_UNVERIFIED_OXYGEN_PURGED, false)) return
        prefs.edit()
            .remove("oxygen")
            .remove("latest_oxygen")
            .remove("latest_oxygen_time")
            .putBoolean(KEY_UNVERIFIED_OXYGEN_PURGED, true)
            .apply()
    }

    fun recordMetric(context: Context, metric: String, value: Double, end: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putFloat("latest_$metric", value.toFloat()).putLong("latest_${metric}_time", end).apply()
        if (metric == "steps") {
            val date = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            val key = "steps_$date"
            val total = prefs.getLong(key, 0L) + value.toLong()
            prefs.edit().putLong(key, total).apply()
            if (total >= STEP_GOAL && !prefs.getBoolean("goal_$date", false)) {
                prefs.edit().putBoolean("goal_$date", true).apply()
                notifyGoal(context, total)
            }
        }
    }

    fun recordExercise(context: Context, start: Long, end: Long, averageHeartRate: Long, zone: String, pausedMillis: Long, route: List<String>) {
        append(context, "exercise", "$end,$start,$averageHeartRate,$zone,$pausedMillis", 20)
        if (route.size >= 2) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_route", route.joinToString("\n")).apply()
    }

    fun lastRoute(context: Context): List<String> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("last_route", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList()

    fun recordQuickEvent(context: Context, type: String, detail: String) {
        append(context, "events", "${System.currentTimeMillis()},$type,$detail", 40)
    }

    fun markSync(context: Context, success: Boolean, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("sync_ok", success).putString("sync_message", message)
            .putLong("sync_time", System.currentTimeMillis()).apply()
    }

    fun dashboard(context: Context): Dashboard {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        return Dashboard(
            heartRate = prefs.getLong("latest_heart", 0),
            oxygen = prefs.getFloat("latest_oxygen", 0f).toDouble(),
            steps = prefs.getLong("steps_$today", 0),
            syncOk = prefs.getBoolean("sync_ok", true),
            syncMessage = prefs.getString("sync_message", "等待背景同步").orEmpty(),
            syncTime = prefs.getLong("sync_time", 0),
            oxygenTrend = history(prefs, "oxygen", 7),
            recentExercises = historyStrings(prefs, "exercise", 5)
        )
    }

    fun heartPoints(context: Context): List<Pair<Long, Long>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return historyStrings(prefs, "heart", MAX_POINTS).mapNotNull { row ->
            val parts = row.split(',')
            val time = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val bpm = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            if (bpm in 1..300) time to bpm else null
        }
    }

    fun historyText(context: Context): String {
        val dashboard = dashboard(context)
        val heart = heartPoints(context).takeLast(8).asReversed().joinToString("\n") { (t, bpm) ->
            "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(t)}  $bpm bpm"
        }.ifBlank { "尚無心率紀錄" }
        val exercises = dashboard.recentExercises.mapNotNull { row ->
            val parts = row.split(',')
            if (parts.size < 5) null else {
                val duration = ((parts[0].toLongOrNull() ?: 0) - (parts[1].toLongOrNull() ?: 0) - (parts[4].toLongOrNull() ?: 0)) / 60_000
                "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(parts[0].toLongOrNull() ?: 0)}  ${duration.coerceAtLeast(0)} 分鐘\n平均 ${parts[2]} bpm · ${parts[3]}"
            }
        }
        val oxygen = if (dashboard.oxygenTrend.isEmpty()) "尚無資料" else dashboard.oxygenTrend.joinToString("  ") { "${it.toInt()}%" }
        return "心率最近紀錄\n$heart\n\nSpO₂ 最近紀錄\n$oxygen\n\n運動歷史\n${exercises.ifEmpty { listOf("尚無運動紀錄") }.joinToString("\n\n")}"
    }

    private fun append(context: Context, key: String, row: String, max: Int = MAX_POINTS) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = prefs.getString(key, "").orEmpty().lineSequence().filter { it.isNotBlank() }.toMutableList()
        values += row
        prefs.edit().putString(key, values.takeLast(max).joinToString("\n")).apply()
    }

    private fun history(prefs: android.content.SharedPreferences, key: String, max: Int): List<Double> =
        historyStrings(prefs, key, max).mapNotNull { it.substringAfter(',').toDoubleOrNull() }

    private fun historyStrings(prefs: android.content.SharedPreferences, key: String, max: Int): List<String> =
        prefs.getString(key, "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList().takeLast(max)

    private fun notifyGoal(context: Context, steps: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "每日健康目標", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, WearMainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(8000, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("每日步數目標達成")
            .setContentText("今天已完成 %,d 步".format(steps))
            .setContentIntent(intent).setAutoCancel(true).build())
    }

    data class Dashboard(
        val heartRate: Long,
        val oxygen: Double,
        val steps: Long,
        val syncOk: Boolean,
        val syncMessage: String,
        val syncTime: Long,
        val oxygenTrend: List<Double>,
        val recentExercises: List<String>
    )
}
