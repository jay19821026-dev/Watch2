package com.jaysato.xiaomihealthbridge

internal object WearDataSyncPolicy {
    const val PREVIEW_INTERVAL_MILLIS = 5_000L
    const val URGENT_INTERVAL_MILLIS = 5_000L
    const val WHS_HEART_RATE_SYNC_INTERVAL_MILLIS = 30_000L

    fun shouldSendPreview(lastSentAt: Long, now: Long): Boolean =
        now >= lastSentAt && (lastSentAt == 0L || now - lastSentAt >= PREVIEW_INTERVAL_MILLIS)

    fun shouldMarkUrgent(isFinal: Boolean, lastUrgentAt: Long, now: Long): Boolean =
        isFinal && now >= lastUrgentAt &&
            (lastUrgentAt == 0L || now - lastUrgentAt >= URGENT_INTERVAL_MILLIS)

    fun shouldMarkStandaloneSnapshotUrgent(isFinal: Boolean): Boolean = isFinal

    fun shouldPublishHeartRate(lastPublishedAt: Long, now: Long): Boolean =
        now >= lastPublishedAt &&
            (lastPublishedAt == 0L || now - lastPublishedAt >= WHS_HEART_RATE_SYNC_INTERVAL_MILLIS)

    fun shouldSaveWorkoutHeartRate(
        lastSavedMeasurementAt: Long,
        incomingMeasurementAt: Long,
        lastSavedElapsedAt: Long,
        nowElapsed: Long,
        force: Boolean = false
    ): Boolean = incomingMeasurementAt > lastSavedMeasurementAt &&
        (force || shouldPublishHeartRate(lastSavedElapsedAt, nowElapsed))

    fun isWorkoutHeartRateSampleEligible(
        workoutActive: Boolean,
        workoutStartTimestamp: Long,
        lastAcceptedMeasurementTimestamp: Long,
        incomingMeasurementTimestamp: Long
    ): Boolean = workoutActive && incomingMeasurementTimestamp >= workoutStartTimestamp &&
        incomingMeasurementTimestamp > lastAcceptedMeasurementTimestamp

    fun shouldMarkStatusUrgent(priority: StatusSyncPriority): Boolean =
        priority != StatusSyncPriority.PROGRESS
}

internal enum class StatusSyncPriority {
    PROGRESS,
    USER_ACTION,
    TERMINAL
}
