package com.jaysato.xiaomihealthbridge

internal object RawSnapshotDiagnosticsPolicy {
    private val failedCommitKinds = mutableSetOf<String>()

    fun shouldRecord(receiptLedger: String, receiptId: String): Boolean =
        !RawSnapshotReceiptLedger.contains(receiptLedger, receiptId)

    @Synchronized
    fun needsDurabilityRetry(kind: String): Boolean = kind in failedCommitKinds

    @Synchronized
    fun recordCommitResult(kind: String, succeeded: Boolean) {
        if (succeeded) failedCommitKinds.remove(kind) else failedCommitKinds.add(kind)
    }

    fun nextReceivedCount(
        incomingSession: Long,
        storedSession: Long,
        storedCount: Int,
        incomingSessionId: String = "",
        storedSessionId: String = "",
        incomingSampleTime: Long = 0L,
        storedSampleTime: Long = 0L
    ): Int? = when {
        incomingSession < storedSession -> null
        incomingSession > storedSession -> 1
        incomingSessionId.isNotBlank() && storedSessionId.isNotBlank() && incomingSessionId != storedSessionId ->
            if (incomingSampleTime > storedSampleTime && incomingSampleTime > 0L) 1 else null
        storedCount == Int.MAX_VALUE -> storedCount
        else -> storedCount + 1
    }
}
