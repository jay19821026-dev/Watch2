package com.jaysato.xiaomihealthbridge

data class ResearchQualityIndex(
    val score: Int?,
    val finiteRatio: Double?,
    val sampleEvidence: Int,
    val waveformStability: Int?,
    val sourceEvidence: Int,
    val timeEvidence: Int,
    val reason: String
)

object ResearchScoreDisplay {
    fun format(score: Int?): String =
        score?.takeIf { it in 1..100 }?.let { "$it/100" } ?: "未評分"

    fun format(score: Double?): String =
        score?.takeIf { it.isFinite() && it > 0.0 && it <= 100.0 }
            ?.let { "%.1f/100".format(it) } ?: "未評分"
}

object ResearchSourceEvidence {
    private val unavailableSources = setOf("", "/", "UNKNOWN", "UNKNOWN_SOURCE", "NOT_REPORTED", "UNAVAILABLE", "NULL")

    fun score(source: String?): Int = if (source?.trim()?.uppercase().orEmpty() in unavailableSources) 0 else 80
}

object ResearchQualityIndexCalculator {
    const val VERSION = "research-quality-index-v1"

    fun calculate(
        finiteRatio: Double?,
        sampleCount: Int,
        waveformStability: Int?,
        sourceEvidence: Int,
        timeEvidence: Int
    ): ResearchQualityIndex {
        require(sampleCount >= 0)
        val finite = finiteRatio?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
        val sample = when {
            sampleCount >= 1024 -> 25
            sampleCount >= 64 -> 20
            sampleCount >= 4 -> 12
            sampleCount > 0 -> 5
            else -> 0
        }
        val parts = listOfNotNull(
            finite?.times(30.0),
            waveformStability?.coerceIn(0, 100)?.times(0.25),
            sourceEvidence.coerceIn(0, 100) * 0.20,
            timeEvidence.coerceIn(0, 100) * 0.15
        )
        val score = if (parts.isEmpty() || sample == 0 || finite == null || finite <= 0.0) null else
            (parts.sum() + sample).toInt().coerceIn(0, 100)
        return ResearchQualityIndex(
            score = score,
            finiteRatio = finite,
            sampleEvidence = sample,
            waveformStability = waveformStability,
            sourceEvidence = sourceEvidence.coerceIn(0, 100),
            timeEvidence = timeEvidence.coerceIn(0, 100),
            reason = if (score == null) "NO_FINITE_OBSERVATION" else "EVIDENCE_COMPLETENESS_NOT_ACCURACY"
        )
    }
}
