package com.jaysato.xiaomihealthbridge

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WearMetricSender {
    fun send(context: Context, metric: String, value: Double, start: Long, end: Long) {
        if (!value.isFinite() || value <= 0.0 || end <= start) return
        val request = PutDataMapRequest.create("/health/$metric/$end").apply {
            dataMap.putString("metric", metric)
            dataMap.putDouble("value", value)
            dataMap.putLong("start", start)
            dataMap.putLong("end", end)
        }.asPutDataRequest().let { if (SmartSyncPolicy.shouldUseUrgent(context)) it.setUrgent() else it }
        Wearable.getDataClient(context).putDataItem(request)
            .addOnSuccessListener { WearHealthStore.markSync(context, true, "$metric 已同步") }
            .addOnFailureListener { WearHealthStore.markSync(context, false, "$metric 等待重試") }
    }
}
