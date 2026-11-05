package com.jaysato.xiaomihealthbridge.engine

enum class ZeroMeaning { MEASURED_ZERO, MISSING_SENTINEL, UNKNOWN, AUTO }

data class ZeroEvidencePolicy(
    val zeroMeaning: ZeroMeaning,
    val previousFiniteWindow: Int = 8,
    val allowDerivedFallback: Boolean = true
) {
    init { require(previousFiniteWindow in 1..1024) }
}

data class ZeroInterceptionResult(
    val rawValues: List<Double?>,
    val analysisValues: List<Double?>,
    val replacedIndices: List<Int>,
    val reason: String
)

/**
 * Separates a true sensor zero from an application/OEM missing-value sentinel.
 * Raw values are immutable; only the derived analysis series may use a fallback.
 */
object ZeroEvidenceInterceptor {
    fun apply(
        rawValues: List<Double?>,
        policy: ZeroEvidencePolicy,
        provenance: SensorProvenance? = null,
        producerMarksZeroAsMissing: Boolean = false
    ): ZeroInterceptionResult {
        require(rawValues.size <= 100_000)
        val meaning = when {
            policy.zeroMeaning != ZeroMeaning.AUTO -> policy.zeroMeaning
            producerMarksZeroAsMissing -> ZeroMeaning.MISSING_SENTINEL
            else -> ZeroMeaning.MEASURED_ZERO
        }
        if (meaning != ZeroMeaning.MISSING_SENTINEL || !policy.allowDerivedFallback) {
            return ZeroInterceptionResult(rawValues.toList(), rawValues.toList(), emptyList(), "ZERO_PRESERVED")
        }
        val analysis = rawValues.toMutableList()
        val replaced = mutableListOf<Int>()
        rawValues.forEachIndexed { index, value ->
            if (value != 0.0) return@forEachIndexed
            val fallback = rawValues.subList(maxOf(0, index - policy.previousFiniteWindow), index)
                .asReversed().firstOrNull { it?.isFinite() == true && it != 0.0 }
                ?: rawValues.drop(index + 1).take(policy.previousFiniteWindow)
                    .firstOrNull { it?.isFinite() == true && it != 0.0 }
            if (fallback != null) {
                analysis[index] = fallback
                replaced += index
            } else {
                analysis[index] = null
            }
        }
        return ZeroInterceptionResult(
            rawValues = rawValues.toList(),
            analysisValues = analysis,
            replacedIndices = replaced,
            reason = if (replaced.isEmpty()) "MISSING_SENTINEL_WITHOUT_FINITE_CONTEXT" else
                "MISSING_SENTINEL_REPLACED_IN_DERIVED_SERIES"
        )
    }
}
