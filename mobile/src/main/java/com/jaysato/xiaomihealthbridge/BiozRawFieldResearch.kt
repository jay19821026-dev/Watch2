package com.jaysato.xiaomihealthbridge

internal object BiozRawFieldResearch {
    const val ALGORITHM_VERSION = "bioz-event-field-research-v1"

    data class Field(
        val index: Int,
        val eventCount: Int,
        val finiteCount: Int,
        val mean: Double?,
        val standardDeviation: Double?,
        val relativeDispersionPercent: Double?,
        val firstToLastQuartileChangePercent: Double?,
        val relativeAmplitude: List<Double?>
    )

    data class Result(
        val eventCount: Int,
        val expectedValueCount: Int,
        val finiteValueCount: Int,
        val evidenceCompleteness: ResearchQualityIndex,
        val fields: List<Field>
    )

    fun analyze(rawRows: String, source: String): Result? {
        val decoded = RawPacketChannelDecoder.decode(rawRows)
        if (decoded.channels.isEmpty()) return null
        val fields = decoded.channels.mapIndexed { index, values ->
            val summary = ResearchSignalSummary.analyze(values.map(Double::toDouble), mappedOrderedWaveform = true)
            val finite = values.filter(Double::isFinite)
            val range = finite.maxOrNull()?.minus(finite.minOrNull() ?: Double.NaN)
            val relativeDispersion = summary.standardDeviation?.let { deviation ->
                if (range != null && range.isFinite() && range > 0.0) {
                    (deviation / range * 100.0).takeIf(Double::isFinite)
                } else null
            }
            val quartileSize = (values.size / 4).coerceAtLeast(1)
            val first = values.take(quartileSize).filter(Double::isFinite)
            val last = values.takeLast(quartileSize).filter(Double::isFinite)
            val relativeChange = if (first.size >= 2 && last.size >= 2 && range != null && range.isFinite() && range > 0.0) {
                ((last.average() - first.average()) / range * 100.0).takeIf(Double::isFinite)
            } else null
            Field(
                index = index,
                eventCount = values.size,
                finiteCount = finite.size,
                mean = summary.mean,
                standardDeviation = summary.standardDeviation,
                relativeDispersionPercent = relativeDispersion,
                firstToLastQuartileChangePercent = relativeChange,
                relativeAmplitude = summary.relativeAmplitude
            )
        }
        val expectedValueCount = decoded.eventCount * decoded.channels.size
        val finiteValueCount = fields.sumOf(Field::finiteCount)
        val completeness = ResearchQualityIndexCalculator.calculate(
            finiteRatio = finiteValueCount.toDouble() / expectedValueCount.coerceAtLeast(1),
            sampleCount = finiteValueCount,
            waveformStability = null,
            sourceEvidence = ResearchSourceEvidence.score(source),
            timeEvidence = 0
        )
        return Result(decoded.eventCount, expectedValueCount, finiteValueCount, completeness, fields)
    }

    fun toDerivedRecord(
        sessionId: String,
        source: String,
        timestampSource: String,
        sourceTimestampMillis: Long?,
        publishedAtMillis: Long?,
        result: Result,
        createdAt: Long = System.currentTimeMillis()
    ): DerivedSignalEntity {
        val fields = org.json.JSONArray().apply {
            result.fields.forEach { field ->
                put(org.json.JSONObject()
                    .put("field_index", field.index)
                    .put("event_count", field.eventCount)
                    .put("finite_count", field.finiteCount)
                    .put("mean_raw_code", field.mean?.takeIf(Double::isFinite) ?: org.json.JSONObject.NULL)
                    .put("standard_deviation_raw_code", field.standardDeviation?.takeIf(Double::isFinite) ?: org.json.JSONObject.NULL)
                    .put("relative_dispersion_percent", field.relativeDispersionPercent?.takeIf(Double::isFinite) ?: org.json.JSONObject.NULL)
                    .put("first_to_last_quartile_change_percent", field.firstToLastQuartileChangePercent?.takeIf(Double::isFinite) ?: org.json.JSONObject.NULL)
                    .put("relative_amplitude", org.json.JSONArray(field.relativeAmplitude.map { it ?: org.json.JSONObject.NULL }))
                )
            }
        }
        return DerivedSignalEntity(
            "$sessionId:$ALGORITHM_VERSION",
            sessionId,
            "bioz-event-field-relative-research",
            ALGORITHM_VERSION,
            null,
            org.json.JSONObject()
                .put("source_session_id", sessionId)
                .put("source", source)
                .put("origin", "DERIVED_RESEARCH_FROM_RAW_HAL_FIELDS")
                .put("field_mapping", "UNVERIFIED")
                .put("timestamp_source", timestampSource)
                .put("source_measurement_timestamp_ms", sourceTimestampMillis ?: org.json.JSONObject.NULL)
                .put("published_at_ms", publishedAtMillis ?: org.json.JSONObject.NULL)
                .put("timeline", "NORMALIZED_EVENT_INDEX")
                .put("sample_rate_hz", org.json.JSONObject.NULL)
                .put("unit_calibration", "UNVERIFIED_RAW_CODES")
                .put("clinical_use", false)
                .toString(),
            org.json.JSONObject()
                .put("event_count", result.eventCount)
                .put("expected_value_count", result.expectedValueCount)
                .put("finite_value_count", result.finiteValueCount)
                .put("evidence_completeness_score", result.evidenceCompleteness.score ?: org.json.JSONObject.NULL)
                .put("fields", fields)
                .toString(),
            createdAt
        )
    }
}
