package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PpgArchiveInputPolicyTest {
    @Test fun countsDoNotWrapIntoInventedValidValues() {
        assertEquals(0, PpgArchiveInputPolicy.eventSampleCount(0))
        assertEquals(Int.MAX_VALUE, PpgArchiveInputPolicy.eventSampleCount(Int.MAX_VALUE.toLong()))
        listOf(-1L, Int.MAX_VALUE.toLong() + 1, 4294967297L, Long.MAX_VALUE).forEach {
            assertTrue(runCatching { PpgArchiveInputPolicy.eventSampleCount(it) }.isFailure)
        }
    }

    @Test fun metadataIsBoundedWithoutTruncation() {
        PpgArchiveInputPolicy.validateMetadata(null, null)
        PpgArchiveInputPolicy.validateMetadata("a".repeat(256), "b".repeat(64))
        assertTrue(runCatching { PpgArchiveInputPolicy.validateMetadata("a".repeat(257), null) }.isFailure)
        assertTrue(runCatching { PpgArchiveInputPolicy.validateMetadata(null, "b".repeat(65)) }.isFailure)
    }

    @Test fun producerAlignedPpgPointsKeepSourceAndCallbackTimesSeparate() {
        val mapped = PpgArchiveInputPolicy.mapAlignedPpgPreviewTimes(
            valueCount = 3,
            eventTimestamps = listOf(100L, 0L, 300L),
            eventTimestampValidity = listOf(1, 0, 1),
            callbackTimestamps = listOf(110L, 210L, 310L)
        )

        assertEquals(0, mapped[0].eventIndex)
        assertEquals(100L, mapped[0].sourceTimestampNanos)
        assertEquals("UNKNOWN", mapped[1].timestampSource)
        assertEquals(210L, mapped[1].callbackTimestampNanos)
        assertEquals(300L, mapped[2].sourceTimestampNanos)
    }

    @Test fun duplicateAndRegressingPpgTimesAreUnknownUntilMonotonicTimeResumes() {
        val mapped = PpgArchiveInputPolicy.mapAlignedPpgPreviewTimes(
            valueCount = 5,
            eventTimestamps = listOf(100L, 200L, 200L, 150L, 300L),
            eventTimestampValidity = listOf(1, 1, 1, 1, 1),
            callbackTimestamps = listOf(110L, 210L, 220L, 230L, 310L)
        )

        assertEquals(100L, mapped[0].sourceTimestampNanos)
        assertEquals(200L, mapped[1].sourceTimestampNanos)
        assertEquals("UNKNOWN", mapped[2].timestampSource)
        assertEquals("UNKNOWN", mapped[3].timestampSource)
        assertEquals(300L, mapped[4].sourceTimestampNanos)
        assertEquals(220L, mapped[2].callbackTimestampNanos)
    }

    @Test fun ppgTimesAreNotAttachedWhenPreviewCannotAlignOneToOne() {
        val mapped = PpgArchiveInputPolicy.mapAlignedPpgPreviewTimes(
            valueCount = 2,
            eventTimestamps = listOf(100L, 200L, 300L),
            eventTimestampValidity = listOf(1, 1, 1),
            callbackTimestamps = listOf(110L, 210L, 310L)
        )

        assertTrue(mapped.all { it.timestampSource == "UNKNOWN" && it.eventIndex == -1 })
    }

    @Test fun alignedTimestampsWithoutValidityFlagsAreRetainedAsUnverified() {
        val mapped = PpgArchiveInputPolicy.mapAlignedPpgPreviewTimes(
            valueCount = 2,
            eventTimestamps = listOf(100L, 200L),
            eventTimestampValidity = emptyList(),
            callbackTimestamps = emptyList()
        )

        assertEquals(100L, mapped[0].sourceTimestampNanos)
        assertEquals("SENSOR_EVENT_UNVERIFIED_CLOCK", mapped[0].timestampSource)
    }

    @Test fun temperatureTimesMapOnlyWhenEachEventHasExactlyOneAlignedSample() {
        val mapped = PpgArchiveInputPolicy.mapSingleSamplePerEventTimes(
            valueCount = 2,
            eventTimestamps = listOf(100L, 200L),
            eventTimestampValidity = listOf(1, 1),
            callbackTimestamps = listOf(110L, 210L),
            eventSampleCounts = listOf(1, 1)
        )

        assertEquals(100L, mapped[0].sourceTimestampNanos)
        assertEquals("SENSOR_EVENT_UNVERIFIED_CLOCK", mapped[0].timestampSource)
        assertEquals(210L, mapped[1].callbackTimestampNanos)
    }

    @Test fun duplicateTemperatureTimesDoNotBecomeMeasurementTimes() {
        val mapped = PpgArchiveInputPolicy.mapSingleSamplePerEventTimes(
            valueCount = 3,
            eventTimestamps = listOf(100L, 100L, 200L),
            eventTimestampValidity = listOf(1, 1, 1),
            callbackTimestamps = listOf(110L, 120L, 210L),
            eventSampleCounts = listOf(1, 1, 1)
        )

        assertEquals(100L, mapped[0].sourceTimestampNanos)
        assertEquals("UNKNOWN", mapped[1].timestampSource)
        assertEquals(200L, mapped[2].sourceTimestampNanos)
    }

    @Test fun alignedTemperatureTimestampsWithoutValidityFlagsAreRetainedAsUnverified() {
        val mapped = PpgArchiveInputPolicy.mapSingleSamplePerEventTimes(
            valueCount = 2,
            eventTimestamps = listOf(100L, 200L),
            eventTimestampValidity = emptyList(),
            callbackTimestamps = emptyList(),
            eventSampleCounts = listOf(1, 1)
        )

        assertEquals(200L, mapped[1].sourceTimestampNanos)
        assertEquals("SENSOR_EVENT_UNVERIFIED_CLOCK", mapped[1].timestampSource)
    }

    @Test fun temperatureTimestampsRemainUnknownWhenAnyAlignmentEvidenceIsMissing() {
        val mapped = PpgArchiveInputPolicy.mapSingleSamplePerEventTimes(
            valueCount = 2,
            eventTimestamps = listOf(100L, 200L),
            eventTimestampValidity = listOf(1, 1),
            callbackTimestamps = listOf(110L, 210L),
            eventSampleCounts = listOf(1, 2)
        )

        assertTrue(mapped.all { it.timestampSource == "UNKNOWN" && it.eventIndex == -1 })
    }
}
