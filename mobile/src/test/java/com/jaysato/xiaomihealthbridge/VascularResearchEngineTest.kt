package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class VascularResearchEngineTest {
    @Test fun usablePpgReportsSignalQualityWithoutCallingItVascularAge() {
        val result = VascularResearchEngine.fromPpg(84, 256, "research_optical")
        assertEquals(84, result.ppgQualityScore)
        assertNull(result.vascularAgeYears)
        assertNull(result.appNormalizedApgIndex)
        assertTrue(result.method.contains("PPG_CHANNEL_MAPPING_UNVERIFIED"))
        assertTrue(result.limitations.contains("指尖 PPG"))
        assertTrue(result.limitations.contains("心率與血壓"))
        assertTrue(result.limitations.contains("跨部位／裝置驗證"))
    }

    @Test fun qualityChangeCannotBecomeVascularAgeChange() {
        val first = VascularResearchEngine.fromPpg(80, 256, "valid_signal")
        val second = VascularResearchEngine.fromPpg(70, 256, "valid_signal")
        assertNull(first.vascularAgeYears)
        assertNull(second.vascularAgeYears)
        assertEquals("尚未建立可比較的個人基線", second.trend)
    }

    @Test fun insufficientPpgDoesNotCreateScore() {
        val result = VascularResearchEngine.fromPpg(90, 12, "has_data")
        assertNull(result.ppgQualityScore)
        assertNull(result.vascularAgeYears)
    }

    @Test fun historicalHasDataStatusDoesNotQualifyAsCurrentPpgQuality() {
        val result = VascularResearchEngine.fromPpg(90, 256, "has_data")
        assertNull(result.ppgQualityScore)
        assertNull(result.vascularAgeYears)
    }

    @Test fun invalidQualityValuesRemainUnscored() {
        assertNull(VascularResearchEngine.fromPpg(0, 256, "valid_signal").ppgQualityScore)
        assertNull(VascularResearchEngine.fromPpg(101, 256, "valid_signal").ppgQualityScore)
    }

    @Test fun sdppgIndexRequiresMappingWearClockAndEnoughSamples() {
        val waveform = syntheticPpg()
        val unmapped = VascularResearchEngine.fromPpg(
            90, 3_272, "valid_signal", waveform, 100f, false, true,
            "android_sensor_event_elapsed_realtime"
        )
        assertNull(unmapped.appNormalizedApgIndex)
        assertEquals("PPG_CHANNEL_MAPPING_UNVERIFIED", unmapped.reasonCode)

        val unknownClock = VascularResearchEngine.fromPpg(
            90, waveform.size, "valid_signal", waveform, 100f, true, true, "UNKNOWN"
        )
        assertNull(unknownClock.appNormalizedApgIndex)
        assertEquals("SENSOR_TIME_UNVERIFIED", unknownClock.reasonCode)
    }

    @Test fun sdppgIndexReportsSpecificQualityAndCaptureGates() {
        val waveform = syntheticPpg()
        val lowQuality = VascularResearchEngine.fromPpg(
            79, waveform.size, "valid_signal", waveform, 100f, true, true,
            "android_sensor_event_elapsed_realtime"
        )
        assertEquals("LOW_PPG_QUALITY", lowQuality.reasonCode)

        val notWorn = VascularResearchEngine.fromPpg(
            90, waveform.size, "valid_signal", waveform, 100f, true, false,
            "android_sensor_event_elapsed_realtime"
        )
        assertEquals("WEAR_NOT_CONFIRMED", notWorn.reasonCode)

        val unknownRate = VascularResearchEngine.fromPpg(
            90, waveform.size, "valid_signal", waveform, 0f, true, true,
            "android_sensor_event_elapsed_realtime"
        )
        assertEquals("SAMPLE_RATE_BELOW_RESEARCH_GATE", unknownRate.reasonCode)

        val shortCapture = VascularResearchEngine.fromPpg(
            90, 1_499, "valid_signal", waveform.take(1_499), 100f, true, true,
            "android_sensor_event_elapsed_realtime"
        )
        assertEquals("CAPTURE_TOO_SHORT", shortCapture.reasonCode)
    }

    @Test fun invalidOrOversizedWaveformsAreRejectedBeforeCaptureLengthGate() {
        val oversized = VascularResearchEngine.fromPpg(
            90, 64, "valid_signal", List(100_001) { 1f }, 100f, true, true,
            "android_sensor_event_elapsed_realtime"
        )
        assertEquals("INVALID_OR_OVERSIZED_WAVEFORM", oversized.reasonCode)

        val nonFinite = VascularResearchEngine.fromPpg(
            90, 64, "valid_signal", listOf(0f, Float.NaN), 100f, true, true,
            "android_sensor_event_elapsed_realtime"
        )
        assertEquals("INVALID_OR_OVERSIZED_WAVEFORM", nonFinite.reasonCode)
    }

    @Test fun opticalFillMarkerRejectsApgInsteadOfCompressingAcrossMissingSamples() {
        val waveform = syntheticPpg().toMutableList().apply { this[1_000] = 524287f }

        val result = VascularResearchEngine.fromPpg(
            90, waveform.size, "valid_signal", waveform, 100f, true, true,
            "android_sensor_event_elapsed_realtime"
        )

        assertNull(result.appNormalizedApgIndex)
        assertEquals("PPG_GAP_PRESENT", result.reasonCode)
        assertEquals(0, result.acceptedBeatCount)
    }

    @Test fun emptyDisplayCacheFallsBackToSavedPpgWaveform() {
        val waveform = "1.0,2.0,3.0"

        assertEquals(listOf(1f, 2f, 3f), PpgVascularInput.parseWaveform("", waveform))
        assertEquals(listOf(1f, 524287f, 3f), PpgVascularInput.parseWaveform("1.0,524287.0,3.0", waveform))
    }

    @Test fun flatPpgWaveformIsRejectedInsteadOfCreatingAResearchIndex() {
        val result = VascularResearchEngine.fromPpg(
            90, 2_000, "valid_signal", List(2_000) { 1f }, 100f, true, true,
            "android_sensor_event_elapsed_realtime"
        )
        assertNull(result.appNormalizedApgIndex)
        assertEquals("FLATLINE", result.reasonCode)
    }

    @Test fun uncertainSyntheticPpgMorphologyDoesNotProduceAgi() {
        val waveform = syntheticPpg()
        val result = VascularResearchEngine.fromPpg(
            90, waveform.size, "valid_signal", waveform, 100f, true, true,
            "android_sensor_event_elapsed_realtime"
        )
        assertNull(result.appNormalizedApgIndex)
        assertEquals("INSUFFICIENT_APG_FIDUCIALS", result.reasonCode)
        assertNull(result.vascularAgeYears)
        assertTrue(result.method.contains(VascularResearchEngine.VERSION))
        assertTrue(result.method.contains("不換算血管年齡"))
    }

    @Test fun appApgMorphologyFormulaProducesFiniteDimensionlessResearchIndex() {
        val amplitudes = listOf(
            0.04444334823274382, 0.09541772416429398, 0.10431208632214115,
            0.016206957460177057, -0.007502443484871224, 0.03928930902956107,
            0.12031318637758309, 0.0720723217659344
        )
        val pulse = List(128) { index ->
            val phase = index.toDouble() / 127.0
            val harmonics = amplitudes.mapIndexed { harmonic, amplitude ->
                amplitude * cos(2.0 * Math.PI * (harmonic + 2) * phase)
            }.sum()
            0.5 - 0.5 * cos(2.0 * Math.PI * phase) + harmonics
        }
        val index = VascularResearchEngine.appNormalizedApgIndexFromPulse(pulse)
        assertTrue(index?.isFinite() == true)
        val gated = VascularResearchEngine.fromPpg(
            90, 3_272, "valid_signal", List(2_000) { index -> pulse[index % pulse.size].toFloat() },
            100f, true, true, "android_sensor_event_elapsed_realtime"
        )
        assertTrue(gated.method.contains("非標準 SDPPG AGI") || gated.method.contains("未與論文流程驗證"))
    }

    private fun syntheticPpg(): List<Float> {
        val sampleRate = 100
        val samplesPerBeat = 80
        return List(sampleRate * 20) { sample ->
            val phase = (sample % samplesPerBeat).toDouble() / samplesPerBeat
            (0.5 - 0.5 * cos(2.0 * Math.PI * phase) + 0.01 * kotlin.math.sin(14.0 * Math.PI * phase)).toFloat()
        }
    }
}
