package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearDataProcessorFormulaIsolationTest {
    @Test
    fun ecgProfileMetadataIsRetainedOnlyForCurrentExternalReferenceMvWaveform() {
        assertEquals("profile-1", EcgCalibrationSnapshotPolicy.activeProfileId(
            "profile-1", "ecg_external_reference_mv"
        ))
        assertEquals(null, EcgCalibrationSnapshotPolicy.activeProfileId(
            "profile-1", "ecg_baseline_relative_unitless"
        ))
        assertEquals(null, EcgCalibrationSnapshotPolicy.activeProfileId(
            null, "ecg_external_reference_mv"
        ))
        assertEquals(null, EcgCalibrationSnapshotPolicy.activeProfileId(
            " ", "ecg_external_reference_mv"
        ))
    }

    @Test
    fun onlyExplicitPpgKindCanEnterOpticalAndRrHrvAlgorithms() {
        assertTrue(RawWaveformFormulaPolicy.mayRunOpticalAlgorithms("ppg"))
        listOf("ecg", "bioz", "lifeq_ott", "max_hr", "spo2").forEach { kind ->
            assertFalse("$kind must not use optical waveform formulas", RawWaveformFormulaPolicy.mayRunOpticalAlgorithms(kind))
        }
    }

    @Test
    fun ppgDerivedMetricsRequireCompleteTrustedMappedWornCapture() {
        val complete = List(64) { it.toFloat() }
        val trustedTimestamp = "android_sensor_event_elapsed_realtime"
        assertTrue(RawWaveformFormulaPolicy.mayDerivePpgMetrics(
            complete, 25.0, trustedTimestamp, channelMappingVerified = true, wearQualified = true
        ))
        assertFalse(RawWaveformFormulaPolicy.mayDerivePpgMetrics(
            complete.take(20) + 524287f + complete.drop(21), 25.0, trustedTimestamp, true, true
        ))
        assertFalse(RawWaveformFormulaPolicy.mayDerivePpgMetrics(
            complete, 0.0, trustedTimestamp, true, true
        ))
        assertFalse(RawWaveformFormulaPolicy.mayDerivePpgMetrics(
            complete, 25.0, "UNKNOWN", true, true
        ))
        assertFalse(RawWaveformFormulaPolicy.mayDerivePpgMetrics(
            complete, 25.0, trustedTimestamp, false, true
        ))
        assertFalse(RawWaveformFormulaPolicy.mayDerivePpgMetrics(
            complete, 25.0, trustedTimestamp, true, false
        ))
    }

    @Test
    fun newPpgSessionClearsOldDerivedValuesAndPreservesOtherKinds() {
        val oldValues = (RawWaveformFormulaPolicy.sessionDerivedKeys.map { "ppg_$it" } +
            listOf("ppg_source_waveform", "ppg_source_waveform_encoding", "ppg_event_sample_counts",
                "ppg_hal_sample_period_s", "ecg_formula_rmssd_ms", "bioz_formula_pulse_bpm"))
            .associateWith { 123f }

        val cleaned = oldValues - RawWaveformFormulaPolicy.keysToClearForNewSession("ppg").toSet()

        assertEquals(mapOf("ecg_formula_rmssd_ms" to 123f, "bioz_formula_pulse_bpm" to 123f), cleaned)
        RawWaveformFormulaPolicy.sessionDerivedKeys.forEach { key ->
            assertFalse(cleaned.containsKey("ppg_$key"))
        }
    }

    @Test
    fun newEcgSessionClearsWaveformEventAndHalMetadataButPreservesPpg() {
        val oldValues = listOf(
            "ecg_event_timestamps_ns", "ecg_event_callback_elapsed_realtime_ns",
            "ecg_event_sample_counts", "ecg_event_metadata_rows", "ecg_hal_sample_period_s",
            "ecg_hal_sample_jitter_s", "ecg_source_waveform", "ecg_source_waveform_encoding",
            "ecg_formula_rr_count", "ppg_event_sample_counts", "ppg_source_waveform"
        ).associateWith { 1L }

        val cleaned = oldValues - RawWaveformFormulaPolicy.keysToClearForNewSession("ecg").toSet()

        assertEquals(mapOf("ppg_event_sample_counts" to 1L, "ppg_source_waveform" to 1L), cleaned)
    }

    @Test
    fun newNonWaveformSessionClearsOnlyItsOwnGenericWaveformState() {
        val oldValues = mapOf(
            "accelerometer_source_waveform" to 1L,
            "accelerometer_source_waveform_encoding" to 2L,
            "ppg_source_waveform" to 3L
        )

        val cleaned = oldValues - RawWaveformFormulaPolicy.keysToClearForNewSession("accelerometer").toSet()

        assertEquals(mapOf("ppg_source_waveform" to 3L), cleaned)
    }

    @Test
    fun newSnapshotClearsOmittedFormulaValuesWithoutTouchingOtherSensorKinds() {
        val previous = (RawWaveformFormulaPolicy.sessionDerivedKeys.map { "pressure_$it" } +
            "ppg_formula_pulse_bpm").associateWith { 1f }
        val present = setOf("formula_pressure_hpa")

        val cleaned = previous - RawWaveformFormulaPolicy
            .keysToClearForSnapshot("pressure", present)
            .toSet()

        assertEquals(mapOf("pressure_formula_pressure_hpa" to 1f, "ppg_formula_pulse_bpm" to 1f), cleaned)
    }

    @Test
    fun legacyBloodPressureFieldsAreClearedEvenWhenOldWatchResendsThem() {
        val present = setOf("formula_sbp_mmhg", "formula_dbp_mmhg", "formula_bp_category")
        val removed = RawWaveformFormulaPolicy.keysToClearForSnapshot("ppg", present)

        assertTrue("ppg_formula_sbp_mmhg" in removed)
        assertTrue("ppg_formula_dbp_mmhg" in removed)
        assertTrue("ppg_formula_bp_category" in removed)
    }

    @Test
    fun unqualifiedHrvSourceClearsOnlyWhenItsMeasurementIsNotOlderThanStoredSource() {
        val previous = HrvResearchSnapshotPolicy.keysToClear.associateWith { "old" }

        assertTrue(HrvResearchSnapshotPolicy.shouldClearExisting("ppg", "ecg", true, false, 200L, 100L, 200L, 100L, 2_000L, 1_000L))
        assertEquals(emptyMap<String, String>(), previous - HrvResearchSnapshotPolicy.keysToClear.toSet())
        assertFalse(HrvResearchSnapshotPolicy.shouldClearExisting("ppg", "ecg", true, false, 90L, 100L, 100L, 100L, 1_000L, 1_000L))
        assertFalse(HrvResearchSnapshotPolicy.shouldClearExisting("ppg", "ecg", true, false, 100L, 100L, 100L, 100L, 1_000L, 1_000L))
        assertFalse(HrvResearchSnapshotPolicy.shouldClearExisting("ppg", "ecg", true, true, 200L, 100L, 200L, 100L, 2_000L, 1_000L))
        assertTrue(HrvResearchSnapshotPolicy.shouldClearExisting("ecg", "ecg", true, false, 110L, 100L, 90L, 80L, 900L, 800L))
        assertTrue(HrvResearchSnapshotPolicy.shouldClearExisting("ecg", "ecg", true, false, 100L, 100L, 90L, 90L, 900L, 900L))
        assertFalse(HrvResearchSnapshotPolicy.shouldClearExisting("ppg", "ecg", true, false, 100L, 100L, 99L, 100L, 900L, 1_000L))
        assertTrue(HrvResearchSnapshotPolicy.shouldClearExisting("ppg", "", true, false, 0L, 0L, 0L, 0L, 0L, 0L))
        assertFalse(HrvResearchSnapshotPolicy.shouldClearExisting("accelerometer", "ecg", true, false, 200L, 100L, 200L, 100L, 2_000L, 1_000L))
    }

    @Test
    fun qualifiedHrvCandidateCannotReplaceNewerSnapshotWithDelayedData() {
        assertTrue(HrvResearchSnapshotPolicy.shouldAcceptCandidate("ppg", "ecg", 200L, 100L, 200L, 100L, 2_000L, 1_000L))
        assertTrue(HrvResearchSnapshotPolicy.shouldAcceptCandidate("ppg", "ecg", 100L, 100L, 101L, 100L, 2_000L, 1_000L))
        assertFalse(HrvResearchSnapshotPolicy.shouldAcceptCandidate("ecg", "ecg", 90L, 100L, 99L, 100L, 900L, 1_000L))
        assertFalse(HrvResearchSnapshotPolicy.shouldAcceptCandidate("ecg", "ppg", 100L, 100L, 100L, 101L, 1_000L, 2_000L))
        assertTrue(HrvResearchSnapshotPolicy.shouldAcceptCandidate("ecg", "ecg", 100L, 100L, 100L, 100L, 1_000L, 1_000L))
        assertFalse(HrvResearchSnapshotPolicy.shouldAcceptCandidate("accelerometer", "ecg", 200L, 100L, 200L, 100L, 2_000L, 1_000L))
    }
}
