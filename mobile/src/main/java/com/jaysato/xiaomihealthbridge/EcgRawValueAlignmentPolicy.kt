package com.jaysato.xiaomihealthbridge

internal object EcgRawValueAlignmentPolicy {
    data class Result(
        val valuesRetained: Boolean,
        val rowsIndexAligned: Boolean,
        val sensorTimestampsValidMonotonic: Boolean,
        val valuesTimeAligned: Boolean
    )

    fun evaluate(
        sourceEncoding: String,
        rawValuesStatus: String,
        rawRowCount: Int,
        eventTimestampsNs: List<Long>
    ): Result {
        val rawSource = sourceEncoding == "hal_event_values_full_unmapped_preview"
        val retainedStatus = rawValuesStatus in setOf("complete_for_observed_events", "bounded_recent_events")
        val valuesRetained = rawSource && retainedStatus && rawRowCount > 0
        val timestampsValid = EcgEventRowExportPolicy.areSensorTimestampsValidMonotonic(eventTimestampsNs)
        val rowsIndexAligned = valuesRetained && eventTimestampsNs.isNotEmpty() &&
            rawRowCount == eventTimestampsNs.size
        return Result(
            valuesRetained = valuesRetained,
            rowsIndexAligned = rowsIndexAligned,
            sensorTimestampsValidMonotonic = timestampsValid,
            valuesTimeAligned = rowsIndexAligned && timestampsValid
        )
    }
}
