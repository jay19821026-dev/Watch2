package com.jaysato.xiaomihealthbridge.engine

data class ResearchAnalysisPass(
    val pass: Int,
    val finiteCount: Int,
    val quality: PpgQualityReport,
    val changed: Boolean,
    val reason: String
)

data class ResearchAnalysisLoopResult(
    val values: List<Double?>,
    val passes: List<ResearchAnalysisPass>,
    val informative: Boolean,
    val terminalReason: String
)

/** Reprocesses derived data only when a pass adds evidence; raw input is never changed. */
object ResearchAnalysisLoop {
    fun analyze(
        rawValues: List<Double?>,
        zeroPolicy: ZeroEvidencePolicy,
        producerMarksZeroAsMissing: Boolean = false,
        maxPasses: Int = 3
    ): ResearchAnalysisLoopResult {
        require(maxPasses in 1..8)
        var current = rawValues.toList()
        val passes = mutableListOf<ResearchAnalysisPass>()
        for (index in 0 until maxPasses) {
            val quality = PpgCompositionQualityEngine.assess(current)
            val intercepted = ZeroEvidenceInterceptor.apply(
                current, zeroPolicy, producerMarksZeroAsMissing = producerMarksZeroAsMissing
            )
            val changed = intercepted.analysisValues != current
            passes += ResearchAnalysisPass(
                pass = index + 1,
                finiteCount = current.count { it?.isFinite() == true },
                quality = quality,
                changed = changed,
                reason = intercepted.reason
            )
            if (!changed) break
            current = intercepted.analysisValues
        }
        val informative = current.any { it?.isFinite() == true }
        val stable = passes.lastOrNull()?.changed != true
        return ResearchAnalysisLoopResult(
            values = current,
            passes = passes,
            informative = informative,
            terminalReason = when {
                !informative -> "NO_FINITE_EVIDENCE"
                stable -> "EVIDENCE_STABLE"
                else -> "MAX_PASSES_REACHED"
            }
        )
    }
}
