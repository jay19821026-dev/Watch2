package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.SerializedSensorPayloadPolicy
import com.jaysato.xiaomihealthbridge.engine.RawSensorCountContract

object RawSensorPayloadPolicy {
    fun hasAcquisitionEvidence(eventCount: Int, sampleCount: Int, waveformCount: Int, latestCount: Int): Boolean =
        eventCount > 0 || sampleCount > 0 || waveformCount > 0 || latestCount > 0

    private val biomedicalKinds = setOf(
        "ppg", "max_hr", "max_heart_rate", "lifeq_ott", "spo2_raw", "mi_spo2", "ecg", "bioz",
        "bioz_respiration", "heart_rate_raw", "body_temperature_raw", "thermometer"
    )
    const val MAX_WAVEFORM_POINTS = 5_120
    const val MAX_LATEST_CSV_CHARS = 4_096
    const val MAX_ECG_EVENT_ROWS = 512
    const val MAX_ECG_METADATA_CHARS = 32_768

    fun isBiomedicalKind(kind: String): Boolean = kind in biomedicalKinds

    fun biomedicalSampleCount(reportedSamples: Int, retainedWaveformPoints: Int): Int =
        reportedSamples.coerceAtLeast(0)

    fun sourceValueCount(contractVersion: Int, rawValueCount: Int?): Int? =
        if (contractVersion == RawSensorCountContract.VERSION) rawValueCount?.coerceAtLeast(0)
        else null

    fun storedDisplayCount(sourceValueCount: Int?, legacySamples: Int): Int =
        sourceValueCount ?: legacySamples.coerceAtLeast(0)

    fun displayCount(isBiomedical: Boolean, contractVersion: Int, rawValueCount: Int?, reportedSamples: Int): Int = when {
        !isBiomedical -> reportedSamples.coerceAtLeast(0)
        contractVersion == RawSensorCountContract.VERSION -> rawValueCount?.coerceAtLeast(0) ?: 0
        contractVersion in 0 until RawSensorCountContract.VERSION -> reportedSamples.coerceAtLeast(0)
        else -> 0
    }

    fun exportSampleCount(
        isBiomedical: Boolean,
        contractVersion: Int,
        rawValueCount: Int?,
        reportedSamples: Int
    ): Int? = when {
        !isBiomedical -> reportedSamples.coerceAtLeast(0)
        contractVersion == RawSensorCountContract.VERSION -> rawValueCount?.coerceAtLeast(0)
        contractVersion in 0 until RawSensorCountContract.VERSION -> reportedSamples.coerceAtLeast(0)
        else -> null
    }

    fun hasStatisticSampleCount(isBiomedical: Boolean, rawValueCount: Int?, displayCount: Int): Boolean =
        if (isBiomedical) (rawValueCount ?: 0) > 0 else displayCount > 0

    fun hasReceivedData(
        contractVersion: Int,
        rawValueCount: Int?,
        eventCount: Int,
        previewPointCount: Int,
        legacySamples: Int
    ): Boolean = when {
        contractVersion == RawSensorCountContract.VERSION ->
            (rawValueCount ?: 0) > 0 || eventCount > 0 || previewPointCount > 0
        contractVersion in 0 until RawSensorCountContract.VERSION -> legacySamples > 0
        else -> false
    }

    fun displayPresenceCount(
        contractVersion: Int,
        rawValueCount: Int?,
        eventCount: Int,
        previewPointCount: Int,
        legacySamples: Int
    ): Int = if (hasReceivedData(contractVersion, rawValueCount, eventCount, previewPointCount, legacySamples)) 1 else 0

    fun previewPointCount(reportedPreviewPoints: Int, payloadPoints: Int): Int =
        minOf(reportedPreviewPoints.coerceAtLeast(0), payloadPoints.coerceAtLeast(0))

    fun rejectionReason(
        waveformPoints: Int,
        latestCsvChars: Int,
        eventTimestampCount: Int,
        eventSampleCountCount: Int,
        eventMetadataChars: Int,
        eventCallbackTimestampCount: Int = 0,
        eventCallbackMetadataPresent: Boolean = false,
        eventRawValueRowCount: Int? = null,
        eventRawValuesStatus: String? = null,
        eventTimestampValidityCount: Int = 0,
        eventTimestampValidityMetadataPresent: Boolean = false,
        eventTimestampValidityValues: List<Long>? = null,
        serializedDataMapBytes: Int = 0,
        declaredPayloadRejection: String? = null
    ): String? = when {
        SerializedSensorPayloadPolicy.rejectionReason(serializedDataMapBytes, declaredPayloadRejection) != null ->
            SerializedSensorPayloadPolicy.rejectionReason(serializedDataMapBytes, declaredPayloadRejection)
        waveformPoints < 0 || waveformPoints > MAX_WAVEFORM_POINTS -> "oversized_waveform"
        latestCsvChars < 0 || latestCsvChars > MAX_LATEST_CSV_CHARS -> "oversized_latest"
        eventTimestampCount < 0 || eventTimestampCount > MAX_ECG_EVENT_ROWS -> "oversized_event_timestamps"
        eventSampleCountCount < 0 || eventSampleCountCount > MAX_ECG_EVENT_ROWS -> "oversized_event_sample_counts"
        eventTimestampCount != eventSampleCountCount -> "mismatched_event_metadata"
        eventTimestampValidityCount < 0 || eventTimestampValidityCount > MAX_ECG_EVENT_ROWS -> "oversized_event_timestamp_validity"
        eventTimestampValidityMetadataPresent && eventTimestampValidityCount != eventTimestampCount -> "mismatched_event_timestamp_validity"
        eventTimestampValidityValues?.any { it !in 0L..1L } == true -> "invalid_event_timestamp_validity"
        eventMetadataChars < 0 || eventMetadataChars > MAX_ECG_METADATA_CHARS -> "oversized_event_metadata"
        eventCallbackTimestampCount < 0 || eventCallbackTimestampCount > MAX_ECG_EVENT_ROWS -> "oversized_event_callback_timestamps"
        eventCallbackMetadataPresent && eventCallbackTimestampCount != eventTimestampCount -> "mismatched_event_callback_timestamps"
        eventRawValueRowCount != null && eventRawValuesStatus in setOf("complete_for_observed_events", "bounded_recent_events") &&
            eventRawValueRowCount != eventTimestampCount -> "mismatched_event_raw_value_rows"
        else -> null
    }
}
