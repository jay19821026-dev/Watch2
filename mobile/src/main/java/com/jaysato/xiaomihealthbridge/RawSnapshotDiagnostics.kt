package com.jaysato.xiaomihealthbridge

internal object RawSnapshotDiagnostics {
    fun emptyMessage(query: String): String = when {
        query == "success" -> "尚無收到的感測器快照"
        query.startsWith("failed:") -> "原始快照讀取失敗，無法確認是否有資料"
        else -> "尚未完成原始快照查詢"
    }

    data class Channel(val kind: String, val timestamp: Long, val samples: Int, val status: String)

    fun channels(snapshot: Map<String, *>): List<Channel> =
        snapshot.keys.filter { it.endsWith("_time") }.sorted().mapNotNull { key ->
            val kind = key.removeSuffix("_time")
            if (kind.isBlank()) return@mapNotNull null
            Channel(
                kind,
                snapshot[key] as Long,
                if (snapshot.containsKey("${kind}_samples")) snapshot["${kind}_samples"] as Int else -1,
                if (snapshot.containsKey("${kind}_status")) snapshot["${kind}_status"] as String else "unknown"
            )
        }
}
