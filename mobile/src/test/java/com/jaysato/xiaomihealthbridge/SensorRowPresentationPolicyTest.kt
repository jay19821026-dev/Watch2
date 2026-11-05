package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorRowPresentationPolicyTest {
    @Test
    fun missingSummaryDoesNotReusePersistedStateFromAnOlderRow() {
        assertEquals(
            SensorDisplayState.NOT_MEASURED,
            SensorRowPresentationPolicy.displayState(null, SensorDisplayState.STALE)
        )
        assertEquals(
            SensorDisplayState.NOT_MEASURED,
            SensorRowPresentationPolicy.displayState(null, SensorDisplayState.AVAILABLE)
        )
        assertEquals(
            SensorDisplayState.NOT_MEASURED,
            SensorRowPresentationPolicy.displayState("   ", SensorDisplayState.AVAILABLE)
        )
        assertEquals(
            SensorDisplayState.AVAILABLE,
            SensorRowPresentationPolicy.displayState("summary", SensorDisplayState.AVAILABLE)
        )
        assertEquals(
            SensorDisplayState.CONDITION_NOT_MET,
            SensorRowPresentationPolicy.displayState("stale summary", SensorDisplayState.STALE, skippedCharging = true)
        )
    }

    @Test
    fun distinguishesReceivedSnapshotWithMissingSummaryFromUnmatchedScan() {
        assertEquals(
            "本次掃描封包已收到，但量測摘要缺失",
            SensorRowPresentationPolicy.missingSummaryMessage(SensorAcquisitionDiagnosticState.SNAPSHOT_RECEIVED)
        )
        assertEquals(
            "本次掃描沒有可配對的量測摘要",
            SensorRowPresentationPolicy.missingSummaryMessage(SensorAcquisitionDiagnosticState.DIFFERENT_SESSION)
        )
    }

    @Test
    fun chargingSkipIsPresentedBeforeDownstreamMetricStatus() {
        val watchStatus = "手錶原始狀態 skipped_charging：貼腕量測略過"
        val phoneStatus = listOf("未計算生理數值", "目前讀值為空")

        assertEquals(
            listOf(watchStatus, "未計算生理數值", "目前讀值為空"),
            SensorRowPresentationPolicy.orderStatusLines(watchStatus, phoneStatus, prioritizeWatchStatus = true)
        )
        assertEquals(
            phoneStatus + watchStatus,
            SensorRowPresentationPolicy.orderStatusLines(watchStatus, phoneStatus, prioritizeWatchStatus = false)
        )
    }
}
