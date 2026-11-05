package com.jaysato.xiaomihealthbridge

internal object PassiveHeartRatePolicy {
    fun shouldSend(timestamp: Long, previousSentTimestamp: Long, intervalMillis: Long): Boolean =
        timestamp > 0L && intervalMillis >= 0L && when {
            previousSentTimestamp <= 0L -> true
            timestamp < previousSentTimestamp -> false
            intervalMillis == 0L -> timestamp > previousSentTimestamp
            else -> timestamp - previousSentTimestamp >= intervalMillis
        }
}
