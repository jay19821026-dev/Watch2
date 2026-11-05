package com.jaysato.xiaomihealthbridge

internal class RawSnapshotEditorChanges {
    private var clearRequested = false
    private val additions = linkedMapOf<String, Any>()
    private val removals = linkedSetOf<String>()

    @Synchronized
    fun put(key: String, value: Any?) {
        if (value == null) {
            remove(key)
            return
        }
        val encoded = RawSnapshotCodec.encode(mapOf(key to value))
        try { additions[key] = RawSnapshotCodec.decode(encoded).getValue(key) }
        finally { encoded.fill(0) }
        removals.remove(key)
    }

    @Synchronized
    fun remove(key: String) {
        additions.remove(key)
        removals.add(key)
    }

    @Synchronized
    fun clear() {
        clearRequested = true
    }

    @Synchronized
    fun drain(): RawSnapshotTransactions.Update {
        val encoded = RawSnapshotCodec.encode(additions)
        return try {
            RawSnapshotTransactions.Update(clearRequested, removals.toSet(), RawSnapshotCodec.decode(encoded)).also {
                clearRequested = false
                additions.clear()
                removals.clear()
            }
        } finally { encoded.fill(0) }
    }
}
