package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class RawSnapshotRejectionCategoryTest {
    @Test
    fun separatesOrderingPayloadAndDuplicateRejections() {
        assertEquals(RawSnapshotRejectionCategory.ORDERING, RawSnapshotRejectionCategory.from("older_timestamp"))
        assertEquals(RawSnapshotRejectionCategory.ORDERING, RawSnapshotRejectionCategory.from("future_timestamp"))
        assertEquals(RawSnapshotRejectionCategory.DUPLICATE, RawSnapshotRejectionCategory.from("duplicate_sequence"))
        assertEquals(RawSnapshotRejectionCategory.ORDERING, RawSnapshotRejectionCategory.from("same_timestamp_sequence_unavailable"))
        assertEquals(RawSnapshotRejectionCategory.DUPLICATE, RawSnapshotRejectionCategory.from("same_timestamp"))
        assertEquals(
            RawSnapshotRejectionCategory.DUPLICATE,
            RawSnapshotRejectionCategory.from("feature_already_committed_same_timestamp")
        )
        assertEquals(RawSnapshotRejectionCategory.PAYLOAD, RawSnapshotRejectionCategory.from("oversized_waveform"))
        assertEquals(RawSnapshotRejectionCategory.PAYLOAD, RawSnapshotRejectionCategory.from("mismatched_event_metadata"))
        assertEquals(
            RawSnapshotRejectionCategory.DUPLICATE,
            RawSnapshotRejectionCategory.from("duplicate_or_older_heart_rate_sample")
        )
    }
}
