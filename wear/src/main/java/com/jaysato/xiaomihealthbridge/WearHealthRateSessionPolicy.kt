package com.jaysato.xiaomihealthbridge

internal data class WearHealthRateSessionMetadata(
    val sessionStartedAtMs: Long,
    val suiteId: String?,
    val suiteStartedAtMs: Long
)

internal object WearHealthRateSessionPolicy {
    fun metadataForSnapshot(
        activeSuiteId: String?,
        activeSuiteStartedAtMs: Long,
        sampleTimestamp: Long
    ): WearHealthRateSessionMetadata {
        val suiteId = activeSuiteId?.takeIf { it.isNotBlank() }
            ?.takeIf { activeSuiteStartedAtMs > 0L }
            ?.takeIf { sampleTimestamp >= activeSuiteStartedAtMs }
        return WearHealthRateSessionMetadata(
            sessionStartedAtMs = suiteId?.let { activeSuiteStartedAtMs } ?: sampleTimestamp,
            suiteId = suiteId,
            suiteStartedAtMs = suiteId?.let { activeSuiteStartedAtMs } ?: 0L
        )
    }
}
