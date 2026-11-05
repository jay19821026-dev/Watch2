package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpticalSignalQualityTest {
    @Test
    fun diagnosticRateTextDistinguishesHalSamplingFromCallbackCadence() {
        assertEquals(
            "HAL 取樣時基未回報",
            RawVitalsProbe.summaryRateDescription(RawVitalsProbe.Kind.ECG, 0.0)
        )
        assertEquals(
            "HAL 回報取樣率 100.0 Hz",
            RawVitalsProbe.summaryRateDescription(RawVitalsProbe.Kind.PPG, 100.0)
        )
        assertEquals(
            "整段事件吞吐 2.0 事件/秒（非取樣率）",
            RawVitalsProbe.summaryRateDescription(RawVitalsProbe.Kind.ACCELEROMETER, 0.0, 2.0)
        )
        assertEquals(
            "硬體取樣率未知",
            RawVitalsProbe.summaryRateDescription(RawVitalsProbe.Kind.MI_SPO2, 0.0)
        )
    }

    @Test
    fun fillDominatedFramesAreRejectedEvenWithAnOutlier() {
        val waveform = MutableList(256) { ChipCalibrationProfiles.OPTICAL_FILL }
        waveform[0] = 180_000f
        waveform[1] = 180_100f
        assertFalse(RawVitalsProbe.isValidOpticalWaveform(waveform))
    }

    @Test
    fun zeroOnlyFramesAreRejected() {
        assertFalse(RawVitalsProbe.isValidOpticalWaveform(List(256) { 0f }))
    }

    @Test
    fun opticalChannelSelectionTreatsZeroAsFiniteAndOnlyExcludesKnownSentinel() {
        assertTrue(RawVitalsProbe.isUsableOpticalValue(0f))
        assertFalse(RawVitalsProbe.isUsableOpticalValue(ChipCalibrationProfiles.OPTICAL_FILL))
        assertFalse(RawVitalsProbe.isUsableOpticalValue(Float.NaN))
    }

    @Test
    fun opticalChannelSelectionPreservesSentinelGapIndicesAndZeroSamples() {
        val rows = listOf(
            floatArrayOf(0f, ChipCalibrationProfiles.OPTICAL_FILL),
            floatArrayOf(10f, ChipCalibrationProfiles.OPTICAL_FILL),
            floatArrayOf(ChipCalibrationProfiles.OPTICAL_FILL, 7f),
            floatArrayOf(30f, ChipCalibrationProfiles.OPTICAL_FILL),
            floatArrayOf(40f, 9f)
        )

        val selected = RawVitalsProbe.selectOpticalChannelForResearch(rows)

        assertEquals(rows.size, selected.size)
        assertEquals(0f, selected[0])
        assertEquals(ChipCalibrationProfiles.OPTICAL_FILL, selected[2])
    }

    @Test
    fun sufficientlyLongNonClippedOpticalWaveformPassesEngineeringSqiGate() {
        val waveform = List(256) { index -> 20_000f + kotlin.math.sin(index / 5.0).toFloat() * 120f }
        assertTrue(RawVitalsProbe.isValidOpticalWaveform(waveform))
    }

    @Test
    fun unpackingKeepsFillAndZeroFramesForQualityAuditing() {
        val frame = floatArrayOf(ChipCalibrationProfiles.OPTICAL_FILL, 0f, 12f, 13f, 0f, 0f, 0f, 0f)
        assertTrue(RawVitalsProbe.unpackWaveform(RawVitalsProbe.Kind.PPG, frame).contains(ChipCalibrationProfiles.OPTICAL_FILL))
        assertTrue(RawVitalsProbe.unpackWaveform(RawVitalsProbe.Kind.PPG, frame).contains(0f))
    }

    @Test
    fun ecgSensorEventValuesAreNeverAssumedToBeWaveformSamples() {
        val eventValues = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f)
        assertTrue(RawVitalsProbe.unpackWaveform(RawVitalsProbe.Kind.ECG, eventValues).isEmpty())
    }

    @Test
    fun biozEventFieldsAreNotFlattenedIntoAnUnverifiedTimeWaveform() {
        val eventFields = floatArrayOf(1f, 50f, 7f, 90f, 4f, 32f, 2f, 16f)
        assertTrue(RawVitalsProbe.unpackWaveform(RawVitalsProbe.Kind.BIOZ, eventFields).isEmpty())
        assertTrue(RawVitalsProbe.unpackWaveform(RawVitalsProbe.Kind.BIOZ_RESPIRATION, eventFields).isEmpty())
    }

    @Test
    fun ppgKeepsAllChannelsAndEventRateIsNotReportedAsAdcRate() {
        val frame = FloatArray(16) { index -> index.toFloat() + 1f }
        val points = RawVitalsProbe.unpackWaveform(RawVitalsProbe.Kind.PPG, frame)
        assertTrue(points.size == frame.size)
        assertTrue(points.first() == 1f && points.last() == 16f)
        assertTrue(RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.PPG, null) == 0.0)
        assertTrue(RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.BIOZ, null) == 0.0)
        assertTrue(RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.SPO2, null) == 0.0)
        assertTrue(RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.ECG, null) == 0.0)
        assertTrue(RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.ECG, 0.002) == 0.0)
        assertTrue(RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.PPG, 0.01) == 100.0)
        assertTrue(RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.SPO2, 0.02) == 50.0)
        assertTrue(RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.BIOZ, 0.005) == 200.0)
        assertTrue(RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.HEART_RATE, 0.01) == 0.0)
        assertTrue(RawVitalsProbe.reportedSampleRateHz(RawVitalsProbe.Kind.BOARD_TEMPERATURE, null) == 0.0)
        assertEquals(2.0, RawVitalsProbe.eventRatePerSessionSecond(20, 10_000_000_000), 0.0)
        assertEquals(0.0, RawVitalsProbe.eventRatePerSessionSecond(0, 10_000_000_000), 0.0)
    }

    @Test
    fun halSamplingInfoRejectsInvalidPeriodAndJitter() {
        assertTrue(RawVitalsProbe.validHalSamplePeriodSeconds(0.01) == 0.01)
        assertTrue(RawVitalsProbe.validHalSamplePeriodSeconds(null) == null)
        assertTrue(RawVitalsProbe.validHalSamplePeriodSeconds(Double.NaN) == null)
        assertTrue(RawVitalsProbe.validHalSamplePeriodSeconds(0.0) == null)
        assertTrue(RawVitalsProbe.validHalSamplePeriodSeconds(2.0) == null)
        assertTrue(RawVitalsProbe.validHalSampleJitterSeconds(0.0001) == 0.0001)
        assertTrue(RawVitalsProbe.validHalSampleJitterSeconds(-0.001) == null)
        assertTrue(RawVitalsProbe.validHalSampleJitterSeconds(Double.POSITIVE_INFINITY) == null)
    }

    @Test
    fun contactSensorsAreClassifiedForChargingGuard() {
        val contactKinds = setOf(
            RawVitalsProbe.Kind.HEART_RATE,
            RawVitalsProbe.Kind.MAX_HEART_RATE,
            RawVitalsProbe.Kind.PPG,
            RawVitalsProbe.Kind.SPO2,
            RawVitalsProbe.Kind.MI_SPO2,
            RawVitalsProbe.Kind.ECG,
            RawVitalsProbe.Kind.BIOZ,
            RawVitalsProbe.Kind.LIFE_Q_OTT,
            RawVitalsProbe.Kind.OFF_BODY
        )
        contactKinds.forEach { assertTrue("$it should require wear contact", it.requiresWearContact) }
        assertFalse(RawVitalsProbe.Kind.PRESSURE.requiresWearContact)
        assertFalse(RawVitalsProbe.Kind.LIGHT.requiresWearContact)
    }

    @Test
    fun boundedSensorSamplingPolicyCapsEachSensorFamily() {
        assertTrue(RawVitalsProbe.minimumSamplingPeriodUs(RawVitalsProbe.Kind.ECG) >= 2_000)
        assertTrue(RawVitalsProbe.minimumSamplingPeriodUs(RawVitalsProbe.Kind.BIOZ) >= 2_000)
        assertTrue(RawVitalsProbe.minimumSamplingPeriodUs(RawVitalsProbe.Kind.PPG) >= 10_000)
        assertTrue(RawVitalsProbe.minimumSamplingPeriodUs(RawVitalsProbe.Kind.ACCELEROMETER) >= 50_000)
    }
}
