package com.jaysato.xiaomihealthbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat

object MobileSafetyAlerts {
    const val PATH = "/safety-alert/"
    const val PREFS = "safety_alerts"
    private const val ROUTINE_CHANNEL = "watch_safety_alerts_routine"
    private const val CRITICAL_CHANNEL = "watch_safety_alerts_critical"
    private const val ROUTINE_NOTIFICATION_ID = 809
    private const val CRITICAL_NOTIFICATION_ID = 810
    private const val RETRY_TYPE = "receipt_retry_type"
    private const val RETRY_TIMESTAMP = "receipt_retry_timestamp"
    private const val TAG = "MobileSafetyAlerts"

    fun areRoutineAlertsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("routine_alerts_enabled", false)

    fun setRoutineAlertsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("routine_alerts_enabled", enabled).apply()
    }

    @Synchronized
    fun receive(context: Context, type: String, value: Double, timestamp: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val eventKey = "last_event_$type"
        val retryingReceipt = prefs.getString(RETRY_TYPE, null) == type &&
            prefs.getLong(RETRY_TIMESTAMP, 0L) == timestamp
        if (!retryingReceipt && !MobileAlertPolicy.shouldProcessEvent(type, timestamp, prefs.getLong(eventKey, 0L), now)) return true
        val critical = MobileAlertPolicy.isCritical(type)
        var shouldNotify = true
        val editor = prefs.edit()
            .putLong(eventKey, timestamp)
            .putString("last_type", type)
            .putFloat("last_value", value.toFloat())
            .putLong("last_timestamp", timestamp)
            .remove(RETRY_TYPE)
            .remove(RETRY_TIMESTAMP)
        if (type == "fall_cancelled") {
            val committed = runCatching { editor.commit() }.getOrDefault(false)
            if (!committed) {
                prefs.edit().putString(RETRY_TYPE, type).putLong(RETRY_TIMESTAMP, timestamp).apply()
                return false
            }
            if (timestamp >= prefs.getLong("last_event_fall_detected", 0L)) {
                runCatching { context.getSystemService(NotificationManager::class.java).cancel(CRITICAL_NOTIFICATION_ID) }
                    .onFailure { Log.w(TAG, "cancel_notification_failed type=${it.javaClass.simpleName}") }
            }
            return true
        }
        if (type == "fall_detected" && timestamp <= prefs.getLong("last_event_fall_cancelled", 0L)) shouldNotify = false
        if (!MobileAlertPolicy.bypassesRoutineThrottle(type)) {
            val previousNotification = prefs.getLong("routine_last_notification_time", 0L)
            if (!MobileAlertPolicy.shouldSendRoutine(
                    prefs.getBoolean("routine_alerts_enabled", false), previousNotification, now
                )) shouldNotify = false
            else editor.putLong("routine_last_notification_time", now)
        }
        val committed = runCatching { editor.commit() }.getOrDefault(false)
        if (!committed) {
            prefs.edit().putString(RETRY_TYPE, type).putLong(RETRY_TIMESTAMP, timestamp).apply()
            return false
        }
        if (!shouldNotify) return true
        val title = when {
            type == "fall_detected" -> "手錶偵測到可能跌倒"
            type == "fall_sos_requested" -> "手錶使用者要求緊急協助"
            type == "fall_cancelled" -> "跌倒警示已取消"
            type == "heart_high" -> "手錶持續高心率"
            type == "heart_low" -> "手錶持續低心率"
            else -> "手錶安全警示"
        }
        val detail = when {
            type.startsWith("heart_") -> "${value.toLong()} bpm；若伴隨胸痛、喘或暈眩請立即就醫。"
            type == "fall_detected" -> "偵測到 ${"%.1f".format(value)} m/s² 衝擊，請確認使用者安全。"
            else -> title
        }
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                ROUTINE_CHANNEL, "健康提醒（靜音）", NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null); enableVibration(false) })
            manager.createNotificationChannel(NotificationChannel(
                CRITICAL_CHANNEL, "緊急安全警示", NotificationManager.IMPORTANCE_HIGH
            ))
            val open = PendingIntent.getActivity(context, notificationIdFor(critical), Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val channel = if (critical) CRITICAL_CHANNEL else ROUTINE_CHANNEL
            val notificationId = notificationIdFor(critical)
            manager.notify(notificationId, NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.app_icon).setContentTitle(title).setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(if (critical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_ALARM).setContentIntent(open)
                .setOnlyAlertOnce(true).setAutoCancel(true).build())
        }.onFailure { Log.w(TAG, "notification_delivery_failed type=${it.javaClass.simpleName}") }
        return true
    }

    private fun notificationIdFor(critical: Boolean) =
        if (critical) CRITICAL_NOTIFICATION_ID else ROUTINE_NOTIFICATION_ID
}
