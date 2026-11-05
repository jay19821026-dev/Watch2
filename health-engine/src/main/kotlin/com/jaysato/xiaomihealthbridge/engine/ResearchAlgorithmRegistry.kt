package com.jaysato.xiaomihealthbridge.engine

enum class NumericMeaning { OBSERVED, RELATIVE_INDEX, SIGNAL_QUALITY, RESEARCH_ESTIMATE, UNKNOWN }
enum class Interpretation { WITHIN_REFERENCE, OUTSIDE_REFERENCE, INSUFFICIENT_EVIDENCE, NOT_APPLICABLE }

data class ReferenceBand(val low: Double, val high: Double, val unit: String, val sourceId: String) {
    init { require(low.isFinite() && high.isFinite() && low <= high && unit.isNotBlank() && sourceId.isNotBlank()) }
}

data class ResearchAlgorithmSpec(
    val id: String,
    val version: String,
    val modality: String,
    val formulaDescription: String,
    val numericMeaning: NumericMeaning,
    val referenceBands: List<ReferenceBand>,
    val clinicalValidated: Boolean = false
)

data class ResearchInterpretation(
    val value: Double?,
    val meaning: NumericMeaning,
    val interpretation: Interpretation,
    val unit: String?,
    val algorithmId: String,
    val algorithmVersion: String,
    val sourceIds: List<String>,
    val reason: String
)

/** Registry for auditable research calculations; it never converts raw ADC to a medical unit. */
object ResearchAlgorithmRegistry {
    private val specs = listOf(
        ResearchAlgorithmSpec("ppg_quality", "v1", "PPG", "finite ratio × stability × motion penalty", NumericMeaning.SIGNAL_QUALITY, emptyList()),
        ResearchAlgorithmSpec("ecg_relative_waveform", "v1", "ECG", "baseline-relative normalized waveform", NumericMeaning.RELATIVE_INDEX, emptyList()),
        ResearchAlgorithmSpec("bioz_waveform_median_position", "v1", "BioZ", "within-session waveform median position; not impedance or a cross-session metric", NumericMeaning.RELATIVE_INDEX, emptyList()),
        ResearchAlgorithmSpec("temperature_evidence", "v1", "temperature", "finite observation + source/time evidence", NumericMeaning.SIGNAL_QUALITY, emptyList()),
        ResearchAlgorithmSpec("vascular_ppg_trend", "v1", "PPG", "quality-weighted personal-baseline delta", NumericMeaning.RESEARCH_ESTIMATE, emptyList())
    )

    fun find(id: String): ResearchAlgorithmSpec? = specs.firstOrNull { it.id == id }

    fun interpret(id: String, value: Double?, unit: String? = null): ResearchInterpretation {
        val spec = find(id) ?: return ResearchInterpretation(
            value, NumericMeaning.UNKNOWN, Interpretation.NOT_APPLICABLE, unit, id, "unknown", emptyList(), "UNKNOWN_ALGORITHM"
        )
        if (value == null || !value.isFinite()) return ResearchInterpretation(
            null, spec.numericMeaning, Interpretation.INSUFFICIENT_EVIDENCE, unit, spec.id, spec.version,
            spec.referenceBands.map { it.sourceId }, "NO_FINITE_VALUE"
        )
        val band = spec.referenceBands.firstOrNull { unit == it.unit }
        val interpretation = when {
            band == null -> Interpretation.NOT_APPLICABLE
            value in band.low..band.high -> Interpretation.WITHIN_REFERENCE
            else -> Interpretation.OUTSIDE_REFERENCE
        }
        return ResearchInterpretation(
            value, spec.numericMeaning, interpretation, unit, spec.id, spec.version,
            spec.referenceBands.map { it.sourceId },
            if (band == null) "RESEARCH_VALUE_WITHOUT_COMPARABLE_BAND" else "REFERENCE_BAND_COMPARISON_ONLY"
        )
    }
}
