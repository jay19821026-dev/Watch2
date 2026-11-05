package com.jaysato.xiaomihealthbridge

internal object SensorFreshnessSummaryPolicy {
    fun requiresAttention(
        freshCount: Int,
        staleCount: Int,
        sessionStartedAt: Long,
        timestampUnknownCount: Int = 0,
        futureTimestampCount: Int = 0
    ): Boolean = freshCount <= 0 || staleCount > 0 || timestampUnknownCount > 0 ||
        futureTimestampCount > 0 || sessionStartedAt <= 0L
}
