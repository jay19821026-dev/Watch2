package com.jaysato.xiaomihealthbridge

internal object WearHeartRateAlertPolicy {
    const val MAX_CONTIGUOUS_SAMPLE_GAP_MILLIS = 2 * 60_000L
    const val SUSTAINED_MILLIS = 10 * 60_000L
    const val MIN_SAMPLES = 3
    const val MAX_OBSERVATION_AGE_MILLIS = 2 * 60_000L
    const val MAX_FUTURE_SKEW_MILLIS = 30_000L

    fun shouldObserve(bpm: Long, timestamp: Long, now: Long): Boolean =
        bpm in 30..220 && timestamp > 0L && now - timestamp in
            -MAX_FUTURE_SKEW_MILLIS..MAX_OBSERVATION_AGE_MILLIS

    data class Progress(
        val kind: String,
        val firstTimestamp: Long,
        val sampleCount: Int,
        val lastSampleTimestamp: Long
    )

    fun advance(
        kind: String,
        timestamp: Long,
        previousObservedTimestamp: Long,
        previousKind: String,
        previousFirstTimestamp: Long,
        previousSampleCount: Int,
        previousSampleTimestamp: Long
    ): Progress? {
        if (timestamp <= previousObservedTimestamp) return null
        if (kind.isEmpty()) return Progress("", 0L, 0, 0L)
        val continuous = kind == previousKind &&
            hasContinuousSampleGap(previousSampleTimestamp, timestamp)
        return Progress(
            kind,
            if (continuous) previousFirstTimestamp else timestamp,
            if (continuous) previousSampleCount + 1 else 1,
            timestamp
        )
    }

    fun isSustained(progress: Progress, lastAlertTimestamp: Long): Boolean =
        progress.kind.isNotEmpty() && progress.sampleCount >= MIN_SAMPLES &&
            progress.lastSampleTimestamp - progress.firstTimestamp >= SUSTAINED_MILLIS &&
            progress.lastSampleTimestamp - lastAlertTimestamp >= WearMainActivity.ALERT_COOLDOWN_MILLIS

    fun hasContinuousSampleGap(previousTimestamp: Long, currentTimestamp: Long): Boolean =
        previousTimestamp > 0L && currentTimestamp > previousTimestamp &&
            currentTimestamp - previousTimestamp <= MAX_CONTIGUOUS_SAMPLE_GAP_MILLIS
}
