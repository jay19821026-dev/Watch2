package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.EcgRawEventDecoder
import com.jaysato.xiaomihealthbridge.engine.EcgEventStructureInterpreter
import com.jaysato.xiaomihealthbridge.engine.EcgCandidateLayoutComparison
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal object EcgEventFieldResearch {
    const val ALGORITHM_VERSION = "ecg-event-fields-relative-index-v3"

    data class FieldTrace(
        val fieldIndex: Int,
        val finiteEventCount: Int,
        val zeroValueCount: Int,
        val relativeAmplitude: List<Double?>
    )

    data class Summary(
        val eventCount: Int,
        val inspectedFieldValueCount: Int,
        val status: String,
        val fields: List<FieldTrace>,
        val layoutComparison: EcgCandidateLayoutComparison?
    )

    fun summarize(decoded: EcgHalEventResearchDecoder.Result): Summary? {
        if (decoded.status != "candidate_values_unverified") return null
        val fields = decoded.layoutComparison?.fieldSummaries.orEmpty()
        if (fields.isEmpty()) return null
        return Summary(
            eventCount = decoded.eventCount,
            inspectedFieldValueCount = decoded.inspectedFieldValueCount,
            status = decoded.status,
            layoutComparison = decoded.layoutComparison,
            fields = fields.map { field ->
                FieldTrace(
                    fieldIndex = field.fieldIndex,
                    finiteEventCount = field.finiteValueCount,
                    zeroValueCount = field.zeroValueCount,
                    relativeAmplitude = field.normalizedEventIndexPreview
                )
            }
        )
    }

    fun toDerivedRecord(
        sessionId: String,
        source: String,
        sourceTimestampSource: String,
        sourceTimestampMillis: Long?,
        publishedAtMillis: Long?,
        sourceSnapshotRows: String,
        summary: Summary,
        createdAt: Long = System.currentTimeMillis()
    ): DerivedSignalEntity? {
        if (summary.status != "candidate_values_unverified" || summary.fields.isEmpty()) return null
        if (sourceSnapshotRows.length > EcgEventRowExportPolicy.MAX_METADATA_CHARS) return null
        val sourceSnapshotSha256 = MessageDigest.getInstance("SHA-256")
            .digest(sourceSnapshotRows.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        val traces = JSONArray().apply {
            summary.fields.forEach { field ->
                put(JSONObject()
                    .put("field_index", field.fieldIndex)
                    .put("finite_event_count", field.finiteEventCount)
                    .put("zero_value_count", field.zeroValueCount)
                    .put("relative_amplitude_0_to_1", JSONArray(field.relativeAmplitude.map {
                        it?.takeIf(Double::isFinite) ?: JSONObject.NULL
                    }))
                )
            }
        }
        val structureConclusion = EcgEventStructureInterpreter.interpret(summary.eventCount, summary.layoutComparison)
        return DerivedSignalEntity(
            resultId = "$sessionId:$ALGORITHM_VERSION:$sourceSnapshotSha256",
            sessionId = sessionId,
            resultType = "ecg-event-fields-relative-index-research",
            algorithmVersion = ALGORITHM_VERSION,
            modelVersion = null,
            provenanceJson = JSONObject()
                .put("source_session_id", sessionId)
                .put("source", source)
                .put("source_snapshot_sha256", sourceSnapshotSha256)
                .put("origin", "NORMALIZED_FROM_MEASURED_RAW_EVENT_FIELDS")
                .put("event_field_mapping", "UNVERIFIED")
                .put("timeline", "NORMALIZED_EVENT_INDEX")
                .put("timestamp_source", "NORMALIZED_EVENT_INDEX")
                .put("source_timestamp_source", sourceTimestampSource.ifBlank { "UNKNOWN" })
                .put("source_measurement_timestamp_ms", sourceTimestampMillis ?: JSONObject.NULL)
                .put("published_at_ms", publishedAtMillis ?: JSONObject.NULL)
                .put("sample_rate_hz", JSONObject.NULL)
                .put("unit", "PER_FIELD_MIN_MAX_RELATIVE_AMPLITUDE")
                .put("calibration_status", "UNVERIFIED")
                .put("clinical_use", false)
                .toString(),
            payloadJson = JSONObject()
                .put("event_count", summary.eventCount)
                .put("inspected_field_value_count", summary.inspectedFieldValueCount)
                .put("field_count", summary.fields.size)
                .put("preview_point_limit", EcgRawEventDecoder.MAX_PREVIEW_POINTS)
                .put("field_relative_preview", traces)
                .put("structure_interpretation_status", structureConclusion.status)
                .put("structure_interpretation_headline", structureConclusion.headline)
                .put("structure_interpretation_details", JSONArray(structureConclusion.details))
                .put("status", summary.status)
                .put("decoded_ecg_sample_count", 0)
                .toString(),
            createdAt = createdAt
        )
    }
}
