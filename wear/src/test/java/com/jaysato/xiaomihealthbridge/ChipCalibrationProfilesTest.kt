package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipCalibrationProfilesTest {
    @Test
    fun opticalShapePassIsExplicitlyHeuristicNotMeasurementValidity() {
        val result = ChipCalibrationProfiles.assessOpticalWaveform(
            List(100) { index -> (100 + index).toFloat() }
        )
        assertTrue(result.valid)
        assertTrue(result.advice.contains("啟發式"))
        assertTrue(result.advice.contains("不代表有效 PPG"))
    }

    @Test
    fun emptyAndFillHeavyOpticalPayloadsAreNotCalledUsable() {
        assertFalse(ChipCalibrationProfiles.assessOpticalWaveform(emptyList()).valid)
        val result = ChipCalibrationProfiles.assessOpticalWaveform(List(100) { ChipCalibrationProfiles.OPTICAL_FILL })
        assertFalse(result.valid)
        assertTrue(result.advice.contains("可能原因未確認"))
    }

    @Test
    fun finiteZeroOpticalSamplesAreNotCountedAsMissingFillValues() {
        val values = List(100) { index -> if (index < 64) 0f else (100 + index).toFloat() }

        val result = ChipCalibrationProfiles.assessOpticalWaveform(values)

        assertTrue(result.valid)
        assertTrue(result.fillRatio == 0.0)
    }

    @Test
    fun manufacturerChipAccuracyIsNotPresentedAsWholeWatchAccuracy() {
        val note = ChipCalibrationProfiles.temperatureCalibrationNote()
        assertTrue(note.contains("單位"))
        assertTrue(note.contains("不套用偏移"))
        assertFalse(note.contains("±0.1 °C"))
    }
}
