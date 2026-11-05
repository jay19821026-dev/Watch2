package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PpgMotionPreviewResearchTest {
    private val sessionId = "ppg-session"
    private val ppgRows = (0 until 10).map { index ->
        RawSensorSampleEntity(
            sessionId = sessionId,
            sampleIndex = index,
            rawEncoding = index.toString(),
            sourceTimestampNanos = 1_000_000_000L + index * 20_000_000L,
            callbackTimestampNanos = null,
            timestampSource = "SENSOR_EVENT_UNVERIFIED_CLOCK",
            eventIndex = index,
            channelIndex = -1
        )
    }
    private val timestamps = (0 until 10).map { 1_000_000_000L + it * 20_000_000L }

    @Test
    fun alignedSameSessionEventsProduceActivityIndexNotArtifactScore() {
        val report = PpgMotionPreviewResearch.analyze(
            sessionId = sessionId,
            ppgRows = ppgRows,
            previewPointEventTimestampsNanos = timestamps,
            previewPointTimestampValidity = List(10) { 1 },
            ppgClockDomain = PpgMotionPreviewResearch.CLOCK_DOMAIN,
            motionClockDomain = PpgMotionPreviewResearch.CLOCK_DOMAIN,
            sameSession = true,
            sameMonotonicClock = true,
            maximumPairSkewNanos = 1_000_000L,
            captureStatus = "captured",
            arrayAlignmentValid = true,
            motionTimestampsNanos = timestamps,
            motionX = List(10) { 0.0 },
            motionY = List(10) { 0.0 },
            motionZ = List(10) { 9.80665 }
        )

        assertEquals("ALIGNED_MOTION_EVIDENCE_ONLY", report.status)
        assertEquals(10, report.evidence?.pairedObservationCount)
        assertEquals(0.0, report.evidence?.motionActivityIndex!!, 1e-12)
        assertTrue(report.evidence?.algorithmVersion!!.startsWith("ppg-motion-evidence-"))
    }

    @Test
    fun callbackOnlyOrDifferentClockDomainCannotProduceAlignedIndex() {
        val report = PpgMotionPreviewResearch.analyze(
            sessionId = sessionId,
            ppgRows = ppgRows,
            previewPointEventTimestampsNanos = timestamps,
            previewPointTimestampValidity = List(10) { 1 },
            ppgClockDomain = "callback_receipt_time",
            motionClockDomain = PpgMotionPreviewResearch.CLOCK_DOMAIN,
            sameSession = true,
            sameMonotonicClock = false,
            maximumPairSkewNanos = 1_000_000L,
            captureStatus = "captured",
            arrayAlignmentValid = true,
            motionTimestampsNanos = timestamps,
            motionX = List(10) { 0.0 },
            motionY = List(10) { 0.0 },
            motionZ = List(10) { 9.80665 }
        )

        assertEquals("COMMON_MONOTONIC_CLOCK_UNCONFIRMED", report.status)
        assertNull(report.evidence)
    }

    @Test
    fun missingSensorTimestampCannotBeFilledFromCallbackTime() {
        val report = PpgMotionPreviewResearch.analyze(
            sessionId = sessionId,
            ppgRows = ppgRows.mapIndexed { index, row ->
                if (index == 3) row.copy(
                    sourceTimestampNanos = null,
                    callbackTimestampNanos = timestamps[index],
                    timestampSource = "UNKNOWN"
                ) else row
            },
            previewPointEventTimestampsNanos = timestamps,
            previewPointTimestampValidity = List(10) { if (it == 3) 0 else 1 },
            ppgClockDomain = PpgMotionPreviewResearch.CLOCK_DOMAIN,
            motionClockDomain = PpgMotionPreviewResearch.CLOCK_DOMAIN,
            sameSession = true,
            sameMonotonicClock = true,
            maximumPairSkewNanos = 1_000_000L,
            captureStatus = "captured",
            arrayAlignmentValid = true,
            motionTimestampsNanos = timestamps,
            motionX = List(10) { 0.0 },
            motionY = List(10) { 0.0 },
            motionZ = List(10) { 9.80665 }
        )

        assertEquals("PPG_SOURCE_TIMESTAMPS_UNAVAILABLE", report.status)
        assertNull(report.evidence)
    }

    @Test
    fun inconsistentStoredRowAndProducerTimestampMappingIsRejected() {
        val report = PpgMotionPreviewResearch.analyze(
            sessionId = sessionId,
            ppgRows = ppgRows.mapIndexed { index, row ->
                if (index == 2) row.copy(sourceTimestampNanos = row.sourceTimestampNanos!! + 1L) else row
            },
            previewPointEventTimestampsNanos = timestamps,
            previewPointTimestampValidity = List(10) { 1 },
            ppgClockDomain = PpgMotionPreviewResearch.CLOCK_DOMAIN,
            motionClockDomain = PpgMotionPreviewResearch.CLOCK_DOMAIN,
            sameSession = true,
            sameMonotonicClock = true,
            maximumPairSkewNanos = 1_000_000L,
            captureStatus = "captured",
            arrayAlignmentValid = true,
            motionTimestampsNanos = timestamps,
            motionX = List(10) { 0.0 },
            motionY = List(10) { 0.0 },
            motionZ = List(10) { 9.80665 }
        )

        assertEquals("PPG_TIMESTAMP_MAPPING_MISMATCH", report.status)
        assertNull(report.evidence)
    }
}
