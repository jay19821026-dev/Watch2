package com.jaysato.xiaomihealthbridge

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WatchInsightsPublisher {
    fun publish(context: Context, insights: AlgorithmicInsightsEngine.Insights) {
        val timestamp = insights.generatedAt?.toEpochMilli() ?: System.currentTimeMillis()
        val request = PutDataMapRequest.create("/health-insights/$timestamp").apply {
            dataMap.putLong("timestamp", timestamp)
            dataMap.putLong("computed_at", timestamp)
            dataMap.putLong("source_data_cutoff_at", insights.sourceDataCutoffAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("rhr_measured_at", insights.restingHeartRateAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("hrv_measured_at", insights.hrvAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("sleep_session_end_at", insights.sleepSessionEndAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("sleep_inference_start_at", insights.sleepInferenceStartAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("sleep_inference_end_at", insights.sleepInferenceEndAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("temperature_measured_at", insights.wristTemperatureMeasuredAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("training_window_start_at", insights.trainingWindowStartAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("training_window_end_at", insights.trainingWindowEndAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("oxygen_window_start_at", insights.oxygenWindowStartAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("oxygen_window_end_at", insights.oxygenWindowEndAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("daily_energy_window_start_at", insights.dailyEnergyWindowStartAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("daily_energy_window_end_at", insights.dailyEnergyWindowEndAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("activity_load_window_start_at", insights.activityLoadWindowStartAt?.toEpochMilli() ?: 0L)
            dataMap.putLong("activity_load_window_end_at", insights.activityLoadWindowEndAt?.toEpochMilli() ?: 0L)
            dataMap.putBoolean("sleep_inferred", insights.sleepInferred)
            dataMap.putBoolean("sleep_stages_complete", insights.sleepStagesComplete)
            dataMap.putBoolean("activity_load_included", insights.activityLoadIncluded)
            dataMap.putInt("readiness", insights.readiness ?: -1)
            dataMap.putInt("body_energy", insights.bodyEnergy ?: -1)
            dataMap.putInt("stress", insights.stress ?: -1)
            dataMap.putInt("recovery", insights.recovery ?: -1)
            dataMap.putString("composite_model_version", ResearchCompositeModel.VERSION)
            dataMap.putString("composite_origin", "MODEL_ESTIMATE_RESEARCH")
            insights.trainingLoad?.let { dataMap.putInt("training_load", it) }
            dataMap.putDouble("sleep_hours", insights.sleepHours ?: Double.NaN)
            dataMap.putDouble("sleep_session_hours", insights.sleepSessionHours ?: Double.NaN)
            dataMap.putDouble("temperature_deviation", insights.temperatureDeviation ?: Double.NaN)
            dataMap.putString("confidence", insights.confidence)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }
}
