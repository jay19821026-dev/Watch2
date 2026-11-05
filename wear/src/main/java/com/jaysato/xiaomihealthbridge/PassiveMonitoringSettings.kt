package com.jaysato.xiaomihealthbridge

import android.content.Context

internal object PassiveMonitoringSettings {
    const val PREFS = "passive_registration"
    const val KEY_ENABLED = "user_enabled"
    const val KEY_INTERVAL_MINUTES = "heart_rate_sync_interval_minutes"
    val supportedIntervalsMinutes = listOf(1, 5, 10, 30, 60, 240, 480, 720, CONTINUOUS_INTERVAL_MINUTES)

    fun isEnabled(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = migratedEnabledState(
            hasExplicitSetting = preferences.contains(KEY_ENABLED),
            explicitSetting = preferences.getBoolean(KEY_ENABLED, false),
            wasPreviouslyRegistered = preferences.getBoolean(PassiveRegistration.KEY_REGISTERED, false)
        )
        if (!preferences.contains(KEY_ENABLED)) {
            preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
        return enabled
    }

    internal fun migratedEnabledState(
        hasExplicitSetting: Boolean,
        explicitSetting: Boolean,
        wasPreviouslyRegistered: Boolean
    ): Boolean = if (hasExplicitSetting) explicitSetting else wasPreviouslyRegistered

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun intervalMinutes(context: Context): Int {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        return PassiveMonitoringPolicy.sanitizeInterval(stored)
    }

    fun setIntervalMinutes(context: Context, minutes: Int) {
        require(minutes in supportedIntervalsMinutes)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INTERVAL_MINUTES, minutes).apply()
    }

    fun intervalLabel(minutes: Int): String = when {
        minutes == CONTINUOUS_INTERVAL_MINUTES -> "連續（耗電較高）"
        minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} 小時"
        else -> "$minutes 分鐘"
    }

    const val DEFAULT_INTERVAL_MINUTES = 5
    const val CONTINUOUS_INTERVAL_MINUTES = 0
}

internal object PassiveMonitoringPolicy {
    fun sanitizeInterval(minutes: Int): Int =
        minutes.takeIf { it in PassiveMonitoringSettings.supportedIntervalsMinutes }
            ?: PassiveMonitoringSettings.DEFAULT_INTERVAL_MINUTES

    fun effectiveHeartRateIntervalMillis(minutes: Int, mode: WearPowerMode): Long? = when (mode) {
        WearPowerMode.NORMAL -> sanitizeInterval(minutes) * MINUTE_MILLIS
        WearPowerMode.SAVER -> maxOf(sanitizeInterval(minutes), SAVER_MINIMUM_MINUTES) * MINUTE_MILLIS
        WearPowerMode.CRITICAL -> null
    }

    private const val MINUTE_MILLIS = 60_000L
    private const val SAVER_MINIMUM_MINUTES = 5
}

internal object PassiveRegistrationPolicy {
    fun shouldBeRegistered(userEnabled: Boolean, mode: WearPowerMode): Boolean =
        userEnabled && mode != WearPowerMode.CRITICAL
}
