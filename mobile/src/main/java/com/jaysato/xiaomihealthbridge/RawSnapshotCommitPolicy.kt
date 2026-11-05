package com.jaysato.xiaomihealthbridge

internal object RawSnapshotCommitPolicy {
    fun canAcknowledgeDataItem(commitFailed: Boolean): Boolean = !commitFailed
}
