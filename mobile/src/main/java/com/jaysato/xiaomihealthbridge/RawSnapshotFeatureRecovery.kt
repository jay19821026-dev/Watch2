package com.jaysato.xiaomihealthbridge

data class HeartRateFeatureSnapshot(
    val bpm: Double,
    val timestamp: Long,
    val sessionStartedAtMs: Long = 0L,
    val producerSequence: Long = 0L,
    val timestampSource: String? = null
)

sealed class RawSnapshotFeatureRecoveryOutcome {
    object NotApplicable : RawSnapshotFeatureRecoveryOutcome()
    object RecoveryFailed : RawSnapshotFeatureRecoveryOutcome()
    data class Recovered(val feature: HeartRateFeatureSnapshot) : RawSnapshotFeatureRecoveryOutcome()
}

object RawSnapshotFeatureRecovery {
    fun recover(
        kind: String,
        isFinal: Boolean,
        formulaPulseBpm: Double?,
        averageBpm: Double?,
        timestamp: Long,
        sessionStartedAtMs: Long = 0L,
        producerSequence: Long = 0L,
        timestampSource: String? = null
    ): RawSnapshotFeatureRecoveryOutcome {
        if (kind != "whs_heart_rate") return RawSnapshotFeatureRecoveryOutcome.NotApplicable
        if (!isFinal) return RawSnapshotFeatureRecoveryOutcome.RecoveryFailed
        return fromSnapshot(kind, isFinal, formulaPulseBpm, averageBpm, timestamp, sessionStartedAtMs, producerSequence, timestampSource)
            ?.let(RawSnapshotFeatureRecoveryOutcome::Recovered)
            ?: RawSnapshotFeatureRecoveryOutcome.RecoveryFailed
    }

    fun fromSnapshot(
        kind: String,
        isFinal: Boolean,
        formulaPulseBpm: Double?,
        averageBpm: Double?,
        timestamp: Long,
        sessionStartedAtMs: Long = 0L,
        producerSequence: Long = 0L,
        timestampSource: String? = null
    ): HeartRateFeatureSnapshot? {
        if (!isFinal || kind != "whs_heart_rate") return null
        val bpm = formulaPulseBpm?.takeIf { it.isFinite() && it in 30.0..220.0 }
            ?: averageBpm?.takeIf { it.isFinite() && it in 30.0..220.0 }
            ?: return null
        return HeartRateFeatureSnapshot(bpm, timestamp, sessionStartedAtMs, producerSequence, timestampSource)
    }
}

internal data class HeartRateFeatureCommitPlan(
    val measurement: HeartRateFeatureSnapshot?,
    val attemptId: String
)

internal object HeartRateFeatureCommitPlanner {
    fun shouldPersistReceiptReplay(isStrictlyNewer: Boolean, featurePending: Boolean): Boolean =
        isStrictlyNewer || featurePending

    fun isStrictlyNewer(
        timestamp: Long,
        sessionStartedAtMs: Long,
        producerSequence: Long,
        previousTimestamp: Long,
        previousSessionStartedAtMs: Long,
        previousProducerSequence: Long,
        now: Long
    ): Boolean = rejectionReason(
        timestamp, sessionStartedAtMs, producerSequence,
        previousTimestamp, previousSessionStartedAtMs, previousProducerSequence, now
    ) == null

    fun rejectionReason(
        timestamp: Long,
        sessionStartedAtMs: Long,
        producerSequence: Long,
        previousTimestamp: Long,
        previousSessionStartedAtMs: Long,
        previousProducerSequence: Long,
        now: Long
    ): String? = FeatureSnapshotOrdering.rejectionReason(timestamp, 0L, now) ?: when {
        timestamp < previousTimestamp -> "older_timestamp"
        timestamp > previousTimestamp -> null
        sessionStartedAtMs > 0L && previousSessionStartedAtMs > 0L &&
            sessionStartedAtMs > previousSessionStartedAtMs -> null
        sessionStartedAtMs > 0L && previousSessionStartedAtMs > 0L &&
            sessionStartedAtMs < previousSessionStartedAtMs -> "older_session"
        sessionStartedAtMs > 0L && sessionStartedAtMs == previousSessionStartedAtMs &&
            producerSequence > 0L && previousProducerSequence > 0L && producerSequence > previousProducerSequence -> null
        sessionStartedAtMs > 0L && sessionStartedAtMs == previousSessionStartedAtMs &&
            producerSequence > 0L && previousProducerSequence > 0L && producerSequence < previousProducerSequence -> "older_sequence"
        else -> "same_timestamp_sequence_unavailable"
    }

    fun plan(
        bpm: Double,
        timestamp: Long,
        previousTimestamp: Long,
        now: Long,
        attemptId: String,
        sessionStartedAtMs: Long = 0L,
        producerSequence: Long = 0L,
        previousSessionStartedAtMs: Long = 0L,
        previousProducerSequence: Long = 0L
    ): HeartRateFeatureCommitPlan? {
        if (!bpm.isFinite() || bpm !in 30.0..220.0 || attemptId.isBlank()) return null
        val measurement = if (rejectionReason(
                timestamp, sessionStartedAtMs, producerSequence, previousTimestamp,
                previousSessionStartedAtMs, previousProducerSequence, now
            ) == null
        ) {
            HeartRateFeatureSnapshot(bpm, timestamp, sessionStartedAtMs, producerSequence)
        } else {
            null
        }
        return HeartRateFeatureCommitPlan(measurement, attemptId)
    }
}
