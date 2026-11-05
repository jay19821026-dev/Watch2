package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthRiskScreeningTest {
    @Test
    fun unknownAndInconclusiveTitlesNeverBecomeRegularOrAfResults() {
        listOf("", "心律結果不明", "資料不足", "非房顫", "未見心律不整").forEach {
            assertEquals(HealthRiskScreening.ResearchPattern.UNKNOWN, HealthRiskScreening.researchPattern(it))
        }
        assertEquals(HealthRiskScreening.ResearchPattern.REGULAR,
            HealthRiskScreening.researchPattern("RR 間距規則（研究；不排除心律異常）"))
        assertEquals(HealthRiskScreening.ResearchPattern.IRREGULAR,
            HealthRiskScreening.researchPattern("RR 間距不規則（研究；非 AF 判讀）"))
    }

    @Test
    fun rhythmScreenRequiresFreshTrustedTimeAndVerifiedWaveformEncoding() {
        val verifiedEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        fun usable(
            timestampSource: String? = "android_sensor_event_elapsed_realtime",
            waveformEncoding: String? = verifiedEncoding,
            wearQualified: Boolean? = true,
            rrCount: Int = 8
        ) = HealthRiskScreening.isResearchRhythmEvidenceUsable(
            measuredAt = 9_500L,
            timestampSource = timestampSource,
            now = 10_000L,
            qualityScore = 80,
            sampleCount = 512,
            sampleRate = 50f,
            status = "has_data",
            rrCount = rrCount,
            wearQualified = wearQualified,
            waveformEncoding = waveformEncoding
        )

        assertTrue(usable())
        assertEquals(false, usable(rrCount = 4))
        assertTrue(usable(rrCount = 5))
        assertEquals(false, usable(timestampSource = "phone_receive_time"))
        assertEquals(false, usable(waveformEncoding = EcgPayloadInterpretationPolicy.UNMAPPED_EVENT_PREVIEW))
        assertEquals(false, usable(wearQualified = false))
        assertEquals(false, usable(wearQualified = null))
    }

    @Test
    fun ppgBelowTwentyHzCannotBeUsedForClinicalRateOrVariability() {
        val waveform = List(256) { index -> (kotlin.math.sin(index / 3.0) * 400 + 12_000).toFloat() }
        val result = BiomedicalCalibration.refinePpg(waveform, 10.3f)
        assertEquals(false, result.valid)
        assertEquals(null, result.pulseRateBpm)
        assertEquals(0, result.rrCount)
    }

    @Test
    fun ppgAtThirtyTwoHzNeverProducesHrv() {
        val waveform = List(512) { index -> (kotlin.math.sin(index / 4.0) * 400 + 12_000).toFloat() }
        val result = BiomedicalCalibration.refinePpg(waveform, 32f)
        assertTrue(result.rrCount >= 0)
        assertTrue(result.pulseRateBpm == null || result.pulseRateBpm!! in 40.0..180.0)
    }

    @Test
    fun ppgAboveSixtyFourHzMayExposeVariabilityOnlyWhenRrIsFound() {
        val waveform = List(2048) { index -> (kotlin.math.sin(index / 4.0) * 400 + 12_000).toFloat() }
        val result = BiomedicalCalibration.refinePpg(waveform, 128f)
        assertEquals(128f, result.effectiveSampleRateHz)
        assertTrue(result.rrCount >= 0)
    }
}
