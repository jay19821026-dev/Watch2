package com.jaysato.xiaomihealthbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WearSafetyAlerts {
    private const val CHANNEL_HEART = "heart_alerts"
    private const val CHANNEL_FALL = "fall_alerts"
    private const val PREFS = "wear_safety_alert_state"
    private const val KEY_KIND = "heart_kind"
    private const val KEY_FIRST = "heart_first"
    private const val KEY_COUNT = "heart_count"
    private const val KEY_LAST_SAMPLE = "heart_last_sample"
    private const val KEY_LAST_OBSERVED = "heart_last_observed"
    private const val KEY_LAST_ALERT = "heart_last_alert"
    private const val KEY_LAST_FALL_PROMPT = "fall_last_prompt"
    private const val FALL_PROMPT_COOLDOWN_MILLIS = 2 * 60_000L

    fun observeHeartRate(context: Context, bpm: Long, timestamp: Long) {
        val thresholds = context.getSharedPreferences(WearMainActivity.THRESHOLD_PREFS, Context.MODE_PRIVATE)
        val high = thresholds.getLong(WearMainActivity.KEY_HIGH, WearMainActivity.DEFAULT_HIGH)
        val low = thresholds.getLong(WearMainActivity.KEY_LOW, WearMainActivity.DEFAULT_LOW)
        val kind = when {
            bpm >= high -> "high"
            bpm <= low -> "low"
            else -> ""
        }
        val state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val progress = WearHeartRateAlertPolicy.advance(
            kind,
            timestamp,
            state.getLong(KEY_LAST_OBSERVED, 0L),
            state.getString(KEY_KIND, "").orEmpty(),
            state.getLong(KEY_FIRST, 0L),
            state.getInt(KEY_COUNT, 0),
            state.getLong(KEY_LAST_SAMPLE, 0L)
        ) ?: return
        val editor = state.edit().putLong(KEY_LAST_OBSERVED, timestamp)
        if (kind.isEmpty()) {
            editor.remove(KEY_KIND).remove(KEY_FIRST).remove(KEY_COUNT).remove(KEY_LAST_SAMPLE).apply()
            return
        }
        editor.putString(KEY_KIND, progress.kind).putLong(KEY_FIRST, progress.firstTimestamp)
            .putInt(KEY_COUNT, progress.sampleCount).putLong(KEY_LAST_SAMPLE, progress.lastSampleTimestamp).apply()
        val lastAlert = state.getLong(KEY_LAST_ALERT, 0L)
        if (!WearHeartRateAlertPolicy.isSustained(progress, lastAlert)) return

        ensureChannels(context)
        val highRate = kind == "high"
        val title = if (highRate) "持續高心率" else "持續低心率"
        val body = "$bpm bpm 已持續超出設定範圍。若有胸痛、喘或暈眩，請立即求助。"
        val open = PendingIntent.getActivity(context, 710, Intent(context, WearMainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_HEART)
            .setSmallIcon(com.jaysato.xiaomihealthbridge.R.drawable.app_icon)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 300, 180, 300)).setContentIntent(open).setAutoCancel(true).build()
        context.getSystemService(NotificationManager::class.java).notify(710, notification)
        state.edit().putLong(KEY_LAST_ALERT, timestamp).putLong(KEY_FIRST, timestamp).putInt(KEY_COUNT, 0).apply()
        publish(context, "heart_$kind", bpm.toDouble(), timestamp)
    }

    fun showFallConfirmation(context: Context, magnitude: Double) {
        val now = System.currentTimeMillis()
        val state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousPrompt = state.getLong(KEY_LAST_FALL_PROMPT, 0L)
        if (previousPrompt > 0L && now >= previousPrompt && now - previousPrompt < FALL_PROMPT_COOLDOWN_MILLIS) return
        state.edit().putLong(KEY_LAST_FALL_PROMPT, now).apply()
        ensureChannels(context)
        val intent = Intent(context, FallConfirmationActivity::class.java)
            .putExtra(FallConfirmationActivity.EXTRA_MAGNITUDE, magnitude)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 711, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_FALL)
            .setSmallIcon(com.jaysato.xiaomihealthbridge.R.drawable.app_icon)
            .setContentTitle("偵測到可能跌倒").setContentText("請確認你是否安全")
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pending, true).setOngoing(true).setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(711, notification)
        context.startActivity(intent)
        publish(context, "fall_detected", magnitude, now)
    }

    fun cancelFall(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(711)
        publish(context, "fall_cancelled", 0.0, System.currentTimeMillis())
    }

    fun publish(context: Context, type: String, value: Double, timestamp: Long) {
        val request = PutDataMapRequest.create("/safety-alert/$type/$timestamp").apply {
            dataMap.putString("type", type); dataMap.putDouble("value", value); dataMap.putLong("timestamp", timestamp)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel(CHANNEL_HEART, "心率警示", NotificationManager.IMPORTANCE_HIGH))
            createNotificationChannel(NotificationChannel(CHANNEL_FALL, "跌倒安全", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            })
        }
    }
}
