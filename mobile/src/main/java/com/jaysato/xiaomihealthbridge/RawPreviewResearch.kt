package com.jaysato.xiaomihealthbridge

import org.json.JSONArray
import org.json.JSONObject

internal object RawPreviewResearch {
    const val ALGORITHM_VERSION = "relative-index-preview-v1"
    private const val MAX_PREVIEW_POINTS = 100_000
    private val supportedKinds = setOf("spo2_raw", "mi_spo2", "thermometer", "body_temperature_raw")

    data class Summary(
        val kind: String,
        val preview: ResearchSignalSummary.Result,
        val pointOriginLabels: List<String>,
        val finitePreviewCount: Int,
        val zeroFiniteCount: Int,
        val qualityIndex: ResearchQualityIndex
    )

    fun summarize(kind: String, source: String, preview: ResearchSignalSummary.Result): Summary {
        require(kind in supportedKinds && source.isNotBlank())
        require(preview.values.size <= MAX_PREVIEW_POINTS)
        val finiteValues = preview.values.filterNotNull().filter(Double::isFinite)
        val quality = ResearchQualityIndexCalculator.calculate(
            finiteRatio = if (preview.values.isEmpty()) null else finiteValues.size.toDouble() / preview.values.size,
            sampleCount = finiteValues.size,
            waveformStability = null,
            sourceEvidence = ResearchSourceEvidence.score(source),
            timeEvidence = 0
        )
        val origins = preview.values.map { value ->
            if (value != null && value.isFinite()) "PRESENT_IN_RECEIVED_PREVIEW" else "UNKNOWN_OR_GAP"
        }
        return Summary(kind, preview, origins, finiteValues.size, finiteValues.count { it == 0.0 }, quality)
    }

    fun toDerivedRecord(
        sessionId: String,
        source: String,
        timestampSource: String,
        sourceMeasurementTimestampMillis: Long?,
        publishedAtMillis: Long?,
        result: Summary,
        createdAt: Long = System.currentTimeMillis()
    ): DerivedSignalEntity {
        require(sessionId.isNotBlank() && result.kind in supportedKinds && source.isNotBlank())
        require(createdAt >= 0L)
        val kind = result.kind
        val preview = result.preview
        val provenance = JSONObject()
            .put("source_session_id", sessionId)
            .put("source", source)
            .put("origin", "RELATIVE_INDEX_DERIVED_FROM_RECEIVED_PREVIEW")
            .put("preview_representation", "RECEIVED_PREVIEW_NOT_FULL_SENSOR_STREAM")
            .put("timestamp_source", timestampSource.ifBlank { "UNKNOWN" })
            .put("source_measurement_timestamp_ms", sourceMeasurementTimestampMillis ?: JSONObject.NULL)
            .put("published_at_ms", publishedAtMillis ?: JSONObject.NULL)
            .put("timeline", "NORMALIZED_PREVIEW_INDEX")
            .put("sample_rate_hz", JSONObject.NULL)
            .put("unit", "UNKNOWN_RAW_CODE")
            .put("calibration_status", "UNVERIFIED")
            .put("confidence", "NOT_ESTABLISHED")
            .put("clinical_use", false)
            .put("raw_evidence_location", "raw_signal_samples")
            .toString()
        val payload = JSONObject()
            .put("kind", kind)
            .put("preview_point_count", preview.values.size)
            .put("finite_preview_point_count", result.finitePreviewCount)
            .put("zero_finite_count", result.zeroFiniteCount)
            .put("finite_preview_ratio", preview.measuredRatio)
            .put("raw_code_mean", preview.mean ?: JSONObject.NULL)
            .put("raw_code_standard_deviation", preview.standardDeviation ?: JSONObject.NULL)
            .put("relative_index_trend_raw_code_per_index", preview.indexTrend ?: JSONObject.NULL)
            .put("mean_adjacent_absolute_difference_raw_code", preview.meanAbsoluteDifference ?: JSONObject.NULL)
            .put("relative_amplitude_0_to_1", JSONArray(preview.relativeAmplitude.map { it ?: JSONObject.NULL }))
            .put("preview_point_origin_labels", JSONArray(result.pointOriginLabels))
            .put("evidence_completeness_score", result.qualityIndex.score ?: JSONObject.NULL)
            .put("evidence_completeness_reason", result.qualityIndex.reason)
            .put("accuracy_or_clinical_confidence", JSONObject.NULL)
            .toString()

        return DerivedSignalEntity(
            resultId = "$sessionId:$ALGORITHM_VERSION",
            sessionId = sessionId,
            resultType = "$kind-relative-index-research",
            algorithmVersion = ALGORITHM_VERSION,
            modelVersion = null,
            provenanceJson = provenance,
            payloadJson = payload,
            createdAt = createdAt
        )
    }
}
