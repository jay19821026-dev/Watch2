package com.jaysato.xiaomihealthbridge

internal object RawSnapshotReceiptRetryState {
    private val failedReceiptIdsByKind = mutableMapOf<String, MutableSet<String>>()

    @Synchronized
    fun shouldRetry(kind: String, receiptId: String): Boolean =
        receiptId.isNotBlank() && receiptId in failedReceiptIdsByKind[kind].orEmpty()

    @Synchronized
    fun isCurrentFailedSnapshot(kind: String, receiptId: String, currentReceiptId: String?): Boolean =
        shouldRetry(kind, receiptId) && currentReceiptId == receiptId

    @Synchronized
    fun needsDurabilityFlushBeforeResolve(kind: String, receiptId: String, currentReceiptId: String?): Boolean =
        shouldRetry(kind, receiptId) && currentReceiptId != receiptId

    @Synchronized
    fun shouldApplyOrderingChecks(retryCurrentSnapshot: Boolean): Boolean = !retryCurrentSnapshot

    @Synchronized
    fun markCommitFailed(kind: String, receiptId: String) {
        if (kind.isNotBlank() && receiptId.isNotBlank()) {
            failedReceiptIdsByKind.getOrPut(kind) { mutableSetOf() }.add(receiptId)
        }
    }

    @Synchronized
    fun markResolved(kind: String, receiptId: String) {
        failedReceiptIdsByKind[kind]?.let { ids ->
            ids.remove(receiptId)
            if (ids.isEmpty()) failedReceiptIdsByKind.remove(kind)
        }
    }

    @Synchronized
    fun markKindPersisted(kind: String) {
        failedReceiptIdsByKind.remove(kind)
    }
}
