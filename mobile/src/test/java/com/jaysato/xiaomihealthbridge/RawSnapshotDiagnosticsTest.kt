package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSnapshotDiagnosticsTest {
    @Test fun failedAndUnqueriedSnapshotsAreNotReportedAsEmptyStorage() {
        assertEquals("尚無收到的感測器快照", RawSnapshotDiagnostics.emptyMessage("success"))
        assertEquals("原始快照讀取失敗，無法確認是否有資料", RawSnapshotDiagnostics.emptyMessage("failed:IOException"))
        assertEquals("尚未完成原始快照查詢", RawSnapshotDiagnostics.emptyMessage("not_run"))
    }

    @Test fun usesOnlySuppliedSnapshotAndPreservesZero() {
        val source = mutableMapOf<String, Any>("ppg_time" to 42L, "ppg_samples" to 0, "ppg_status" to "raw")
        val snapshot = source.toMap()
        source["ppg_samples"] = 10
        assertEquals(RawSnapshotDiagnostics.Channel("ppg", 42L, 0, "raw"), RawSnapshotDiagnostics.channels(snapshot).single())
    }

    @Test fun missingFieldsRemainUnknownAndMalformedValuesFailExplicitly() {
        assertEquals(RawSnapshotDiagnostics.Channel("ppg", 42L, -1, "unknown"),
            RawSnapshotDiagnostics.channels(mapOf("ppg_time" to 42L)).single())
        assertTrue(runCatching { RawSnapshotDiagnostics.channels(mapOf("ppg_time" to "42")) }.isFailure)
        assertTrue(runCatching { RawSnapshotDiagnostics.channels(mapOf("ppg_time" to 42L, "ppg_samples" to 1L)) }.isFailure)
    }
}
