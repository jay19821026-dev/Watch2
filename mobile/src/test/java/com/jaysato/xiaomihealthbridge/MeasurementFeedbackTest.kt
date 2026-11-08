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
        assertEquals("這次沒有可顯示的讀值", result.headline)
        assertTrue(result.guidance.contains("原廠量測頁重測"))
    }

    @Test
    fun offWristRawEventsAreReportedSeparatelyFromWearQualification() {
        val offWrist = MeasurementFeedbackFactory.create("off_wrist", 10)
        assertTrue(offWrist.receivedData)
        assertEquals("手錶沒有戴好", offWrist.headline)
        assertTrue(offWrist.guidance.contains("戴緊"))
        assertTrue(MeasurementFeedbackFactory.create("skipped_charging", 0).guidance.contains("拔除充電器"))
    }

    @Test
    fun invalidOpticalSignalIsReportedReceivedButNotAsUsableData() {
        val result = MeasurementFeedbackFactory.create("invalid_optical", 200)
        assertTrue(result.receivedData)
        assertEquals("這次訊號不清楚", result.headline)
    }

    @Test
    fun derivedEstimatesAreNeverPresentedAsSensorMeasurements() {
        val result = MeasurementFeedbackFactory.create("derived_estimate", 1)
        assertFalse(result.receivedData)
        assertEquals("這次只有參考估算", result.headline)
        assertTrue(result.guidance.contains("並非這次直接量到"))
    }
}
