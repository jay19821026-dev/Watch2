package com.jaysato.xiaomihealthbridge

object SensorSnapshotTimestamp {
    fun sampleTime(lastSampleAt: Long, hasLastSampleAt: Boolean): Long =
        if (hasLastSampleAt) lastSampleAt.coerceAtLeast(0L) else 0L

    fun rejectionReason(
        sampleTime: Long,
        previousSampleTime: Long,
        sessionStartedAt: Long,
        previousSessionStartedAt: Long,
        hasWatchSampleTime: Boolean
    ): String? = if (
        hasWatchSampleTime &&
        sessionStartedAt == previousSessionStartedAt &&
        sampleTime > 0L &&
        previousSampleTime > 0L &&
        sampleTime < previousSampleTime
    ) "older_sample_timestamp" else null
}
