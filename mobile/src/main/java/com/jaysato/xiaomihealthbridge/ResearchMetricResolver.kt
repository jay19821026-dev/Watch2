package com.jaysato.xiaomihealthbridge

enum class ResearchMetricState(val displayLabel: String) {
    MEASURED("實測"),
    RESEARCH_ESTIMATE("研究估算"),
    HISTORICAL("歷史讀值"),
    TIME_UNVERIFIED("時間未確認"),
    TIME_INVALID("時間異常"),
    UNAVAILABLE("無資料")
}

data class ResearchMetricCandidate(
    val value: Double?,
    val source: String,
    val measuredAtMillis: Long,
    val timestampSource: String?,
    val qualityScore: Int?,
    val isResearch: Boolean
)

data class ResolvedResearchMetric(
    val value: Double?,
    val source: String,
    val state: ResearchMetricState,
    val qualityScore: Int?,
    val measuredAtMillis: Long,
    val timestampSource: String?,
    val reason: String
)

object ResearchMetricResolver {
    fun resolve(
        candidates: List<ResearchMetricCandidate>,
        nowMillis: Long,
        freshnessMillis: Long = 30 * 60 * 1000L
    ): ResolvedResearchMetric {
        require(freshnessMillis > 0)
        val valid = candidates.filter { candidate ->
            candidate.value?.isFinite() == true && candidate.value in 0.0..1_000_000.0
        }.sortedWith(compareByDescending<ResearchMetricCandidate> { !it.isResearch }.thenByDescending { it.measuredAtMillis })
        val fresh = valid.filter { candidate ->
            HealthAnalysisFreshnessPolicy.isFreshMeasurement(
                candidate.measuredAtMillis,
                candidate.timestampSource,
                nowMillis,
                freshnessMillis
            )
        }
        val selected = fresh.sortedWith(
            compareByDescending<ResearchMetricCandidate> { !it.isResearch }
                .thenByDescending { it.measuredAtMillis }
        ).firstOrNull() ?: valid.filter { it.measuredAtMillis in 1L..nowMillis }.ifEmpty { valid }.sortedWith(
            compareByDescending<ResearchMetricCandidate> { it.measuredAtMillis }
                .thenByDescending { !it.isResearch }
        ).firstOrNull()
            ?: return ResolvedResearchMetric(null, "NONE", ResearchMetricState.UNAVAILABLE, null, 0L, null, "NO_FINITE_CANDIDATE")
        val historical = selected !in fresh
        val timestampTrusted = HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(selected.timestampSource)
        val timestampValid = selected.measuredAtMillis in 1L..nowMillis
        val state = when {
            !timestampValid && selected.measuredAtMillis > nowMillis -> ResearchMetricState.TIME_INVALID
            !timestampTrusted || !timestampValid -> ResearchMetricState.TIME_UNVERIFIED
            historical -> ResearchMetricState.HISTORICAL
            selected.isResearch -> ResearchMetricState.RESEARCH_ESTIMATE
            else -> ResearchMetricState.MEASURED
        }
        val reason = when {
            selected.measuredAtMillis > nowMillis -> "FUTURE_TIMESTAMP"
            selected.measuredAtMillis <= 0L -> "TIMESTAMP_MISSING"
            !timestampTrusted -> "TIMESTAMP_SOURCE_UNTRUSTED"
            historical -> "VALUE_PRESENT_BUT_NOT_FRESH"
            else -> "HIGHEST_PRIORITY_FRESH_SOURCE"
        }
        return ResolvedResearchMetric(
            value = selected.value,
            source = selected.source,
            state = state,
            qualityScore = selected.qualityScore?.coerceIn(0, 100),
            measuredAtMillis = selected.measuredAtMillis,
            timestampSource = selected.timestampSource,
            reason = reason
        )
    }
}
