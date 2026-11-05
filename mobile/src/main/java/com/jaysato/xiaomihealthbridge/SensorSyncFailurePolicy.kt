package com.jaysato.xiaomihealthbridge

internal object SensorSyncFailurePolicy {
    const val RECENT_WINDOW_MILLIS = 30 * 60 * 1000L

    fun isRecentForKind(
        requestedKind: String,
        lastRejectedKind: String?,
        lastRejectionTime: Long,
        now: Long,
        windowMillis: Long = RECENT_WINDOW_MILLIS
    ): Boolean = requestedKind.isNotBlank() && requestedKind == lastRejectedKind &&
        lastRejectionTime > 0L && now >= lastRejectionTime && now - lastRejectionTime <= windowMillis
}
