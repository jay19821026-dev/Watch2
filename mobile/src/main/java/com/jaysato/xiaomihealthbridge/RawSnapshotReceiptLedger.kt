package com.jaysato.xiaomihealthbridge

internal object RawSnapshotReceiptLedger {
    private const val MAX_RECEIPTS = 512
    private const val MAX_PENDING_RECEIPTS = 512

    fun contains(encoded: String, receiptId: String): Boolean =
        receiptId.isNotBlank() && encoded.lineSequence().any { it == receiptId }

    fun requiresReplay(receipts: String, pending: String, receiptId: String): Boolean =
        contains(receipts, receiptId) || contains(pending, receiptId)

    fun shouldReplay(
        receipts: String,
        pending: String,
        receiptId: String,
        retryingCurrentSnapshot: Boolean,
        payloadRejected: Boolean
    ): Boolean = !retryingCurrentSnapshot && !payloadRejected && requiresReplay(receipts, pending, receiptId)

    fun append(encoded: String, receiptId: String): String {
        if (receiptId.isBlank() || contains(encoded, receiptId)) return encoded
        return (encoded.lineSequence().filter(String::isNotBlank).toList() + receiptId)
            .takeLast(MAX_RECEIPTS)
            .joinToString("\n")
    }

    fun remove(encoded: String, receiptId: String): String =
        encoded.lineSequence().filter { it.isNotBlank() && it != receiptId }.toList().joinToString("\n")

    fun pendingAfterRejectedReceipt(encoded: String, receiptId: String): String? =
        if (contains(encoded, receiptId)) remove(encoded, receiptId) else null

    fun appendPending(encoded: String, receiptId: String): String? {
        if (receiptId.isBlank()) return null
        if (contains(encoded, receiptId)) return encoded
        val pending = encoded.lineSequence().filter(String::isNotBlank).toList()
        if (pending.size >= MAX_PENDING_RECEIPTS) return null
        return (pending + receiptId).joinToString("\n")
    }

    fun reservePending(encoded: String, receiptId: String, isNewWork: Boolean): String? =
        if (isNewWork) appendPending(encoded, receiptId) else encoded
}

internal object DataItemDurabilityCommit {
    fun commitThenAcknowledge(
        commitCheckpoint: () -> Boolean,
        afterCommit: () -> Unit,
        acknowledgeDataItem: () -> Unit
    ): Boolean {
        if (!commitCheckpoint()) return false
        afterCommit()
        acknowledgeDataItem()
        return true
    }
}
