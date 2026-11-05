package com.jaysato.xiaomihealthbridge

internal object EcgEventRowExportPolicy {
    const val MAX_EVENT_ROWS = 512
    const val MAX_CANDIDATE_POINTS = 5_120
    const val MAX_METADATA_CHARS = 32_768
    private const val MAX_CANDIDATE_POINTS_PER_EVENT = 10

    data class EventRow(
        val eventIndex: Int,
        val eventTimestampNs: Long,
        val callbackReceivedElapsedRealtimeNs: Long?,
        val candidateValues: List<Double>,
        val rawEventValuesCsv: String,
        val legacyRemainderCsv: String
    )

    data class Result(
        val status: String,
        val rows: List<EventRow>,
        val rowIndexAligned: Boolean = false,
        val sensorTimestampsValidMonotonic: Boolean = false
    )

    fun areSensorTimestampsValidMonotonic(timestampsNs: List<Long>): Boolean =
        timestampsNs.isNotEmpty() && timestampsNs.all { it > 0L } &&
            timestampsNs.zipWithNext().all { (previous, current) -> current > previous }

    fun build(
        eventTimestampsNs: List<Long>,
        callbackTimesNs: List<Long>,
        sampleCounts: List<Int>,
        metadataRows: List<String>,
        candidateValues: List<Double>,
        sourceEncoding: String,
        rawValuesStatus: String = "legacy_app_split_unverified",
        parseFailed: Boolean = false
    ): Result {
        if (parseFailed) return Result("rejected_parse_failure", emptyList())
        if (rawValuesStatus == "rejected_size_limit") return Result("rejected_size_limit", emptyList())
        if (eventTimestampsNs.isEmpty() && sampleCounts.isEmpty() && metadataRows.isEmpty()) {
            return Result("no_event_rows", emptyList())
        }
        if (eventTimestampsNs.size > MAX_EVENT_ROWS || sampleCounts.size > MAX_EVENT_ROWS ||
            metadataRows.size > MAX_EVENT_ROWS || callbackTimesNs.size > MAX_EVENT_ROWS ||
            candidateValues.size > MAX_CANDIDATE_POINTS ||
            metadataRows.sumOf { it.length.toLong() } + (metadataRows.size - 1).coerceAtLeast(0) > MAX_METADATA_CHARS
        ) return Result("rejected_size_limit", emptyList())
        val eventCount = eventTimestampsNs.size
        if (eventCount == 0 || sampleCounts.size != eventCount || metadataRows.size != eventCount ||
            (callbackTimesNs.isNotEmpty() && callbackTimesNs.size != eventCount)
        ) return Result("rejected_row_alignment", emptyList())
        if (sampleCounts.any { it !in 0..MAX_CANDIDATE_POINTS_PER_EVENT } ||
            sampleCounts.sumOf { it.toLong() } != candidateValues.size.toLong() || candidateValues.any { !it.isFinite() }
        ) return Result("rejected_candidate_count_mismatch", emptyList())
        val fullRawEventValues = sourceEncoding == "hal_event_values_full_unmapped_preview"
        if (fullRawEventValues && rawValuesStatus !in setOf("complete_for_observed_events", "bounded_recent_events")) {
            return Result("rejected_incomplete_raw_event_values", emptyList())
        }
        if (fullRawEventValues && (candidateValues.isNotEmpty() || sampleCounts.any { it != 0 })) {
            return Result("rejected_unexpected_decoded_ecg_samples", emptyList())
        }

        var valueOffset = 0
        val rows = eventTimestampsNs.indices.map { index ->
            val count = sampleCounts[index]
            val values = candidateValues.subList(valueOffset, valueOffset + count).toList()
            valueOffset += count
            EventRow(
                eventIndex = index,
                eventTimestampNs = eventTimestampsNs[index],
                callbackReceivedElapsedRealtimeNs = callbackTimesNs.getOrNull(index),
                candidateValues = if (fullRawEventValues) emptyList() else values,
                rawEventValuesCsv = if (fullRawEventValues) metadataRows[index] else "",
                legacyRemainderCsv = if (fullRawEventValues) "" else metadataRows[index]
            )
        }
        val status = when {
            !fullRawEventValues -> "aligned_legacy_split_unmapped_values"
            rawValuesStatus == "bounded_recent_events" -> "aligned_bounded_recent_raw_event_values"
            else -> "aligned_full_raw_event_values_unmapped"
        }
        val timestampsValidMonotonic = areSensorTimestampsValidMonotonic(eventTimestampsNs)
        val finalStatus = if (timestampsValidMonotonic) status else "${status}_invalid_sensor_timestamps"
        return Result(finalStatus, rows, rowIndexAligned = true, sensorTimestampsValidMonotonic = timestampsValidMonotonic)
    }
}
