package com.jaysato.xiaomihealthbridge

object FeatureSnapshotOrdering {
    private const val MAX_FUTURE_SKEW_MILLIS = 5 * 60 * 1000L
    private const val WEAR_STATE_FRESHNESS_MILLIS = 5 * 60 * 1000L

    fun isFreshWearState(
        wearKnown: Boolean,
        hasWearValue: Boolean,
        measuredAt: Long,
        timestampSource: String?,
        now: Long
    ): Boolean = wearKnown && hasWearValue && HealthAnalysisFreshnessPolicy.isFreshSensor(
        measuredAt,
        timestampSource,
        now,
        WEAR_STATE_FRESHNESS_MILLIS
    )

    fun rejectionReason(incomingTimestamp: Long, previousTimestamp: Long, now: Long): String? = when {
        incomingTimestamp <= 0L -> "invalid_timestamp"
        incomingTimestamp == previousTimestamp -> "same_timestamp"
        incomingTimestamp < previousTimestamp -> "older_timestamp"
        incomingTimestamp > now + MAX_FUTURE_SKEW_MILLIS -> "future_timestamp"
        else -> null
    }

    fun shouldAccept(incomingTimestamp: Long, previousTimestamp: Long, now: Long): Boolean =
        rejectionReason(incomingTimestamp, previousTimestamp, now) == null

    fun currentWearState(wearKnown: Boolean, worn: Boolean): Boolean? =
        worn.takeIf { wearKnown }

    fun shouldAccept(
        incomingTimestamp: Long,
        previousFeatureTimestamp: Long,
        previousSensorTimestamp: Long,
        now: Long
    ): Boolean = shouldAccept(
        incomingTimestamp,
        maxOf(previousFeatureTimestamp, previousSensorTimestamp),
        now
    )

    fun canonicalHeartRateValue(
        incomingTimestamp: Long,
        previousFeatureTimestamp: Long,
        previousSensorTimestamp: Long,
        incomingBpm: Double,
        previousFeatureBpm: Double,
        now: Long
    ): Double? {
        if (!incomingBpm.isFinite() || incomingBpm !in 30.0..220.0 ||
            incomingTimestamp <= 0L || incomingTimestamp < maxOf(previousFeatureTimestamp, previousSensorTimestamp) ||
            incomingTimestamp > now + MAX_FUTURE_SKEW_MILLIS
        ) return null
        return if (incomingTimestamp == previousFeatureTimestamp &&
            previousFeatureBpm.isFinite() && previousFeatureBpm in 30.0..220.0
        ) previousFeatureBpm else incomingBpm
    }

    fun canonicalHealthServicesHeartRateValue(
        incomingTimestamp: Long,
        previousFeatureTimestamp: Long,
        previousSensorTimestamp: Long,
        incomingBpm: Double,
        previousFeatureBpm: Double,
        previousSensorBpm: Double,
        now: Long
    ): Double? {
        val canonicalBpm = canonicalHeartRateValue(
            incomingTimestamp,
            previousFeatureTimestamp,
            previousSensorTimestamp,
            incomingBpm,
            previousFeatureBpm,
            now
        ) ?: return null
        if (incomingTimestamp == previousSensorTimestamp &&
            previousSensorBpm.isFinite() && previousSensorBpm in 30.0..220.0
        ) return previousSensorBpm
        return canonicalBpm
    }
}
