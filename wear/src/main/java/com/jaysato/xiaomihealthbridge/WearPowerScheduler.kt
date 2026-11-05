package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.work.WorkManager

enum class WearPowerMode { NORMAL, SAVER, CRITICAL }

enum class WearPowerPolicy {
    AUTO,
    FORCE_NORMAL,
    FORCE_SAVER,
    FORCE_CRITICAL
}

data class WearPowerState(
    val mode: WearPowerMode,
    val batteryPercent: Int,
    val charging: Boolean,
    val systemPowerSave: Boolean,
    val policy: WearPowerPolicy = WearPowerPolicy.AUTO
) {
    val allowReminderWork: Boolean
        get() = charging || mode == WearPowerMode.NORMAL
    val allowBackgroundSync: Boolean
        get() = charging || mode != WearPowerMode.CRITICAL
    val label: String
        get() = when (mode) {
            WearPowerMode.NORMAL -> "正常"
            WearPowerMode.SAVER -> "省電"
            WearPowerMode.CRITICAL -> "極省"
        }
    val detail: String
        get() = when (mode) {
            WearPowerMode.NORMAL -> "久坐提醒與背景同步照常；心率回報依所選間隔與系統批次"
            WearPowerMode.SAVER -> "延後久坐提醒；心率同步至少 5 分鐘，取樣仍由 Wear OS 控制"
            WearPowerMode.CRITICAL -> "暫停非必要背景工作與被動心率；僅手動量測"
        }
}

/**
 * Smart power scheduler for Watch 2 Pro.
 * Does not rewrite system AOD/brightness (OEM-locked); controls App WorkManager + measure policy.
 */
object WearPowerScheduler {
    const val DEFAULT_REMINDERS_ENABLED = false
    private const val PREFS = "wear_power_scheduler"
    private const val KEY_MODE = "mode"
    private const val KEY_LEVEL = "level"
    private const val KEY_UPDATED = "updated"
    private const val KEY_POLICY = "policy"
    private const val KEY_REMINDERS = "reminders_enabled"
    private const val KEY_REMINDERS_OPT_OUT_MIGRATED = "routine_reminders_opt_out_migrated"
    private const val KEY_DEFER_SYNC = "defer_sync_low_battery"
    private const val KEY_BATTERY_SAMPLES = "battery_samples"
    private const val MAX_BATTERY_SAMPLES = 12

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun policy(context: Context): WearPowerPolicy =
        runCatching { WearPowerPolicy.valueOf(prefs(context).getString(KEY_POLICY, WearPowerPolicy.AUTO.name)!!) }
            .getOrDefault(WearPowerPolicy.AUTO)

    fun setPolicy(context: Context, policy: WearPowerPolicy) {
        prefs(context).edit().putString(KEY_POLICY, policy.name).apply()
        apply(context)
    }

    fun remindersEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REMINDERS, DEFAULT_REMINDERS_ENABLED)

    fun setRemindersEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMINDERS, enabled).apply()
        apply(context)
    }

    fun deferSyncWhenLow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEFER_SYNC, true)

    fun setDeferSyncWhenLow(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEFER_SYNC, enabled).apply()
        apply(context)
    }

    fun apply(context: Context, refreshOxygenSchedule: Boolean = true): WearPowerState {
        migrateRoutineReminders(context)
        val baseState = currentState(context)
        recordBatterySample(context, baseState.batteryPercent)
        val decision = if (baseState.policy == WearPowerPolicy.AUTO) aiDecision(context) else null
        val state = if (decision != null && decision.mode != WearPowerMode.NORMAL) {
            baseState.copy(mode = decision.mode)
        } else baseState
        val passiveEnabled = PassiveMonitoringSettings.isEnabled(context)
        val passiveRegistered = context.getSharedPreferences(
            PassiveRegistration.PREFS,
            Context.MODE_PRIVATE
        ).getBoolean(PassiveRegistration.KEY_REGISTERED, false)
        val shouldRegisterPassive = PassiveRegistrationPolicy.shouldBeRegistered(passiveEnabled, state.mode)
        when {
            !shouldRegisterPassive && passiveRegistered ->
                PassiveRegistration.suspendForPower(context)
            shouldRegisterPassive && !passiveRegistered ->
                PassiveRegistration.register(context) {}
        }
        if (refreshOxygenSchedule) {
            WorkManager.getInstance(context).cancelUniqueWork(StartupReceiver.PASSIVE_REGISTRATION_PERIODIC)
            OxygenSyncWorker.schedule(context)
        }
        if (remindersEnabled(context) && state.allowReminderWork) {
            StandingReminderWorker.schedule(context)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(StandingReminderWorker.UNIQUE_WORK)
        }
        prefs(context).edit()
            .putString(KEY_MODE, state.mode.name)
            .putInt(KEY_LEVEL, state.batteryPercent)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
        return state
    }

    fun migrateRoutineReminders(context: Context) {
        val settings = prefs(context)
        if (!settings.getBoolean(KEY_REMINDERS_OPT_OUT_MIGRATED, false)) {
            settings.edit()
                .putBoolean(KEY_REMINDERS, false)
                .putBoolean(KEY_REMINDERS_OPT_OUT_MIGRATED, true)
                .apply()
        }
        if (!remindersEnabled(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(StandingReminderWorker.UNIQUE_WORK)
        }
    }

    fun currentState(context: Context): WearPowerState {
        val battery = context.getSystemService(BatteryManager::class.java)
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val charging = battery.isCharging
        val powerSave = context.getSystemService(PowerManager::class.java).isPowerSaveMode
        val policy = policy(context)
        return decide(level, charging, powerSave, policy)
    }

    fun batteryEstimate(context: Context): BatteryDrainEstimate =
        BatteryDrainEstimator.estimate(readBatterySamples(context), currentState(context).batteryPercent)

    fun aiDecision(context: Context, workload: PowerWorkload = PowerWorkload.IDLE): AiPowerDecision {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val state = currentState(context)
        val thermalLimited = runCatching {
            powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        }.getOrDefault(false)
        return AiPowerEngine.decide(
            batteryPercent = state.batteryPercent,
            charging = state.charging,
            thermalLimited = thermalLimited,
            drainPercentPerHour = batteryEstimate(context).percentPerHour,
            workload = workload
        )
    }

    private fun recordBatterySample(context: Context, level: Int) {
        val now = System.currentTimeMillis()
        val samples = readBatterySamples(context).toMutableList()
        if (samples.lastOrNull()?.let { now - it.elapsedMillis < 5 * 60_000L } == true) return
        samples += BatterySample(level, now)
        val encoded = samples.takeLast(MAX_BATTERY_SAMPLES)
            .joinToString(";") { "${it.levelPercent},${it.elapsedMillis}" }
        prefs(context).edit().putString(KEY_BATTERY_SAMPLES, encoded).apply()
    }

    private fun readBatterySamples(context: Context): List<BatterySample> =
        prefs(context).getString(KEY_BATTERY_SAMPLES, "").orEmpty()
            .split(';').mapNotNull { item ->
                val parts = item.split(',')
                if (parts.size != 2) return@mapNotNull null
                runCatching { BatterySample(parts[0].toInt(), parts[1].toLong()) }.getOrNull()
            }

    fun decide(
        level: Int,
        charging: Boolean,
        powerSave: Boolean,
        policy: WearPowerPolicy = WearPowerPolicy.AUTO
    ): WearPowerState {
        val automatic = when {
            charging -> WearPowerMode.NORMAL
            level <= 15 -> WearPowerMode.CRITICAL
            level <= 30 || powerSave -> WearPowerMode.SAVER
            else -> WearPowerMode.NORMAL
        }
        val mode = when (policy) {
            WearPowerPolicy.AUTO -> automatic
            WearPowerPolicy.FORCE_NORMAL -> WearPowerMode.NORMAL
            WearPowerPolicy.FORCE_SAVER -> WearPowerMode.SAVER
            WearPowerPolicy.FORCE_CRITICAL -> WearPowerMode.CRITICAL
        }
        return WearPowerState(mode, level, charging, powerSave, policy)
    }

    fun lastUpdatedMs(context: Context): Long = prefs(context).getLong(KEY_UPDATED, 0L)

    fun shouldDeferBackground(context: Context): Boolean {
        if (!deferSyncWhenLow(context)) return false
        val state = currentState(context)
        return !state.allowBackgroundSync
    }
}
