package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertTrue
import org.junit.Test

class WearDataProcessorEventMetadataTest {
    @Test
    fun newSensorSessionClearsPreviousEventMetadataForEverySupportedStream() {
        listOf("spo2_raw", "mi_spo2", "body_temperature_raw", "thermometer", "bioz", "bioz_respiration")
            .forEach { kind ->
                val keys = RawWaveformFormulaPolicy.keysToClearForNewSession(kind)
                assertTrue("$kind timestamps", "${kind}_event_timestamps_ns" in keys)
                assertTrue("$kind timestamp validity", "${kind}_event_timestamp_validity" in keys)
                assertTrue("$kind callbacks", "${kind}_event_callback_elapsed_realtime_ns" in keys)
                assertTrue("$kind counts", "${kind}_event_sample_counts" in keys)
                assertTrue("$kind raw rows", "${kind}_event_raw_values_rows" in keys)
                assertTrue("$kind status", "${kind}_event_raw_values_status" in keys)
            }
    }

    @Test
    fun biozDerivedFieldsAreClearedWithEachNewCapture() {
        val keys = RawWaveformFormulaPolicy.keysToClearForNewSession("bioz")

        assertTrue("bioz_quality_score" in keys)
        assertTrue("bioz_confidence_score" in keys)
        assertTrue("bioz_waveform_median_position_index" in keys)
        assertTrue("bioz_relative_change_percent" in keys)
        assertTrue("bioz_stability_score" in keys)
        assertTrue("bioz_timeline_source" in keys)
        assertTrue("bioz_algorithm_version" in keys)
        assertTrue("bioz_display_waveform" in keys)
    }

    @Test
    fun newEcgSessionClearsPreviousDecodedWaveformAndStatus() {
        val keys = RawWaveformFormulaPolicy.keysToClearForNewSession("ecg")

        assertTrue("ecg_research_waveform" in keys)
        assertTrue("ecg_research_waveform_encoding" in keys)
        assertTrue("ecg_research_decode_status" in keys)
        assertTrue("ecg_research_decoded_value_count" in keys)
    }

    @Test
    fun eventMetadataMissingFromSnapshotIsClearedWithoutClearingPresentFields() {
        val missing = RawWaveformFormulaPolicy.keysToClearForSnapshot("bioz", setOf("event_timestamps_ns"))

        assertTrue("bioz_event_callback_elapsed_realtime_ns" in missing)
        assertTrue("bioz_event_timestamp_validity" in missing)
        assertTrue("bioz_event_sample_counts" in missing)
        assertTrue("bioz_event_raw_values_rows" in missing)
        assertTrue("bioz_event_raw_values_status" in missing)
        assertTrue("bioz_event_timestamps_ns" !in missing)

        val legacyEcg = RawWaveformFormulaPolicy.keysToClearForSnapshot("ecg", setOf("event_metadata_rows"))
        assertTrue("ecg_event_raw_values_rows" !in legacyEcg)

        val presentBioz = RawWaveformFormulaPolicy.keysToClearForSnapshot(
            "bioz",
            setOf(
                "event_timestamps_ns",
                "event_timestamp_validity",
                "event_callback_elapsed_realtime_ns",
                "event_sample_counts",
                "event_raw_values_rows",
                "event_raw_values_status"
            )
        )
        assertTrue("bioz_event_raw_values_rows" !in presentBioz)
        assertTrue("bioz_event_raw_values_status" !in presentBioz)
    }

    @Test
    fun ecgNewSessionClearsTimestampAndSampleCountSemantics() {
        val keys = RawWaveformFormulaPolicy.keysToClearForNewSession("ecg")

        assertTrue("ecg_event_timestamp_validity" in keys)
        assertTrue("ecg_event_timestamp_validity_semantics" in keys)
        assertTrue("ecg_event_sample_counts_semantics" in keys)
    }
}
