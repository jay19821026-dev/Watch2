package com.jaysato.xiaomihealthbridge

internal object SensorRowPresentationPolicy {
    fun displayState(
        summary: String?,
        persistedState: SensorDisplayState,
        skippedCharging: Boolean = false
    ): SensorDisplayState = when {
        skippedCharging -> SensorDisplayState.CONDITION_NOT_MET
        summary.isNullOrBlank() -> SensorDisplayState.NOT_MEASURED
        else -> persistedState
    }

    fun missingSummaryMessage(acquisitionState: SensorAcquisitionDiagnosticState): String =
        if (acquisitionState == SensorAcquisitionDiagnosticState.SNAPSHOT_RECEIVED) {
            "本次掃描封包已收到，但量測摘要缺失"
        } else {
            "本次掃描沒有可配對的量測摘要"
        }

    fun orderStatusLines(
        watchStatusLine: String,
        detailLines: List<String>,
        prioritizeWatchStatus: Boolean
    ): List<String> = if (prioritizeWatchStatus) {
        listOf(watchStatusLine) + detailLines
    } else {
        detailLines + watchStatusLine
    }
}
