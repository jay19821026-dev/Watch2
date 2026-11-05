package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class Spo2RawQualityPolicyTest {
    @Test
    fun unknownRateReportsIndexDomainShapeWithoutInventingTimeScaleOrSpo2Percent() {
        val waveform = List(128) { index -> sin(index * 0.2).toFloat() }

        val result = Spo2RawQualityPolicy.assess(waveform, 0f)

        assertNotNull(result.score)
        assertEquals("scored_index_domain", result.status)
        assertEquals(128, result.finiteSampleCount)
        assertEquals(128, result.totalSampleCount)
        assertEquals(100, result.finiteCoveragePercent)
        assertTrue(Spo2RawQualityPolicy.summary(result).contains("索引域波形形狀指數"))
        assertTrue(Spo2RawQualityPolicy.summary(result).contains("未使用時間／頻率特徵"))
        assertTrue(Spo2RawQualityPolicy.summary(result).contains("尚無可信百分比"))
    }

    @Test
    fun reportedRateAllowsOnlyGenericOpticalShapeScore() {
        val waveform = List(128) { index -> sin(index * 0.2).toFloat() }

        val result = Spo2RawQualityPolicy.assess(waveform, 25f)

        assertNotNull(result.score)
        assertEquals("scored_index_domain", result.status)
        assertEquals(100, result.finiteCoveragePercent)
    }

    @Test
    fun unknownRateKeepsGapSegmentsSeparateAndLabelsIndexDomain() {
        val waveform = List(128) { index ->
            if (index == 64) 524287f else sin(index * 0.2).toFloat()
        }

        val result = Spo2RawQualityPolicy.assess(waveform, 0f)

        assertNotNull(result.score)
        assertEquals("scored_index_domain_segmented", result.status)
        assertEquals(127, result.finiteSampleCount)
        assertEquals(127, result.qualitySampleCount)
        assertTrue(Spo2RawQualityPolicy.summary(result).contains("索引域波形形狀指數"))
        assertTrue(Spo2RawQualityPolicy.summary(result).contains("尚無可信百分比"))
    }

    @Test
    fun sampleRateDoesNotInflateIndexShapeScoreWithFrequencyBonus() {
        val waveform = List(128) { index -> sin(index * 0.2).toFloat() }

        val indexOnly = Spo2RawQualityPolicy.assess(waveform, 0f)
        val reportedRate = Spo2RawQualityPolicy.assess(waveform, 25f)

        assertEquals(indexOnly.score, reportedRate.score)
        assertTrue(Spo2RawQualityPolicy.summary(reportedRate).contains("未使用時間／頻率特徵"))
    }

    @Test
    fun insufficientSamplesRemainUnscored() {
        val result = Spo2RawQualityPolicy.assess(List(8) { it.toFloat() }, 25f)

        assertNull(result.score)
        assertEquals("not_scored_insufficient_samples", result.status)
    }

    @Test
    fun sentinelAndNonFiniteValuesAreExcludedFromCoverageWithoutArithmeticOverflow() {
        val waveform = listOf(Float.MAX_VALUE, -Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, 524287f)

        val result = Spo2RawQualityPolicy.assess(waveform, 0f)

        assertNull(result.score)
        assertEquals(2, result.finiteSampleCount)
        assertEquals(5, result.totalSampleCount)
        assertEquals(40, result.finiteCoveragePercent)
    }

    @Test
    fun fixedWaveformWithoutTimebaseRemainsUnscoredAsAFlatline() {
        val result = Spo2RawQualityPolicy.assess(List(128) { 7f }, 0f)

        assertNull(result.score)
        assertEquals(100, result.finiteCoveragePercent)
        assertEquals("not_scored_zero_quality", result.status)
    }

    @Test
    fun fixedWaveformWithKnownRateDoesNotDisplayZeroOutOfHundred() {
        val result = Spo2RawQualityPolicy.assess(List(128) { 7f }, 25f)

        assertNull(result.score)
        assertEquals("not_scored_zero_quality", result.status)
        assertTrue(Spo2RawQualityPolicy.summary(result).contains("形狀品質未評分"))
        assertTrue(!Spo2RawQualityPolicy.summary(result).contains("0/100"))
    }

    @Test
    fun extremeAdjacentValuesAreNotPassedToFloatBasedQualityMath() {
        val waveform = List(16) { index -> if (index % 2 == 0) Float.MAX_VALUE else -Float.MAX_VALUE }

        val result = Spo2RawQualityPolicy.assess(waveform, 25f)

        assertNull(result.score)
        assertEquals("not_scored_numeric_range", result.status)
        assertEquals(100, result.finiteCoveragePercent)
    }

    @Test
    fun emptyInputHasUndefinedCoverageInsteadOfZeroPercent() {
        val result = Spo2RawQualityPolicy.assess(emptyList(), 0f)

        assertNull(result.finiteCoveragePercent)
        assertTrue(Spo2RawQualityPolicy.summary(result).contains("未計算（無事件）"))
    }

    @Test
    fun missingValuesSplitQualityWindowsInsteadOfBeingCompressedAway() {
        val waveform = List(40) { index ->
            if (index == 20) 524287f else kotlin.math.sin(index * 0.2).toFloat()
        }

        val result = Spo2RawQualityPolicy.assess(waveform, 25f)

        assertEquals("scored_index_domain_segmented", result.status)
        assertEquals(39, result.finiteSampleCount)
        assertEquals(97, result.finiteCoveragePercent)
        assertEquals(39, result.qualitySampleCount)
        assertTrue(Spo2RawQualityPolicy.summary(result).contains("尚無可信百分比"))
    }
}
