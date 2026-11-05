package com.jaysato.xiaomihealthbridge

import android.os.SystemClock
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.Instant

class PassiveHealthService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        if (!PassiveMonitoringSettings.isEnabled(this)) return
        val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
        val heartPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
            .sortedBy { it.getTimeInstant(bootInstant) }
        heartPoints.forEach { point ->
            val timestamp = point.getTimeInstant(bootInstant).toEpochMilli()
            val bpm = point.value.toLong()
            if (bpm in 30..220 && timestamp > 0L &&
                WearHeartRateAlertPolicy.shouldObserve(bpm, timestamp, System.currentTimeMillis())
            ) {
                WearSafetyAlerts.observeHeartRate(this, bpm, timestamp)
            }
        }
        heartPoints.lastOrNull { it.value.toLong() in 30..220 }?.let { latest ->
            val timestamp = latest.getTimeInstant(bootInstant).toEpochMilli()
            if (WearHeartRateAlertPolicy.shouldObserve(latest.value.toLong(), timestamp, System.currentTimeMillis())) {
                cachePendingHeartRate(latest.value.toLong(), timestamp)
                sendPendingHeartRate()
            }
        }
        dataPoints.getData(DataType.STEPS).forEach {
            WearHealthStore.recordMetric(this, "steps", it.value.toDouble(), it.getEndInstant(bootInstant).toEpochMilli())
            send("steps", it.value.toDouble(), it.getStartInstant(bootInstant), it.getEndInstant(bootInstant))
        }
        dataPoints.getData(DataType.DISTANCE).forEach {
            send("distance", it.value, it.getStartInstant(bootInstant), it.getEndInstant(bootInstant))
        }
        dataPoints.getData(DataType.CALORIES).forEach {
            send("calories", it.value, it.getStartInstant(bootInstant), it.getEndInstant(bootInstant))
        }
    }

    override fun onPermissionLost() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_ERROR, "被動健康監測權限已失效").apply()
        PassiveRegistration.recordPermissionLost(this)
    }

    private fun send(metric: String, value: Double, start: Instant, end: Instant) {
        if (value <= 0.0 || end <= start) return
        val path = "/health/$metric/${end.toEpochMilli()}"
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putString("metric", metric)
            dataMap.putDouble("value", value)
            dataMap.putLong("start", start.toEpochMilli())
            dataMap.putLong("end", end.toEpochMilli())
        }.asPutDataRequest()
        Wearable.getDataClient(this).putDataItem(request)
            .addOnSuccessListener { WearHealthStore.markSync(this, true, "$metric 已同步") }
            .addOnFailureListener { WearHealthStore.markSync(this, false, "$metric 同步失敗") }
    }

    private fun cachePendingHeartRate(beatsPerMinute: Long, timestamp: Long) {
        synchronized(HEART_RATE_SEND_LOCK) {
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val pendingTimestamp = prefs.getLong(KEY_PENDING_HEART_RATE_TIMESTAMP, 0L)
            val lastAcknowledgedTimestamp = prefs.getLong(KEY_LAST_HEART_RATE_SENT, 0L)
            if (timestamp > lastAcknowledgedTimestamp && timestamp > pendingTimestamp) {
                prefs.edit()
                    .putLong(KEY_PENDING_HEART_RATE_BPM, beatsPerMinute)
                    .putLong(KEY_PENDING_HEART_RATE_TIMESTAMP, timestamp)
                    .apply()
            }
        }
    }

    private fun sendPendingHeartRate() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_PENDING_HEART_RATE_TIMESTAMP, 0L)
        val beatsPerMinute = prefs.getLong(KEY_PENDING_HEART_RATE_BPM, 0L)
        val payload = HealthServicesHeartRatePayload.create(beatsPerMinute, timestamp)
            ?: return
        if (beatsPerMinute !in 1..300) return
        val interval = PassiveMonitoringPolicy.effectiveHeartRateIntervalMillis(
            PassiveMonitoringSettings.intervalMinutes(this),
            WearPowerScheduler.currentState(this).mode
        ) ?: return
        if (!claimHeartRateSend(timestamp, interval)) return
        WearHealthStore.recordHeartRate(this, beatsPerMinute, timestamp)
        val request = PutDataMapRequest.create("/heart-rate/${payload.eventTimestampMillis}").apply {
            dataMap.putLong("bpm", payload.bpm)
            dataMap.putLong("timestamp", payload.eventTimestampMillis)
            dataMap.putString("timestamp_source", payload.timestampSource)
        }.asPutDataRequest()
        Wearable.getDataClient(this).putDataItem(request)
            .addOnSuccessListener {
                synchronized(HEART_RATE_SEND_LOCK) {
                    val currentPrefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                    val update = currentPrefs.edit().putLong(KEY_LAST_HEART_RATE_SENT, timestamp)
                    if (currentPrefs.getLong(KEY_PENDING_HEART_RATE_TIMESTAMP, 0L) == timestamp) {
                        update.remove(KEY_PENDING_HEART_RATE_BPM).remove(KEY_PENDING_HEART_RATE_TIMESTAMP)
                    }
                    update.apply()
                    heartRateSendInFlight = false
                }
                WearHealthStore.markSync(this, true, "心率已同步")
            }
            .addOnFailureListener {
                synchronized(HEART_RATE_SEND_LOCK) { heartRateSendInFlight = false }
                WearHealthStore.markSync(this, false, "心率等待重試")
            }
    }

    private fun claimHeartRateSend(timestamp: Long, intervalMillis: Long): Boolean =
        synchronized(HEART_RATE_SEND_LOCK) {
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val previous = prefs.getLong(KEY_LAST_HEART_RATE_SENT, 0L)
            if (heartRateSendInFlight || !PassiveHeartRatePolicy.shouldSend(timestamp, previous, intervalMillis)) {
                false
            } else {
                heartRateSendInFlight = true
                true
            }
        }

    companion object {
        const val PREFS = "passive_status"
        const val KEY_ERROR = "error"
        private const val KEY_LAST_HEART_RATE_SENT = "last_heart_rate_sent_timestamp"
        private const val KEY_PENDING_HEART_RATE_BPM = "pending_heart_rate_bpm"
        private const val KEY_PENDING_HEART_RATE_TIMESTAMP = "pending_heart_rate_timestamp"
        private val HEART_RATE_SEND_LOCK = Any()
        private var heartRateSendInFlight = false
    }
}
