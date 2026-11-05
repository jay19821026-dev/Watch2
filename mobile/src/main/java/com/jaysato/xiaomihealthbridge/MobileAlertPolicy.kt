package com.jaysato.xiaomihealthbridge

internal object MobileAlertPolicy {
    const val ROUTINE_QUIET_PERIOD_MILLIS = 30 * 60 * 1000L
    private const val FUTURE_TOLERANCE_MILLIS = 2 * 60 * 1000L
    private const val FALL_EVENT_MAX_AGE_MILLIS = 30 * 60 * 1000L
    private const val OTHER_EVENT_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L

    fun shouldProcessEvent(type: String, timestamp: Long, previousTimestamp: Long, now: Long): Boolean {
        val maximumAge = if (type.startsWith("fall_")) FALL_EVENT_MAX_AGE_MILLIS else OTHER_EVENT_MAX_AGE_MILLIS
        return timestamp > previousTimestamp && timestamp > 0L &&
            timestamp <= now + FUTURE_TOLERANCE_MILLIS && now - timestamp <= maximumAge
    }

    fun isCritical(type: String): Boolean = type == "fall_detected" || type == "fall_sos_requested"

    fun bypassesRoutineThrottle(type: String): Boolean = isCritical(type)

    fun shouldSuppressRoutine(previousNotificationTime: Long, now: Long): Boolean =
        previousNotificationTime > 0L && now >= previousNotificationTime &&
            now - previousNotificationTime < ROUTINE_QUIET_PERIOD_MILLIS

    fun shouldSendRoutine(enabled: Boolean, previousNotificationTime: Long, now: Long): Boolean =
        enabled && !shouldSuppressRoutine(previousNotificationTime, now)

}
