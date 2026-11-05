package com.jaysato.xiaomihealthbridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PpgEvidenceArchive {
    suspend fun appendPreview(
        context: Context,
        receiptId: String,
        values: List<Float>,
        sourceEncoding: String?,
        sourceTimestamp: Long,
        timestampSource: String?,
        eventCount: Int?,
        sourceRawValueCount: Int?,
        previewPointCount: Int,
        eventTimestamps: List<Long>,
        eventSampleCounts: List<Int>,
        eventTimestampValidity: List<Int> = emptyList(),
        dao: SignalEvidenceDao? = null,
        kind: String = "ppg",
        publishedAt: Long = 0L,
        reportedRate: Double? = null,
        evidenceFlags: String = "",
        evidenceFlagsVersion: Int = 0,
        callbackTimestamps: List<Long> = emptyList(),
        previewPointEventTimestamps: List<Long> = emptyList(),
        previewPointEventTimestampValidity: List<Int> = emptyList(),
        previewPointCallbackTimestamps: List<Long> = emptyList(),
        motionCaptureStatus: String? = null,
        motionClockDomain: String? = null,
        motionEventTimestamps: List<Long> = emptyList(),
        motionX: List<Float> = emptyList(),
        motionY: List<Float> = emptyList(),
        motionZ: List<Float> = emptyList(),
        ppgPreviewClockDomain: String? = null,
        motionSameSession: Boolean = false,
        motionSameMonotonicClock: Boolean = false,
        motionMaximumPairSkewNanos: Long = 0L,
        rawEventRows: String = "",
        sourceSessionStartedAt: Long = 0L,
        sourceSessionElapsedNanos: Long = 0L,
        reportedEventCount: Int? = eventCount,
        countContractVersion: Int = 0
    ) {
        require(receiptId.isNotBlank() && receiptId.length <= 256)
        require(kind in setOf("ppg", "ecg", "bioz", "body_temperature_raw", "thermometer", "spo2_raw", "mi_spo2"))
        require(evidenceFlags.length <= 2048)
        require(callbackTimestamps.size <= 100_000 && rawEventRows.length <= 512_000)
        require(previewPointEventTimestamps.size <= 100_000 && previewPointCallbackTimestamps.size <= 100_000)
        require(previewPointEventTimestampValidity.isEmpty() || previewPointEventTimestampValidity.size == previewPointEventTimestamps.size)
        require(previewPointEventTimestampValidity.all { it in 0..1 })
        require(motionEventTimestamps.size <= 512 && motionX.size <= 512 && motionY.size <= 512 && motionZ.size <= 512)
        require(motionMaximumPairSkewNanos in 0L..100_000_000L)
        PpgArchiveInputPolicy.validateMetadata(sourceEncoding, timestampSource)
        require((eventCount == null || eventCount >= 0) && previewPointCount >= 0 && (sourceRawValueCount == null || sourceRawValueCount >= 0))
        require(values.size <= 100_000 && eventTimestamps.size <= 100_000 && eventSampleCounts.size <= 100_000)
        require(eventSampleCounts.all { it >= 0 })
        require(eventTimestampValidity.size <= 100_000 && eventTimestampValidity.all { it in 0..1 })
        require(eventTimestampValidity.isEmpty() || eventTimestampValidity.size == eventTimestamps.size)
        val hasRawData = values.isNotEmpty() || rawEventRows.isNotEmpty()
        val normalizedEvidenceFlags = evidenceFlags.ifBlank {
            EvidenceFlags.build(sourceTimestamp, publishedAt, timestampSource, reportedRate, hasRawData)
        }
        val sessionId = "$kind-preview-v4:$receiptId"
        val metadata = JSONObject()
            .put("archive_metadata_version", 5)
            .put("source_session_started_at_ms", sourceSessionStartedAt)
            .put("source_session_started_elapsed_realtime_ns", sourceSessionElapsedNanos)
            .put("representation", "received_preview_not_full_adc_stream")
            .put("source_encoding", sourceEncoding ?: JSONObject.NULL)
            .put("source_reported_timestamp_ms", sourceTimestamp)
            .put("published_at_ms", publishedAt)
            .put("reported_sample_rate_hz", reportedRate?.takeIf { it.isFinite() && it > 0.0 } ?: JSONObject.NULL)
            .put("evidence_flags", normalizedEvidenceFlags)
            .put("evidence_flags_version", if (evidenceFlags.isBlank()) EvidenceFlags.VERSION else evidenceFlagsVersion)
            .put("raw_data_available", hasRawData)
            .put("source_time_available", sourceTimestamp > 0L && HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource))
            .put("publication_time_available", publishedAt > 0L)
            .put("reported_rate_available", reportedRate?.let { it.isFinite() && it > 0.0 } == true)
            .put("timestamp_source", timestampSource ?: "UNKNOWN")
            .put("event_count", eventCount ?: JSONObject.NULL)
            .put("event_count_known", eventCount != null)
            .put("reported_event_count", reportedEventCount ?: JSONObject.NULL)
            .put("count_contract_version", countContractVersion)
            .put("source_raw_value_count", sourceRawValueCount ?: JSONObject.NULL)
            .put("preview_point_count", previewPointCount)
            .put("stored_preview_value_count", values.size)
            .put("channel_mapping", "UNKNOWN")
            .put("sample_clock", "UNKNOWN")
            .put("event_timestamps_ns", JSONArray(eventTimestamps))
            .put("event_timestamp_validity", JSONArray(eventTimestampValidity))
            .put("event_timestamp_validity_semantics", if (eventTimestampValidity.isEmpty()) "not_recorded" else "source_timestamp_positive_only_not_clock_trust")
            .put("event_sample_counts", JSONArray(eventSampleCounts))
            .put("event_callback_elapsed_realtime_ns", JSONArray(callbackTimestamps))
            .put("preview_point_event_timestamps_ns", JSONArray(previewPointEventTimestamps))
            .put("preview_point_event_timestamp_validity", JSONArray(previewPointEventTimestampValidity))
            .put("preview_point_callback_elapsed_realtime_ns", JSONArray(previewPointCallbackTimestamps))
            .put("event_raw_values_rows", rawEventRows)
            .put("preview_event_mapping", when {
                kind == "ppg" && previewPointEventTimestamps.size == values.size &&
                    (previewPointEventTimestampValidity.isEmpty() || previewPointEventTimestampValidity.size == values.size) ->
                    "producer_aligned_one_timestamp_per_preview_point"
                kind in setOf("body_temperature_raw", "thermometer") && eventTimestamps.size == values.size &&
                    eventSampleCounts.size == values.size && eventSampleCounts.all { it == 1 } &&
                    (eventTimestampValidity.isEmpty() || eventTimestampValidity.size == values.size) ->
                    "producer_aligned_one_sample_per_temperature_event"
                else -> "unavailable_no_inferred_alignment"
            })
            .put("preview_event_index_domain", when {
                kind == "ppg" && previewPointEventTimestamps.size == values.size &&
                    (previewPointEventTimestampValidity.isEmpty() || previewPointEventTimestampValidity.size == values.size) ->
                    "ppg_preview_window_local_index"
                kind in setOf("body_temperature_raw", "thermometer") && eventTimestamps.size == values.size &&
                    eventSampleCounts.size == values.size && eventSampleCounts.all { it == 1 } &&
                    (eventTimestampValidity.isEmpty() || eventTimestampValidity.size == values.size) ->
                    "temperature_event_window_local_index"
                else -> JSONObject.NULL
            })
            .put("motion_capture_status", motionCaptureStatus ?: "not_recorded")
            .put("motion_clock_domain", motionClockDomain ?: "UNKNOWN")
            .put("ppg_preview_clock_domain", ppgPreviewClockDomain ?: "UNKNOWN")
            .put("motion_same_session", motionSameSession)
            .put("motion_same_monotonic_clock", motionSameMonotonicClock)
            .put("motion_maximum_pair_skew_ns", motionMaximumPairSkewNanos)
            .put("motion_array_alignment_valid", motionEventTimestamps.size == motionX.size && motionX.size == motionY.size && motionY.size == motionZ.size)
            .put("motion_event_timestamps_ns", JSONArray(motionEventTimestamps))
            .put("motion_x_meters_per_second_squared", JSONArray(motionX.map(Float::toDouble)))
            .put("motion_y_meters_per_second_squared", JSONArray(motionY.map(Float::toDouble)))
            .put("motion_z_meters_per_second_squared", JSONArray(motionZ.map(Float::toDouble)))
            .put("motion_mapping_semantics", "raw_watch_accelerometer_events;separate_from_ppg_preview_sample_clock")
            .put("callback_timestamp_role", "receipt_envelope_only_not_acquisition_clock")
        val metadataJson = metadata.toString()
        require(metadataJson.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "Evidence metadata exceeds supported size" }
        val ppgPointTimes = if (kind == "ppg") PpgArchiveInputPolicy.mapAlignedPpgPreviewTimes(
            values.size, previewPointEventTimestamps, previewPointEventTimestampValidity, previewPointCallbackTimestamps
        ) else if (kind in setOf("body_temperature_raw", "thermometer")) {
            PpgArchiveInputPolicy.mapSingleSamplePerEventTimes(
                values.size, eventTimestamps, eventTimestampValidity, callbackTimestamps, eventSampleCounts
            )
        } else emptyList()
        val rows = values.mapIndexed { index, value ->
            val pointTime = ppgPointTimes.getOrNull(index)
            RawSensorSampleEntity(
                sessionId, index, value.toString(), pointTime?.sourceTimestampNanos,
                pointTime?.callbackTimestampNanos, pointTime?.timestampSource ?: "UNKNOWN",
                pointTime?.eventIndex ?: -1, -1
            )
        }
        val finite = values.filter(Float::isFinite).map(Float::toDouble)
        val mean = finite.takeIf { it.isNotEmpty() }?.average()
        val standardDeviation = mean?.let { average ->
            kotlin.math.sqrt(finite.sumOf { (it - average) * (it - average) } / finite.size).takeIf(Double::isFinite)
        }
        val statistics = JSONObject()
            .put("preview_count", values.size)
            .put("finite_count", finite.size)
            .put("finite_ratio", if (values.isEmpty()) 0.0 else finite.size.toDouble() / values.size)
            .put("mean_raw_units", finite.takeIf { it.isNotEmpty() }?.average() ?: JSONObject.NULL)
            .put("standard_deviation_raw_units", standardDeviation ?: JSONObject.NULL)
            .put("minimum_raw_units", finite.minOrNull() ?: JSONObject.NULL)
            .put("maximum_raw_units", finite.maxOrNull() ?: JSONObject.NULL)
            .put("relative_amplitude_range", if (finite.isNotEmpty()) "0..1 per preview" else JSONObject.NULL)
            .put("descriptive_support", ResearchSignalSummary.supportFor(finite.size, values.size).name)
            .put("algorithm_version", ResearchSignalSummary.VERSION)
            .put("waveform_view", if (finite.size >= 2) "LIMITED" else "FAIL")
            .put("physiological_conversion", "FAIL")
            .put("reason", "Preview statistics; channel mapping and physiological calibration not verified")
        val evidenceDao = dao ?: SignalEvidenceDatabase.get(context).evidence()
        evidenceDao.appendOrVerifyRawSession(
            SensorSessionEntity(sessionId, "wear-data-layer-$kind-preview", System.currentTimeMillis(), metadataJson), rows
        )
        evidenceDao.appendOrVerifyDerived(DerivedSignalEntity(
            "$sessionId:raw-statistics-v1", sessionId, "RAW_PREVIEW_STATISTICS",
            "raw-preview-statistics-v1", null,
            JSONObject().put("source_session_id", sessionId).put("origin", "MEASURED")
                .put("representation", "received_preview_not_full_adc_stream").toString(),
            statistics.toString(), System.currentTimeMillis()
        ))
    }
}
