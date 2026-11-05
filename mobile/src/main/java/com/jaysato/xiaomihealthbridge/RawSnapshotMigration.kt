package com.jaysato.xiaomihealthbridge

internal object RawSnapshotMigration {
    interface Storage {
        fun isCommitted(): Boolean
        fun encryptedExists(): Boolean
        fun readLegacy(): Map<String, *>
        fun readEncrypted(): Map<String, *>
        fun writeEncrypted(values: Map<String, *>)
        fun commitMarker()
        fun deleteLegacy(): Boolean
    }

    data class Result(val values: Map<String, *>, val legacyCleanupComplete: Boolean)

    fun open(storage: Storage): Result {
        if (!storage.isCommitted()) {
            val source = RawSnapshotCodec.encode(storage.readLegacy())
            try {
                if (!storage.encryptedExists()) storage.writeEncrypted(RawSnapshotCodec.decode(source))
                val target = RawSnapshotCodec.encode(storage.readEncrypted())
                try {
                    check(source.contentEquals(target)) { "Raw snapshot migration verification failed." }
                } finally { target.fill(0) }
                val latest = RawSnapshotCodec.encode(storage.readLegacy())
                try {
                    check(source.contentEquals(latest)) { "Legacy snapshot changed during migration; evidence is preserved." }
                } finally { latest.fill(0) }
                storage.commitMarker()
                check(storage.isCommitted()) { "Raw snapshot migration marker persistence failed." }
            } finally { source.fill(0) }
        }
        check(storage.encryptedExists()) { "Committed raw snapshot missing; legacy evidence is preserved." }
        val values = storage.readEncrypted()
        val cleaned = storage.deleteLegacy()
        return Result(values, cleaned)
    }
}
