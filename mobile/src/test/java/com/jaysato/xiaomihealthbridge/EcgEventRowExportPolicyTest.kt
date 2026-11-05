package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgEventRowExportPolicyTest {
    @Test
    fun alignsCandidateValuesAndUnmappedFieldsByOriginalEventIndex() {
        val result = EcgEventRowExportPolicy.build(
            eventTimestampsNs = listOf(100L, 200L),
            callbackTimesNs = listOf(110L, 210L),
            sampleCounts = listOf(2, 1),
            metadataRows = listOf("x,y", "z"),
            candidateValues = listOf(1.0, 2.0, 3.0),
            sourceEncoding = "legacy_unverified"
        )

        assertEquals("aligned_legacy_split_unmapped_values", result.status)
        assertEquals(2, result.rows.size)
        assertEquals(listOf(1.0, 2.0), result.rows[0].candidateValues)
        assertEquals("x,y", result.rows[0].legacyRemainderCsv)
        assertEquals(210L, result.rows[1].callbackReceivedElapsedRealtimeNs)
        assertEquals(listOf(3.0), result.rows[1].candidateValues)
    }

    @Test
    fun rejectsMisalignedOrTruncatedSeriesInsteadOfReturningPartialRows() {
        val result = EcgEventRowExportPolicy.build(
            eventTimestampsNs = listOf(100L, 200L),
            callbackTimesNs = emptyList(),
            sampleCounts = listOf(2, 1),
            metadataRows = listOf("x,y"),
            candidateValues = listOf(1.0, 2.0, 3.0),
            sourceEncoding = "legacy_unverified"
        )

        assertEquals("rejected_row_alignment", result.status)
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun rejectsCountsThatCannotDescribeTheCandidateWaveform() {
        val result = EcgEventRowExportPolicy.build(
            eventTimestampsNs = listOf(100L),
            callbackTimesNs = emptyList(),
            sampleCounts = listOf(2),
            metadataRows = listOf("x"),
            candidateValues = listOf(1.0),
            sourceEncoding = "legacy_unverified"
        )

        assertEquals("rejected_candidate_count_mismatch", result.status)
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun distinguishesMalformedStoredDataFromNoEventRows() {
        val result = EcgEventRowExportPolicy.build(
            eventTimestampsNs = emptyList(),
            callbackTimesNs = emptyList(),
            sampleCounts = emptyList(),
            metadataRows = emptyList(),
            candidateValues = emptyList(),
            sourceEncoding = "legacy_unverified",
            parseFailed = true
        )

        assertEquals("rejected_parse_failure", result.status)
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun fullRawEventRowsNeverBecomeWaveformCandidates() {
        val result = EcgEventRowExportPolicy.build(
            eventTimestampsNs = listOf(100L),
            callbackTimesNs = listOf(101L),
            sampleCounts = listOf(0),
            metadataRows = listOf("1.0,2.0,3.0"),
            candidateValues = emptyList(),
            sourceEncoding = "hal_event_values_full_unmapped_preview",
            rawValuesStatus = "complete_for_observed_events"
        )

        assertEquals("aligned_full_raw_event_values_unmapped", result.status)
        assertTrue(result.rowIndexAligned)
        assertTrue(result.sensorTimestampsValidMonotonic)
        assertTrue(result.rows.single().candidateValues.isEmpty())
        assertEquals("1.0,2.0,3.0", result.rows.single().rawEventValuesCsv)
    }

    @Test
    fun preservesRawRowsButMarksInvalidOrOutOfOrderSensorTimestamps() {
        val result = EcgEventRowExportPolicy.build(
            eventTimestampsNs = listOf(200L, 100L),
            callbackTimesNs = emptyList(),
            sampleCounts = listOf(0, 0),
            metadataRows = listOf("first", "second"),
            candidateValues = emptyList(),
            sourceEncoding = "hal_event_values_full_unmapped_preview",
            rawValuesStatus = "complete_for_observed_events"
        )

        assertTrue(result.status.endsWith("_invalid_sensor_timestamps"))
        assertTrue(result.rowIndexAligned)
        assertTrue(!result.sensorTimestampsValidMonotonic)
        assertEquals(listOf("first", "second"), result.rows.map { it.rawEventValuesCsv })
    }

    @Test
    fun distinguishesRetainedIndexAlignedRowsFromInvalidSensorTimeAlignment() {
        val result = EcgRawValueAlignmentPolicy.evaluate(
            sourceEncoding = "hal_event_values_full_unmapped_preview",
            rawValuesStatus = "bounded_recent_events",
            rawRowCount = 2,
            eventTimestampsNs = listOf(0L, 0L)
        )

        assertTrue(result.valuesRetained)
        assertTrue(result.rowsIndexAligned)
        assertTrue(!result.sensorTimestampsValidMonotonic)
        assertTrue(!result.valuesTimeAligned)
    }

    @Test
    fun retainedRawRowsAreNotReportedAsIndexAlignedWhenRowCountsDiffer() {
        val result = EcgRawValueAlignmentPolicy.evaluate(
            sourceEncoding = "hal_event_values_full_unmapped_preview",
            rawValuesStatus = "bounded_recent_events",
            rawRowCount = 1,
            eventTimestampsNs = listOf(100L, 200L)
        )

        assertTrue(result.valuesRetained)
        assertTrue(!result.rowsIndexAligned)
        assertTrue(!result.valuesTimeAligned)
    }

    @Test
    fun fullRawRowsRejectWhenCaptureWasSizeLimited() {
        val result = EcgEventRowExportPolicy.build(
            eventTimestampsNs = listOf(100L),
            callbackTimesNs = emptyList(),
            sampleCounts = listOf(0),
            metadataRows = emptyList(),
            candidateValues = emptyList(),
            sourceEncoding = "hal_event_values_full_unmapped_preview",
            rawValuesStatus = "rejected_size_limit"
        )

        assertEquals("rejected_size_limit", result.status)
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun metadataCharacterLimitIncludesSerializedRowSeparatorsAndHonorsBoundary() {
        val rowCount = EcgEventRowExportPolicy.MAX_EVENT_ROWS
        val rowLengths = MutableList(rowCount) { 63 }.also { it[0] = 64 }
        val rowsAtLimit = rowLengths.map { "x".repeat(it) }
        val timestamps = (1..rowCount).map(Int::toLong)
        val counts = List(rowCount) { 0 }

        val accepted = EcgEventRowExportPolicy.build(
            eventTimestampsNs = timestamps,
            callbackTimesNs = emptyList(),
            sampleCounts = counts,
            metadataRows = rowsAtLimit,
            candidateValues = emptyList(),
            sourceEncoding = "legacy_unverified"
        )
        val rejected = EcgEventRowExportPolicy.build(
            eventTimestampsNs = timestamps,
            callbackTimesNs = emptyList(),
            sampleCounts = counts,
            metadataRows = listOf("x" + rowsAtLimit[0]) + rowsAtLimit.drop(1),
            candidateValues = emptyList(),
            sourceEncoding = "legacy_unverified"
        )

        assertEquals(EcgEventRowExportPolicy.MAX_METADATA_CHARS, rowsAtLimit.sumOf { it.length } + rowCount - 1)
        assertEquals("aligned_legacy_split_unmapped_values", accepted.status)
        assertEquals("rejected_size_limit", rejected.status)
    }
}
