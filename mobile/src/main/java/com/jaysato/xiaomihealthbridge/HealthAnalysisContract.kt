package com.jaysato.xiaomihealthbridge

/** User-facing interpretation of evidence, not a medical diagnosis. */
enum class HealthResultState {
    NORMAL,
    ATTENTION,
    RESEARCH_ONLY,
    UNAVAILABLE
}

enum class EvidenceOrigin {
    MEASURED,
    FILTERED,
    RECONSTRUCTED,
    IMPUTED,
    MODEL_ESTIMATE,
    UNKNOWN
}

enum class EvidenceTimebase {
    SENSOR,
    ESTIMATED,
    NORMALIZED_INDEX,
    UNKNOWN
}

enum class CalibrationState {
    VERIFIED_DEVICE,
    VALIDATED_REFERENCE,
    UNVERIFIED,
    NOT_APPLICABLE,
    UNKNOWN
}

enum class EvidenceSupport {
    SUPPORTED,
    LIMITED,
    INSUFFICIENT
}

enum class HealthReasonCode {
    NO_EVIDENCE,
    INSUFFICIENT_EVIDENCE,
    STALE_EVIDENCE,
    SOURCE_UNVERIFIED,
    TIMEBASE_UNVERIFIED,
    UNIT_UNVERIFIED,
    CALIBRATION_UNVERIFIED,
    QUALITY_LIMITED,
    RECONSTRUCTED_INPUT,
    MODEL_ESTIMATE_INPUT,
    ANALYSIS_ERROR
}

/**
 * A typed, provenance-preserving result for one metric.
 *
 * `value` is nullable to distinguish missing evidence from a legitimate zero.
 * Values with unverified units must be represented with [rawValue] and no
 * physical [unit].
 */
data class HealthMetricResult(
    val metricId: String,
    val state: HealthResultState,
    val origin: EvidenceOrigin,
    val timebase: EvidenceTimebase,
    val calibration: CalibrationState,
    val value: Double? = null,
    val unit: String? = null,
    val rawValue: Double? = null,
    val rawUnit: String? = null,
    val source: String? = null,
    val quality: Double? = null,
    val evidenceSupport: EvidenceSupport,
    val reasonCodes: Set<HealthReasonCode> = emptySet(),
    val algorithmVersion: String,
    val modelVersion: String? = null,
    val measuredAtEpochMillis: Long? = null
) {
    init {
        require(metricId.isNotBlank()) { "metricId must not be blank" }
        require(algorithmVersion.isNotBlank()) { "algorithmVersion must not be blank" }
        require(value == null || value.isFinite()) { "value must be finite when present" }
        require(rawValue == null || rawValue.isFinite()) { "rawValue must be finite when present" }
        require(quality == null || quality.isFinite() && quality in 0.0..1.0) {
            "quality must be between 0 and 1 when present"
        }
        require(unit == null || unit.isNotBlank()) { "unit must not be blank when present" }
        require(rawUnit == null || rawUnit.isNotBlank()) { "rawUnit must not be blank when present" }
        require(source == null || source.isNotBlank()) { "source must not be blank when present" }
        require(modelVersion == null || modelVersion.isNotBlank()) {
            "modelVersion must not be blank when present"
        }
        require(measuredAtEpochMillis == null || measuredAtEpochMillis >= 0L) {
            "measuredAtEpochMillis must not be negative"
        }

        if (state == HealthResultState.UNAVAILABLE) {
            require(value == null && rawValue == null) {
                "unavailable results cannot contain measurement values"
            }
            require(HealthReasonCode.NO_EVIDENCE in reasonCodes ||
                HealthReasonCode.INSUFFICIENT_EVIDENCE in reasonCodes ||
                HealthReasonCode.ANALYSIS_ERROR in reasonCodes) {
                "unavailable results require a no-evidence, insufficient-evidence, or error reason"
            }
        }

        if (state == HealthResultState.NORMAL || state == HealthResultState.ATTENTION) {
            require(evidenceSupport == EvidenceSupport.SUPPORTED) {
                "normal or attention requires supported evidence"
            }
            require(source != null) { "normal or attention requires a source" }
            require(timebase != EvidenceTimebase.UNKNOWN) {
                "normal or attention requires a known timebase"
            }
            require(origin == EvidenceOrigin.MEASURED || origin == EvidenceOrigin.FILTERED) {
                "normal or attention cannot be based on reconstructed or estimated evidence"
            }
        }

        if (timebase != EvidenceTimebase.UNKNOWN) {
            require(source != null) { "a declared timebase requires a source" }
        }

        if (value != null) {
            require(unit != null) { "a physical value requires an explicit unit" }
        }

        if (unit != null) {
            require(calibration == CalibrationState.VERIFIED_DEVICE ||
                calibration == CalibrationState.VALIDATED_REFERENCE ||
                calibration == CalibrationState.NOT_APPLICABLE) {
                "physical units require verified/validated calibration or not-applicable calibration"
            }
        }

        if (state == HealthResultState.RESEARCH_ONLY) {
            require(evidenceSupport != EvidenceSupport.SUPPORTED ||
                origin == EvidenceOrigin.MEASURED || origin == EvidenceOrigin.FILTERED) {
                "research-only reconstructed or estimated evidence must declare limited/insufficient support"
            }
        }
    }
}

data class HealthSessionResult(
    val sessionId: String,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val results: List<HealthMetricResult>,
    val algorithmVersion: String
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(algorithmVersion.isNotBlank()) { "algorithmVersion must not be blank" }
        require(startedAtEpochMillis == null || startedAtEpochMillis >= 0L) {
            "startedAtEpochMillis must not be negative"
        }
        require(endedAtEpochMillis == null || endedAtEpochMillis >= 0L) {
            "endedAtEpochMillis must not be negative"
        }
        require(startedAtEpochMillis == null || endedAtEpochMillis == null ||
            endedAtEpochMillis >= startedAtEpochMillis) {
            "session end must not precede session start"
        }
        require(results.map(HealthMetricResult::metricId).distinct().size == results.size) {
            "a session cannot contain duplicate metric ids"
        }
        require(results.all { result ->
            result.measuredAtEpochMillis == null ||
                (startedAtEpochMillis == null || result.measuredAtEpochMillis >= startedAtEpochMillis) &&
                (endedAtEpochMillis == null || result.measuredAtEpochMillis <= endedAtEpochMillis)
        }) { "measurement timestamps must fall within the session when bounds are known" }
    }
}
