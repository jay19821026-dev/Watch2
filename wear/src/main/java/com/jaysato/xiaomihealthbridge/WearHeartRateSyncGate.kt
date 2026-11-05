package com.jaysato.xiaomihealthbridge

internal class WearHeartRateSyncGate(initialTimestamp: Long = 0L) {
    private var lastPublishedTimestamp = initialTimestamp

    @Synchronized
    fun claim(timestamp: Long): Boolean {
        if (!WearDataSyncPolicy.shouldPublishHeartRate(lastPublishedTimestamp, timestamp)) return false
        lastPublishedTimestamp = timestamp
        return true
    }
}
