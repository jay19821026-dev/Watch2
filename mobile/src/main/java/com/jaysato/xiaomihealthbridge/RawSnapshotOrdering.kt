package com.jaysato.xiaomihealthbridge

object RawSnapshotOrdering {
    fun shouldAccept(
        incomingSession: Long,
        incomingTime: Long,
        incomingSequence: Long,
        incomingFinal: Boolean,
        previousSession: Long,
        previousTime: Long,
        previousSequence: Long,
        previousFinal: Boolean
    ): Boolean = rejectionReason(
        incomingSession, incomingTime, incomingSequence, incomingFinal,
        previousSession, previousTime, previousSequence, previousFinal
    ) == null

    fun rejectionReason(
        incomingSession: Long,
        incomingTime: Long,
        incomingSequence: Long,
        incomingFinal: Boolean,
        previousSession: Long,
        previousTime: Long,
        previousSequence: Long,
        previousFinal: Boolean,
        incomingSessionElapsedNs: Long = 0L,
        previousSessionElapsedNs: Long = 0L,
        incomingSuiteId: String = "",
        previousSuiteId: String = "",
        latestSuiteId: String = ""
    ): String? = when {
        incomingSuiteId.isNotBlank() && latestSuiteId.isNotBlank() && incomingSuiteId != latestSuiteId -> "suite_mismatch"
        incomingSession < previousSession -> "older_session"
        incomingSession > previousSession -> null
        isNewMeasurementSession(incomingSession, incomingSuiteId, previousSession, previousSuiteId, latestSuiteId,
            incomingSessionElapsedNs, previousSessionElapsedNs) -> null
        incomingTime < previousTime -> "older_timestamp"
        incomingTime > previousTime -> null
        previousFinal && !incomingFinal -> "preview_after_final"
        incomingSequence > 0L && previousSequence > 0L && incomingSequence < previousSequence -> "older_sequence"
        incomingSequence > 0L && previousSequence > 0L && incomingSequence == previousSequence -> "duplicate_sequence"
        incomingSequence > 0L && previousSequence > 0L && incomingSequence > previousSequence -> null
        previousFinal && incomingFinal -> "same_timestamp_sequence_unavailable"
        else -> null
    }

    fun isNewMeasurementSession(
        incomingSession: Long,
        incomingSuiteId: String,
        previousSession: Long,
        previousSuiteId: String,
        latestSuiteId: String,
        incomingSessionElapsedNs: Long = 0L,
        previousSessionElapsedNs: Long = 0L
    ): Boolean = incomingSession > previousSession || (
        incomingSession == previousSession && incomingSuiteId.isNotBlank() &&
            incomingSuiteId != previousSuiteId && latestSuiteId.isNotBlank() &&
            incomingSuiteId == latestSuiteId
        ) || (
            incomingSession == previousSession && incomingSessionElapsedNs > 0L &&
                previousSessionElapsedNs > 0L && incomingSessionElapsedNs > previousSessionElapsedNs
        )
}
