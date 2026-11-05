package com.jaysato.xiaomihealthbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class StandingReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!WearPowerScheduler.remindersEnabled(applicationContext)) return Result.success()
        if (!WearPowerScheduler.currentState(applicationContext).allowReminderWork) return Result.success()
        val hour = LocalTime.now().hour
        if (hour !in 9..21) return Result.success()
        val dashboard = WearHealthStore.dashboard(applicationContext)
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getLong(KEY_LAST_STEPS, dashboard.steps)
        prefs.edit().putLong(KEY_LAST_STEPS, dashboard.steps).apply()
        if (dashboard.steps - previous < 200) notifyStand()
        return Result.success()
    }

    private fun notifyStand() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "久坐提醒（靜音）", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        })
        val intent = PendingIntent.getActivity(applicationContext, 1, Intent(applicationContext, WearMainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(LocalDate.now().dayOfYear, NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("起身活動一下")
            .setContentText("這一小時活動較少，走動 3 分鐘吧").setContentIntent(intent)
            .setOnlyAlertOnce(true).setAutoCancel(true).build())
    }

    companion object {
        private const val PREFS = "standing_reminder"
        private const val KEY_LAST_STEPS = "last_steps"
        private const val CHANNEL = "standing_reminders"
        const val UNIQUE_WORK = "standing_reminder_hourly"
        fun schedule(context: Context) = WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<StandingReminderWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
        )
    }
}
