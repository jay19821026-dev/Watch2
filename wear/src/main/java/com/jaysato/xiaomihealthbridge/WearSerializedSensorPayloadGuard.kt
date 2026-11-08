package com.jaysato.xiaomihealthbridge

import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.jaysato.xiaomihealthbridge.engine.SerializedSensorPayloadPolicy

internal object WearSerializedSensorPayloadGuard {
    fun compactOversizedSnapshot(request: PutDataMapRequest): Int {
        val map = request.dataMap
        val serializedBytes = map.toByteArray().size
        if (SerializedSensorPayloadPolicy.rejectionReason(serializedBytes) == null) return serializedBytes

        val kind = map.getString("kind").orEmpty().take(64)
        val label = map.getString("label").orEmpty().take(96)
        val sensor = map.getString("sensor")?.take(96)
        val vendor = map.getString("vendor")?.take(96)
        val reportingMode = map.getString("reporting_mode")?.take(64)
        val registrationResult = map.getString("registration_result")?.take(96)
        val stopReason = map.getString("acquisition_stop_reason")?.take(96)
        val timestampSource = map.getString("last_sample_timestamp_source")?.take(64)
        val waveformEncoding = map.getString("waveform_encoding")?.take(96)
        val evidenceFlags = map.getString("evidence_flags")?.take(256)
        val firstSampleAt = map.getLong("first_sample_at_ms", 0L)
        val lastSampleAt = map.getLong("last_sample_at_ms", 0L)
        val lastCallbackReceivedAt = map.getLong("last_callback_received_at_ms", 0L)
        val sessionStartedAt = map.getLong("session_started_at_ms", 0L)
        val sessionElapsedNs = map.getLong("session_started_elapsed_realtime_ns", 0L)
        val suiteId = map.getString("suite_id")?.take(96)
        val sequence = map.getLong("producer_sequence", 0L)
        val timestamp = map.getLong("timestamp", 0L)
        val enqueueRequestedAt = map.getLong("producer_enqueue_requested_at_ms", timestamp)
        val motionStatus = map.getString("motion_capture_status")?.take(64)
        val motionEventCount = map.getInt("motion_event_count", 0).coerceIn(0, 512)
        val motionSameSession = map.getBoolean("motion_same_session", false)
        val motionSameClock = map.getBoolean("motion_same_monotonic_clock", false)
        val motionMaxPairSkew = map.getLong("motion_maximum_pair_skew_ns", 0L).coerceAtLeast(0L)
        val final = map.getBoolean("final", false)
        val countFields = listOf("samples", "raw_value_count", "preview_point_count", "event_count", "count_contract_version")
            .mapNotNull { key -> if (map.containsKey(key)) key to map.getInt(key) else null }
        val eventTimestamps = map.getLongArray("event_timestamps_ns")
        val eventTimestampValidity = map.getLongArray("event_timestamp_validity")
        val eventCallbackTimes = map.getLongArray("event_callback_elapsed_realtime_ns")
        val eventSampleCounts = map.getLongArray("event_sample_counts")
        val eventRawValues = map.getString("event_raw_values_rows")
        val eventRawValuesStatus = map.getString("event_raw_values_status")
        val rawEventMetadataCanBePreserved = final &&
            eventTimestamps != null && eventSampleCounts != null &&
            eventTimestamps.size == eventSampleCounts.size && eventTimestamps.size <= MAX_EVENT_ROWS &&
            (eventTimestampValidity == null || eventTimestampValidity.size == eventTimestamps.size) &&
            (eventCallbackTimes == null || eventCallbackTimes.size == eventTimestamps.size) &&
            eventRawValues != null && eventRawValues.length <= MAX_EVENT_RAW_VALUE_CHARS &&
            !eventRawValuesStatus.isNullOrBlank() &&
            (eventRawValuesStatus !in COMPLETE_RAW_STATUSES ||
                (eventRawValues.count { it == ';' } + (if (eventRawValues.isEmpty()) 0 else 1)) == eventTimestamps.size)

        fun writeCompactSummary(rejected: Boolean) {
            map.clear()
            map.putString("kind", kind)
            map.putString("label", label)
            map.putString("status", if (rejected) "payload_rejected" else "payload_compacted")
            map.putString(
                "reason",
                if (rejected) "Serialized sensor payload exceeds the supported transport limit"
                else "Preview data omitted to preserve raw event evidence"
            )
            if (rejected) map.putString("payload_rejection_reason", SerializedSensorPayloadPolicy.OVERSIZED_REASON)
            else map.putString("preview_payload_compaction_reason", "oversized_preview_raw_metadata_preserved")
            map.putInt("payload_serialized_bytes", serializedBytes)
            sensor?.let { map.putString("sensor", it) }
            vendor?.let { map.putString("vendor", it) }
            reportingMode?.let { map.putString("reporting_mode", it) }
            registrationResult?.let { map.putString("registration_result", it) }
            stopReason?.let { map.putString("acquisition_stop_reason", it) }
            timestampSource?.let { map.putString("last_sample_timestamp_source", it) }
            waveformEncoding?.let { map.putString("waveform_encoding", it) }
            evidenceFlags?.let { map.putString("evidence_flags", it) }
            map.putLong("first_sample_at_ms", firstSampleAt)
            map.putLong("last_sample_at_ms", lastSampleAt)
            map.putLong("last_callback_received_at_ms", lastCallbackReceivedAt)
            map.putLong("session_started_at_ms", sessionStartedAt)
            map.putLong("session_started_elapsed_realtime_ns", sessionElapsedNs)
            suiteId?.let { map.putString("suite_id", it) }
            map.putLong("producer_sequence", sequence)
            map.putLong("timestamp", timestamp)
            map.putLong("producer_enqueue_requested_at_ms", enqueueRequestedAt)
            map.putString("payload_timestamp_source", "wear_wall_clock_publish_request")
            motionStatus?.let { map.putString("motion_capture_status", it) }
            map.putInt("motion_event_count", motionEventCount)
            map.putBoolean("motion_same_session", motionSameSession)
            map.putBoolean("motion_same_monotonic_clock", motionSameClock)
            map.putLong("motion_maximum_pair_skew_ns", motionMaxPairSkew)
            map.putBoolean("final", final)
            countFields.forEach { (key, value) -> map.putInt(key, value) }
        }

        writeCompactSummary(rejected = false)
        if (rawEventMetadataCanBePreserved) {
            map.putLongArray("event_timestamps_ns", eventTimestamps!!)
            eventTimestampValidity?.let { map.putLongArray("event_timestamp_validity", it) }
            eventCallbackTimes?.let { map.putLongArray("event_callback_elapsed_realtime_ns", it) }
            map.putLongArray("event_sample_counts", eventSampleCounts!!)
            map.putString("event_raw_values_rows", eventRawValues!!)
            map.putString("event_raw_values_status", eventRawValuesStatus!!)
            val compactedBytes = map.toByteArray().size
            if (SerializedSensorPayloadPolicy.rejectionReason(compactedBytes) == null) {
                map.putInt("payload_serialized_bytes", compactedBytes)
                Log.w("WearRawSensorPublisher", "sensor_snapshot_preview_compacted_raw_preserved kind=$kind original_bytes=$serializedBytes compacted_bytes=$compactedBytes")
                return compactedBytes
            }
        }

        writeCompactSummary(rejected = true)
        Log.w("WearRawSensorPublisher", "sensor_snapshot_rejected_oversize kind=$kind bytes=$serializedBytes")
        return serializedBytes
    }

    private const val MAX_EVENT_ROWS = 512
    private const val MAX_EVENT_RAW_VALUE_CHARS = 32_768
    private val COMPLETE_RAW_STATUSES = setOf("complete_for_observed_events", "bounded_recent_events")
}
