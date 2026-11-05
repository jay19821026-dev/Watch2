package com.jaysato.xiaomihealthbridge

internal enum class RawSnapshotRejectionCategory {
    ORDERING,
    PAYLOAD,
    DUPLICATE;

    companion object {
        fun from(reason: String): RawSnapshotRejectionCategory = when {
            reason == "duplicate_sequence" -> DUPLICATE
            reason == "duplicate_or_older_heart_rate_sample" -> DUPLICATE
            reason == "same_timestamp" || reason == "feature_already_committed_same_timestamp" -> DUPLICATE
            reason.startsWith("oversized_") || reason.startsWith("unsupported_payload_") || reason == "mismatched_event_metadata" -> PAYLOAD
            else -> ORDERING
        }
    }
}
