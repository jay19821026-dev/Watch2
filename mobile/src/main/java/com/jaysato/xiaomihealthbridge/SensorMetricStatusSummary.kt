package com.jaysato.xiaomihealthbridge

import org.json.JSONObject

internal object SensorMetricStatusSummary {
    fun count(states: Iterable<SensorDisplayState>): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        states.forEach { state -> counts[state.exportValue] = (counts[state.exportValue] ?: 0) + 1 }
        return counts
    }

    fun toJson(states: Iterable<SensorDisplayState>): JSONObject {
        val counts = count(states)
        return JSONObject().apply {
            put("fresh_hardware", counts[SensorDisplayState.AVAILABLE.exportValue] ?: 0)
            put("stale", counts[SensorDisplayState.STALE.exportValue] ?: 0)
            put("future_timestamp", counts[SensorDisplayState.FUTURE_TIMESTAMP.exportValue] ?: 0)
            put("timestamp_unknown", counts[SensorDisplayState.TIMESTAMP_UNKNOWN.exportValue] ?: 0)
            put("not_measured", counts[SensorDisplayState.NOT_MEASURED.exportValue] ?: 0)
            put("condition_not_met", counts[SensorDisplayState.CONDITION_NOT_MET.exportValue] ?: 0)
            put("quality_rejected", counts[SensorDisplayState.QUALITY_REJECTED.exportValue] ?: 0)
            put("calibration_missing", counts[SensorDisplayState.CALIBRATION_MISSING.exportValue] ?: 0)
            put("raw_or_research", counts[SensorDisplayState.RAW_ONLY.exportValue] ?: 0)
            put("signal_quality_passed_research_only", counts[SensorDisplayState.SIGNAL_VALID_RESEARCH.exportValue] ?: 0)
            put("derived_estimate", counts[SensorDisplayState.DERIVED_ESTIMATE.exportValue] ?: 0)
        }
    }
}
