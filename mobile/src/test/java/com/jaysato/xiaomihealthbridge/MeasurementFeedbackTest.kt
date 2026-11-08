package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementFeedbackTest {
    @Test
    fun failureStatesAreNotPresentedAsSuccessfulMeasurements() {
        listOf("missing", "no_events", "not_measured", "skipped_charging").forEach { status ->
            val result = MeasurementFeedbackFactory.create(status, 0)
            assertFalse(result.receivedData)
            assertEquals("這次沒有可顯示的量測值", result.headline)
            assertTrue(result.guidance.isNotBlank())
        }
    }

    @Test
    fun unscaledOpticalFramesAreDescribedAsResearchOnly() {
        val result = MeasurementFeedbackFactory.create("unscaled_optical", 160)
        assertTrue(result.receivedData)
        assertEquals("已收到研究訊號", result.headline)
        assertTrue(result.guidance.contains("不會把它當成醫療讀值"))
    }

    @Test
    fun offWristRawEventsAreReportedSeparatelyFromWearQualification() {
        val offWrist = MeasurementFeedbackFactory.create("off_wrist", 10)
        assertTrue(offWrist.receivedData)
        assertEquals("有資料，但偵測為離腕", offWrist.headline)
        assertTrue(offWrist.guidance.contains("戴緊"))
        assertTrue(MeasurementFeedbackFactory.create("skipped_charging", 0).guidance.contains("拔除充電器"))
    }

    @Test
    fun invalidOpticalSignalIsReportedReceivedButNotAsUsableData() {
        val result = MeasurementFeedbackFactory.create("invalid_optical", 200)
        assertTrue(result.receivedData)
        assertEquals("光學訊號品質不足", result.headline)
    }

    @Test
    fun derivedEstimatesAreNeverPresentedAsSensorMeasurements() {
        val result = MeasurementFeedbackFactory.create("derived_estimate", 1)
        assertFalse(result.receivedData)
        assertEquals("僅有推估資料", result.headline)
        assertTrue(result.guidance.contains("不是此感測器的實測"))
    }
}
