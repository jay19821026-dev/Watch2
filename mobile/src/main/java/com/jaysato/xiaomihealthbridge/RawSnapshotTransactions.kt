package com.jaysato.xiaomihealthbridge

internal class RawSnapshotTransactions(
    private val readPersisted: () -> Map<String, *>,
    private val writePersisted: (Map<String, *>) -> Unit
) {
    data class Update(
        val clear: Boolean = false,
        val removals: Set<String> = emptySet(),
        val values: Map<String, *> = emptyMap<String, Any>()
    )

    @Synchronized
    fun read(): Map<String, Any> = copy(readPersisted())

    @Synchronized
    fun update(update: Update): Map<String, Any> {
        val additions = copy(update.values)
        val next = if (update.clear) linkedMapOf() else copy(readPersisted()).toMutableMap()
        update.removals.forEach(next::remove)
        next.putAll(additions)
        val expected = RawSnapshotCodec.encode(next)
        try {
            writePersisted(copy(next))
            val saved = copy(readPersisted())
            val actual = RawSnapshotCodec.encode(saved)
            try {
                check(expected.contentEquals(actual)) { "Snapshot update durability verification failed." }
            } finally { actual.fill(0) }
            return saved
        } finally { expected.fill(0) }
    }

    private fun copy(values: Map<String, *>): Map<String, Any> {
        val encoded = RawSnapshotCodec.encode(values)
        return try { RawSnapshotCodec.decode(encoded) } finally { encoded.fill(0) }
    }
}
