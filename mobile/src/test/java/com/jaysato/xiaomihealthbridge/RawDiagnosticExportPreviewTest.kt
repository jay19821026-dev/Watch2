package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawDiagnosticExportPreviewTest {
    @Test
    fun summarizesIncludedChannelsCountsAndMeasurementBounds() {
        val preview = RawDiagnosticExportPreview.summarize(
            listOf(
                RawDiagnosticExportPreview.Channel("PPG", 100L, 3, 2, 10, 6),
                RawDiagnosticExportPreview.Channel("SpO₂", 200L, 4, 1, 8, 0),
                RawDiagnosticExportPreview.Channel("空通道", 0L, 0, 0, 0, 0)
            )
        )

        assertEquals(listOf("PPG", "SpO₂"), preview.channels)
        assertEquals(2, preview.measuredChannelCount)
        assertEquals(7L, preview.storedSampleCount)
        assertEquals(3L, preview.previewPointCount)
        assertEquals(18L, preview.waveformPointCount)
        assertEquals(6L, preview.sourceWaveformPointCount)
        assertEquals(100L, preview.earliestMeasurementAt)
        assertEquals(200L, preview.latestMeasurementAt)
    }

    @Test
    fun missingMeasurementTimesRemainExplicit() {
        val preview = RawDiagnosticExportPreview.summarize(
            listOf(RawDiagnosticExportPreview.Channel("ECG", 0L, 1, 0, 0, 0))
        )

        assertEquals(1, preview.measuredChannelCount)
        assertNull(preview.earliestMeasurementAt)
        assertNull(preview.latestMeasurementAt)
    }
}
