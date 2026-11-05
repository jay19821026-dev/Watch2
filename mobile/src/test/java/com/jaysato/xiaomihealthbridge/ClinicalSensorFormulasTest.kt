package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.HealthAlgorithmEngine as ClinicalSensorFormulas

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class ClinicalSensorFormulasTest {
    @Test
    fun hrvComputesRmssdSdnnAndNrmssd() {
        val rr = listOf(800.0, 810.0, 790.0, 805.0, 795.0, 800.0, 815.0, 785.0)
        val hrv = ClinicalSensorFormulas.hrvFromRr(rr)
        assertEquals(8, hrv.rrCount)
        assertNotNull(hrv.rmssdMs)
        assertNotNull(hrv.sdnnMs)
        assertNotNull(hrv.nRmssd)
        assertTrue(hrv.rmssdMs!! > 0)
        assertEquals(hrv.rmssdMs!! / hrv.meanRrMs!!, hrv.nRmssd!!, 1e-9)
        assertTrue(hrv.citations.any { it.contains("nRMSSD") || it.contains("RMSSD") })
    }

    @Test
    fun opticalPiIsAcOverDcPercent() {
        val wave = (0 until 128).map { i ->
            (1000.0 + 50.0 * sin(i * 2 * Math.PI / 16.0)).toFloat()
        }
        val optical = ClinicalSensorFormulas.opticalFromWaveform(wave, 25.0, channelMappingVerified = true)
        assertNotNull(optical.perfusionIndexPercent)
        assertTrue(optical.perfusionIndexPercent!! > 0)
        assertTrue(optical.note.contains("SpO") || optical.note.contains("PI"))
        // Single channel must not invent calibrated SpO2 %
        assertNull(optical.ratioOfRatiosR)
    }

    @Test
    fun dualChannelRatioOfRatiosComputedWithoutSpo2Percent() {
        val pairs = (0 until 64).map { i ->
            val red = 1000.0 + 40.0 * sin(i * 0.4)
            val ir = 1200.0 + 80.0 * sin(i * 0.4)
            red to ir
        }
        val optical = ClinicalSensorFormulas.opticalFromWaveform(
            pairs.map { it.first.toFloat() },
            25.0,
            pairs,
            channelMappingVerified = true
        )
        assertNotNull(optical.ratioOfRatiosR)
        assertTrue(optical.ratioOfRatiosR!! > 0)
        assertTrue(optical.note.contains("不轉 SpO") || optical.note.contains("不輸出 SpO"))
    }

    @Test
    fun isaAltitudeAtSeaLevelNearZero() {
        val m = ClinicalSensorFormulas.pressureAltitude(1013.25)
        assertEquals(1013.25, m.pressureHpa!!, 1e-6)
        assertEquals(0.0, m.altitudeM!!, 0.5)
    }

    @Test
    fun isaAltitudeIncreasesWhenPressureDrops() {
        val high = ClinicalSensorFormulas.pressureAltitude(900.0)
        assertTrue(high.altitudeM!! > 800)
        assertTrue(high.note.contains("44330"))
    }

    @Test
    fun fillSentinelExcludedFromOptical() {
        val wave = List(64) { ClinicalSensorFormulas.OPTICAL_FILL } +
            List(64) { (900 + it).toFloat() }
        val optical = ClinicalSensorFormulas.opticalFromWaveform(wave, 50.0, channelMappingVerified = true)
        assertNotNull(optical.dc)
        assertTrue(abs(optical.dc!! - ClinicalSensorFormulas.OPTICAL_FILL) > 1.0)
    }

    @Test
    fun unknownSamplingClockIsNotMisreportedAsTooFewSamples() {
        val waveform = List(128) { (1000 + it).toFloat() }

        val optical = ClinicalSensorFormulas.opticalFromWaveform(
            waveform,
            sampleRateHz = 0.0,
            channelMappingVerified = true
        )

        assertNull(optical.pulseRateBpm)
        assertNull(optical.perfusionIndexPercent)
        assertNotNull(optical.relativeAcDcRatio)
        assertTrue(optical.relativeAcDcRatio!! > 0.0)
        assertTrue(optical.note.contains("取樣時基"))
        assertFalse(optical.note.contains("樣本不足"))
        assertTrue(optical.note.contains("無單位描述指標"))
        assertTrue(ClinicalSensorFormulas.summaryLine(optical, null, null).contains("相對 AC/DC"))
    }

    @Test
    fun unverifiedChannelMappingRetainsRelativeAcDcButBlocksPhysiologicalMetrics() {
        val waveform = List(128) { (1000 + it).toFloat() }

        val optical = ClinicalSensorFormulas.opticalFromWaveform(waveform, 50.0)

        assertNull(optical.pulseRateBpm)
        assertNull(optical.perfusionIndexPercent)
        assertNull(optical.ratioOfRatiosR)
        assertNotNull(optical.relativeAcDcRatio)
        assertTrue(optical.relativeAcDcRatio!! > 0.0)
        assertTrue(optical.note.contains("通道映射"))
        assertTrue(optical.note.contains("不是灌流指數或生理值"))
    }

    @Test
    fun flatlineOpticalWaveformHasNoRelativeRatio() {
        val optical = ClinicalSensorFormulas.opticalFromWaveform(List(128) { 0f }, 0.0)

        assertNull(optical.relativeAcDcRatio)
        assertNull(optical.perfusionIndexPercent)
        assertTrue(optical.note.contains("取樣時基"))
    }

    @Test
    fun researchStressScoreFallsWhenNrmssdHigh() {
        val calm = ClinicalSensorFormulas.researchStressScore(0.18, 60.0)
        val tense = ClinicalSensorFormulas.researchStressScore(0.02, 95.0)
        assertNotNull(calm)
        assertNotNull(tense)
        assertTrue(tense!! > calm!!)
    }

    @Test
    fun researchStressScoreRejectsInvalidInputsInsteadOfClampingThem() {
        assertNull(ClinicalSensorFormulas.researchStressScore(Double.NaN, 60.0))
        assertNull(ClinicalSensorFormulas.researchStressScore(-0.01, 60.0))
        assertNull(ClinicalSensorFormulas.researchStressScore(2.01, 60.0))
        assertNull(ClinicalSensorFormulas.researchStressScore(0.1, Double.NaN))
        assertNull(ClinicalSensorFormulas.researchStressScore(0.1, 0.0))
        assertNull(ClinicalSensorFormulas.researchStressScore(0.1, 241.0))
        assertNotNull(ClinicalSensorFormulas.researchStressScore(0.1, null))
    }
}
