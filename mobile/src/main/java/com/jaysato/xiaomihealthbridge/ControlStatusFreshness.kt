package com.jaysato.xiaomihealthbridge

internal object ControlStatusFreshness {
    fun isStale(timestamp: Long, now: Long, freshnessMillis: Long): Boolean =
        timestamp <= 0L || now < timestamp || now - timestamp > freshnessMillis
}
