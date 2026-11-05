package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawSensorPayloadPolicyTest {
    @Test
    fun rawValueCountIsNotInflatedByPreviewOrCalibrationWaveform() {
        assertEquals(32, RawSensorPayloadPolicy.biomedicalSampleCount(32, 0))
        assertEquals(64, RawSensorPayloadPolicy.biomedicalSampleCount(64, 80))
        assertEquals(0, RawSensorPayloadPolicy.biomedicalSampleCount(0, 512))
        assertEquals(0, RawSensorPayloadPolicy.biomedicalSampleCount(-3, -1))
    }

    @Test
    fun rawCountIsIndependentFromPreviewCountAndMissingLegacyCountStaysUnknown() {
        assertEquals(512, RawSensorPayloadPolicy.previewPointCount(512, 5_120))
        assertEquals(0, RawSensorPayloadPolicy.previewPointCount(0, 512))
        val legacyCount = RawSensorPayloadPolicy.sourceValueCount(contractVersion = 0, rawValueCount = null)
        assertNull(legacyCount)
        assertEquals(512, RawSensorPayloadPolicy.storedDisplayCount(legacyCount, legacySamples = 512))
        assertEquals(17, RawSensorPayloadPolicy.sourceValueCount(contractVersion = 2, rawValueCount = 17))
    }

    @Test
    fun versionedZeroRawCountDoesNotFallBackToLegacySamplesOrCallbacks() {
        val sourceCount = RawSensorPayloadPolicy.sourceValueCount(
            contractVersion = 2,
            rawValueCount = 0
        )

        assertEquals(0, sourceCount)
        assertEquals(0, RawSensorPayloadPolicy.storedDisplayCount(sourceCount, legacySamples = 8))
    }

    @Test
    fun legacyCountRemainsDisplayableButCannotBecomeVersionedRawCount() {
        val sourceCount = RawSensorPayloadPolicy.sourceValueCount(
            contractVersion = 1,
            rawValueCount = null
        )

        assertNull(sourceCount)
        assertEquals(512, RawSensorPayloadPolicy.storedDisplayCount(sourceCount, legacySamples = 512))
    }

    @Test
    fun zeroFiniteRawValuesCanStillRepresentReceivedEventsAndPreview() {
        assertEquals(0, RawSensorPayloadPolicy.sourceValueCount(2, 0))
        assertEquals(0, RawSensorPayloadPolicy.storedDisplayCount(0, 8))
        assertEquals(true, RawSensorPayloadPolicy.hasReceivedData(2, 0, 8, 5, 8))
        assertEquals(false, RawSensorPayloadPolicy.hasReceivedData(2, 0, 0, 0, 8))
        assertEquals(true, RawSensorPayloadPolicy.hasReceivedData(0, null, 0, 0, 8))
    }

    @Test
    fun versionedMissingRawCountRemainsUnknownInsteadOfBecomingZero() {
        assertNull(RawSensorPayloadPolicy.sourceValueCount(2, null))
        assertEquals(false, RawSensorPayloadPolicy.hasReceivedData(2, null, 0, 0, 512))
        assertEquals(true, RawSensorPayloadPolicy.hasReceivedData(2, null, 4, 0, 512))
        assertEquals(0, RawSensorPayloadPolicy.displayCount(true, 2, null, 512))
        assertEquals(null, RawSensorPayloadPolicy.exportSampleCount(true, 2, null, 512))
        assertEquals(512, RawSensorPayloadPolicy.exportSampleCount(true, 1, null, 512))
    }

    @Test
    fun unsupportedFutureContractVersionIsNotInterpretedAsVersionTwo() {
        assertNull(RawSensorPayloadPolicy.sourceValueCount(3, 24))
        assertEquals(0, RawSensorPayloadPolicy.displayCount(true, 3, 24, 24))
        assertNull(RawSensorPayloadPolicy.exportSampleCount(true, 3, 24, 24))
        assertEquals(false, RawSensorPayloadPolicy.hasReceivedData(3, 24, 24, 24, 24))
        assertEquals(24, RawSensorPayloadPolicy.displayCount(false, 3, null, 24))
    }

    @Test
    fun nonBiomedicalCountsKeepTheirOwnVersionIndependentPath() {
        assertEquals(12, RawSensorPayloadPolicy.displayCount(false, 2, null, 12))
        assertEquals(true, RawSensorPayloadPolicy.hasStatisticSampleCount(false, null, 12))
        assertEquals(0, RawSensorPayloadPolicy.displayCount(true, 2, 0, 12))
        assertEquals(false, RawSensorPayloadPolicy.hasStatisticSampleCount(true, 0, 0))
    }

    @Test
    fun sourceRawCountIsNotReplacedByPreviewLength() {
        val sourceCount = RawSensorPayloadPolicy.sourceValueCount(
            contractVersion = 2,
            rawValueCount = 128
        )

        assertEquals(128, RawSensorPayloadPolicy.storedDisplayCount(sourceCount, legacySamples = 512))
    }

    @Test
    fun acceptsMaximumSizedCompletePayload() {
        assertNull(
            RawSensorPayloadPolicy.rejectionReason(
                RawSensorPayloadPolicy.MAX_WAVEFORM_POINTS,
                RawSensorPayloadPolicy.MAX_LATEST_CSV_CHARS,
                RawSensorPayloadPolicy.MAX_ECG_EVENT_ROWS,
                RawSensorPayloadPolicy.MAX_ECG_EVENT_ROWS,
                RawSensorPayloadPolicy.MAX_ECG_METADATA_CHARS
            )
        )
    }

    @Test
    fun rejectsOversizedSerializedDataMapAndProducerRejectionEnvelope() {
        assertEquals(
            "oversized_serialized_data_map",
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 0, 0, 0,
                serializedDataMapBytes = com.jaysato.xiaomihealthbridge.engine.SerializedSensorPayloadPolicy.MAX_DATA_MAP_BYTES + 1
            )
        )
        assertEquals(
            "oversized_serialized_data_map",
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 0, 0, 0,
                serializedDataMapBytes = 128,
                declaredPayloadRejection = "oversized_serialized_data_map"
            )
        )
        assertNull(
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 0, 0, 0,
                serializedDataMapBytes = com.jaysato.xiaomihealthbridge.engine.SerializedSensorPayloadPolicy.MAX_DATA_MAP_BYTES
            )
        )
        assertEquals(
            "unsupported_payload_rejection_marker",
            RawSensorPayloadPolicy.rejectionReason(
                128, 0, 0, 0, 0,
                declaredPayloadRejection = "arbitrary_marker"
            )
        )
    }

    @Test
    fun rejectsOversizedWaveformAsAWhole() {
        assertEquals(
            "oversized_waveform",
            RawSensorPayloadPolicy.rejectionReason(
                RawSensorPayloadPolicy.MAX_WAVEFORM_POINTS + 1, 0, 0, 0, 0
            )
        )
    }

    @Test
    fun rejectsOversizedLatestCsvAndEventMetadata() {
        assertEquals(
            "oversized_latest",
            RawSensorPayloadPolicy.rejectionReason(0, RawSensorPayloadPolicy.MAX_LATEST_CSV_CHARS + 1, 0, 0, 0)
        )
        assertEquals(
            "oversized_event_metadata",
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 0, 0, RawSensorPayloadPolicy.MAX_ECG_METADATA_CHARS + 1
            )
        )
    }

    @Test
    fun rejectsOversizedOrUnpairedEcgEventArrays() {
        assertEquals(
            "oversized_event_timestamps",
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, RawSensorPayloadPolicy.MAX_ECG_EVENT_ROWS + 1, 0, 0
            )
        )
        assertEquals(
            "mismatched_event_metadata",
            RawSensorPayloadPolicy.rejectionReason(0, 0, 2, 1, 0)
        )
    }

    @Test
    fun boundsCallbackReceiptTimingWithoutRequiringItForLegacyPayloads() {
        assertNull(RawSensorPayloadPolicy.rejectionReason(0, 0, 0, 0, 0))
        assertNull(
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 0, 0, 0, RawSensorPayloadPolicy.MAX_ECG_EVENT_ROWS
            )
        )
        assertEquals(
            "oversized_event_callback_timestamps",
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 0, 0, 0, RawSensorPayloadPolicy.MAX_ECG_EVENT_ROWS + 1
            )
        )
    }

    @Test
    fun rejectsMisalignedCallbackAndCompleteRawEventRows() {
        assertEquals(
            "mismatched_event_callback_timestamps",
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 2, 2, 0,
                eventCallbackTimestampCount = 1,
                eventCallbackMetadataPresent = true
            )
        )
        assertEquals(
            "mismatched_event_raw_value_rows",
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 2, 2, 12,
                eventCallbackTimestampCount = 2,
                eventCallbackMetadataPresent = true,
                eventRawValueRowCount = 1,
                eventRawValuesStatus = "complete_for_observed_events"
            )
        )
        assertNull(
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 2, 2, 0,
                eventCallbackTimestampCount = 2,
                eventCallbackMetadataPresent = true,
                eventRawValueRowCount = 0,
                eventRawValuesStatus = "rejected_size_limit"
            )
        )
    }

    @Test
    fun timestampValidityFlagsMustAlignWhenTheProducerProvidesThem() {
        assertEquals(
            "mismatched_event_timestamp_validity",
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 2, 2, 0,
                eventTimestampValidityCount = 1,
                eventTimestampValidityMetadataPresent = true
            )
        )
        assertNull(
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 2, 2, 0,
                eventTimestampValidityCount = 2,
                eventTimestampValidityMetadataPresent = true
            )
        )
        assertNull(RawSensorPayloadPolicy.rejectionReason(0, 0, 2, 2, 0))
        assertEquals(
            "invalid_event_timestamp_validity",
            RawSensorPayloadPolicy.rejectionReason(
                0, 0, 2, 2, 0,
                eventTimestampValidityCount = 2,
                eventTimestampValidityMetadataPresent = true,
                eventTimestampValidityValues = listOf(1L, 2L)
            )
        )
    }
}
