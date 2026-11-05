package com.jaysato.xiaomihealthbridge

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

class WearInsightsListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED }.forEach { event ->
            val item = event.dataItem
            if (!item.uri.path.orEmpty().startsWith(PATH)) return@forEach
            val data = DataMapItem.fromDataItem(item).dataMap
            val preferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val generatedAt = if (data.containsKey("computed_at")) data.getLong("computed_at") else data.getLong("timestamp")
            if (!WearInsightPacketPolicy.shouldAccept(generatedAt, preferences.getLong("timestamp", 0L))) {
                Wearable.getDataClient(this).deleteDataItems(item.uri)
                return@forEach
            }
            val editor = preferences.edit().putLong("timestamp", generatedAt)
            editor.putLong("source_data_cutoff_at", data.getLong("source_data_cutoff_at", 0L))
            listOf("rhr_measured_at", "hrv_measured_at", "sleep_session_end_at", "sleep_inference_start_at", "sleep_inference_end_at", "temperature_measured_at", "training_window_start_at", "training_window_end_at", "oxygen_window_start_at", "oxygen_window_end_at", "daily_energy_window_start_at", "daily_energy_window_end_at", "activity_load_window_start_at", "activity_load_window_end_at").forEach { key ->
                editor.putLong(key, WearInsightPacketPolicy.sourceTimestamp(data.containsKey(key), data.getLong(key, 0L)))
            }
            editor.putBoolean("sleep_inferred", data.getBoolean("sleep_inferred", false))
            editor.putBoolean("sleep_stages_complete", data.getBoolean("sleep_stages_complete", false))
            editor.putBoolean("activity_load_included", data.getBoolean("activity_load_included", false))
            listOf("readiness", "body_energy", "stress", "recovery").forEach { key ->
                editor.putInt(key, WearInsightPacketPolicy.scoreOrUnavailable(data.containsKey(key), data.getInt(key, -1)))
            }
            if (data.containsKey("training_load")) editor.putInt("training_load", data.getInt("training_load"))
            else editor.remove("training_load")
            if (data.containsKey("sleep_hours")) editor.putFloat("sleep_hours", data.getDouble("sleep_hours").toFloat())
            else editor.remove("sleep_hours")
            if (data.containsKey("sleep_session_hours")) editor.putFloat("sleep_session_hours", data.getDouble("sleep_session_hours").toFloat())
            else editor.remove("sleep_session_hours")
            if (data.containsKey("temperature_deviation")) editor.putFloat("temperature_deviation", data.getDouble("temperature_deviation").toFloat())
            else editor.remove("temperature_deviation")
            editor.putString("confidence", data.getString("confidence")).apply()
            Wearable.getDataClient(this).deleteDataItems(item.uri)
        }
    }

    companion object {
        const val PATH = "/health-insights/"
        const val PREFS = "phone_health_insights"
    }
}

internal object WearInsightPacketPolicy {
    private const val COMPOSITE_SCORES_ENABLED = false

    fun shouldAccept(incomingGeneratedAt: Long, storedGeneratedAt: Long): Boolean =
        incomingGeneratedAt > 0L && incomingGeneratedAt > storedGeneratedAt

    fun sourceTimestamp(fieldPresent: Boolean, incomingValue: Long): Long =
        incomingValue.takeIf { fieldPresent && it > 0L } ?: 0L

    fun scoreOrUnavailable(fieldPresent: Boolean, incomingValue: Int): Int =
        incomingValue.takeIf { fieldPresent && it in 0..100 } ?: -1

    fun compositeScoreForDisplay(storedValue: Int): Int? =
        storedValue.takeIf { COMPOSITE_SCORES_ENABLED && it in 0..100 }
}
