package com.jaysato.xiaomihealthbridge.engine

enum class SourceEvidenceLevel { DEVICE_RAW, PLATFORM_MEASURED, EXTERNAL_REFERENCE, PUBLISHED_REFERENCE, UNVERIFIED }

data class SourceEvidence(
    val sourceId: String,
    val level: SourceEvidenceLevel,
    val documentationUrl: String? = null,
    val calibrationVerified: Boolean = false,
    val timestampVerified: Boolean = false
)

data class SourceAssessment(
    val displayLabel: String,
    val confidenceCeiling: Double,
    val usableFor: Set<String>,
    val reason: String
)

/** Uses evidence and validation status, not vendor nationality, to constrain conclusions. */
object SourceEvidencePolicy {
    fun assess(source: SourceEvidence): SourceAssessment = when {
        source.calibrationVerified && source.timestampVerified && source.level == SourceEvidenceLevel.EXTERNAL_REFERENCE ->
            SourceAssessment("外部參考／已驗證", 0.9, setOf("research", "calibrated_comparison"), "REFERENCE_EVIDENCE_VERIFIED")
        source.level == SourceEvidenceLevel.PLATFORM_MEASURED && source.timestampVerified ->
            SourceAssessment("平台實測", 0.7, setOf("research", "trend"), "PLATFORM_TIMESTAMPED_MEASUREMENT")
        source.level == SourceEvidenceLevel.DEVICE_RAW ->
            SourceAssessment("裝置原始資料", 0.4, setOf("raw", "relative", "quality"), "RAW_UNIT_OR_CALIBRATION_MAY_BE_UNVERIFIED")
        source.level == SourceEvidenceLevel.PUBLISHED_REFERENCE ->
            SourceAssessment("公開研究參考", 0.3, setOf("comparison"), "REFERENCE_NOT_A_PERSONAL_MEASUREMENT")
        else -> SourceAssessment("來源未驗證", 0.1, setOf("raw"), "INSUFFICIENT_SOURCE_EVIDENCE")
    }
}
