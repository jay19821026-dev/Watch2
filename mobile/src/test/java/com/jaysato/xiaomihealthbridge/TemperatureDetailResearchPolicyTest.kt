package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureDetailResearchPolicyTest {
    @Test
    fun missingTimestampStillShowsIndexedRelativeMetricsAndEvidence() {
        val lines = TemperatureDetailResearchPolicy.findings(
            waveform = listOf(0f, 10f, 20f),
            fallbackRawValue = null,
            source = "watch_temperature_raw",
            timestampSource = "unknown",
            measuredAtMillis = 0L,
            nowMillis = 1_000_000L
        )

        assertTrue(lines.any { it.contains("有限樣本 3 點") })
        assertTrue(lines.any { it.contains("索引順序，非時間尺度") })
        assertTrue(lines.any { it.contains("最後有限樣本−中位數") })
        assertTrue(lines.any { it.contains("資料來源：watch_temperature_raw") })
        assertTrue(lines.any { it.contains("時間來源：unknown") && it.contains("保存時間欄位：缺少／無效") })
        assertTrue(lines.any { it.contains("NORMALIZED_INDEX") })
        assertTrue(lines.any { it.contains("訊號品質／分析信心：未評分") })
        assertTrue(lines.any { it.contains("資料完整度") && it.contains("非準確率") })
    }

    @Test
    fun trustedTimestampContributesEvidenceButDoesNotChangeIndexAxis() {
        val lines = TemperatureDetailResearchPolicy.findings(
            waveform = listOf(35.0f, 36.0f, 37.0f),
            fallbackRawValue = null,
            source = "watch_temperature_raw",
            timestampSource = "android_sensor_event_elapsed_realtime",
            measuredAtMillis = 900_000L,
            nowMillis = 1_000_000L
        )

        assertTrue(lines.any { it.contains("NORMALIZED_INDEX") })
        assertTrue(lines.any { it.contains("保存時間欄位：900000 ms") })
        assertTrue(lines.any { it.contains("分析信心未評分") })
    }

    @Test
    fun staleTrustedTimeDoesNotContributeCurrentTimeEvidenceToCompleteness() {
        val waveform = listOf(35f, 36f, 37f)
        val lines = TemperatureDetailResearchPolicy.findings(
            waveform = waveform,
            fallbackRawValue = null,
            source = "watch_temperature_raw",
            timestampSource = "android_sensor_event_elapsed_realtime",
            measuredAtMillis = 1_000_000L - TemperaturePresentation.MAX_AGE_MILLIS - 1L,
            nowMillis = 1_000_000L
        )
        val noTimeEvidence = TemperatureResearchSummary.calculate(
            waveform,
            sourceEvidence = ResearchSourceEvidence.score("watch_temperature_raw"),
            timeEvidence = 0
        )!!.displayText()

        assertTrue(lines.contains(noTimeEvidence))
    }

    @Test
    fun oneStoredValueStillProducesAResearchSummaryWithoutInventingSequence() {
        val lines = TemperatureDetailResearchPolicy.findings(
            waveform = emptyList(),
            fallbackRawValue = 36.6,
            source = "watch_temperature_raw",
            timestampSource = null,
            measuredAtMillis = 0L,
            nowMillis = 1_000_000L
        )

        assertTrue(lines.any { it.contains("有限樣本 1 點") })
        assertTrue(lines.any { it.contains("單點原始值 36.60") })
        assertTrue(lines.any { it.contains("至少需 2 點") })
    }

    @Test
    fun missingSourceIsShownAsUnknownInsteadOfSlash() {
        val lines = TemperatureDetailResearchPolicy.findings(
            waveform = listOf(36f),
            fallbackRawValue = null,
            source = "/",
            timestampSource = null,
            measuredAtMillis = 0L,
            nowMillis = 1_000_000L
        )

        assertTrue(lines.any { it.contains("資料來源：UNKNOWN_SOURCE") })
    }

    @Test
    fun emptyTemperatureCaptureShowsZeroObservedSamplesAndUnratedReason() {
        val lines = TemperatureDetailResearchPolicy.findings(
            waveform = emptyList(),
            fallbackRawValue = null,
            source = "watch_temperature_raw",
            timestampSource = null,
            measuredAtMillis = 0L,
            nowMillis = 1_000_000L
        )

        assertTrue(lines.any { it.contains("有限值 0 / 輸入 0 點") })
        assertTrue(lines.any { it.contains("研究指標：未評分（沒有有限原始樣本）") })
        assertTrue(lines.any { it.contains("NORMALIZED_INDEX") })
    }

    @Test
    fun nonFiniteTemperatureValuesAreCountedAsInputButNotAsSamples() {
        val lines = TemperatureDetailResearchPolicy.findings(
            waveform = listOf(Float.NaN, Float.POSITIVE_INFINITY),
            fallbackRawValue = null,
            source = "watch_temperature_raw",
            timestampSource = null,
            measuredAtMillis = 0L,
            nowMillis = 1_000_000L
        )

        assertTrue(lines.any { it.contains("有限值 0 / 輸入 2 點") })
        assertTrue(lines.any { it.contains("研究指標：未評分（沒有有限原始樣本）") })
    }

    @Test
    fun finiteStoredFallbackIsShownWhenWaveformContainsOnlyNonFinitePoints() {
        val lines = TemperatureDetailResearchPolicy.findings(
            waveform = listOf(Float.NaN),
            fallbackRawValue = 36.6,
            source = "watch_temperature_raw",
            timestampSource = null,
            measuredAtMillis = 0L,
            nowMillis = 1_000_000L
        )

        assertTrue(lines.any { it.contains("有限值 0 / 輸入 1 點") })
        assertTrue(lines.any { it.contains("使用獨立保存的原始值") })
        assertTrue(lines.any { it.contains("單點原始值 36.60") })
    }
}
