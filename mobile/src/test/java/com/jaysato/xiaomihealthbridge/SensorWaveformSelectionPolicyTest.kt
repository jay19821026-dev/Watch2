package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorWaveformSelectionPolicyTest {
    @Test
    fun ppgDisplayPrefersGapPreservingWaveformWithoutChangingAnalysisInput() {
        val display = listOf(1f, Float.NaN, 3f)
        val analysis = listOf(1f, 3f)

        val selected = SensorWaveformSelectionPolicy.ppgDisplayWaveform(display, analysis)

        assertEquals(3, selected.size)
        assertTrue(selected[1].isNaN())
        assertEquals(2, analysis.size)
    }

    @Test
    fun ppgDisplayFallsBackWhenNoGapPreservingPreviewExists() {
        val analysis = listOf(1f, 2f)

        assertEquals(analysis, SensorWaveformSelectionPolicy.ppgDisplayWaveform(emptyList(), analysis))
    }
}
