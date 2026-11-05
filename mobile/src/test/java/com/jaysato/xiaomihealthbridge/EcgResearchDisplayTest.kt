package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgResearchDisplayTest {
    @Test
    fun missingStableWindowDoesNotReuseWholeCaptureScoreLabel() {
        assertEquals("穩定區段研究品質 未評分", EcgResearchDisplay.stableWindowQualityLabel(null))
        assertEquals("穩定區段研究品質 81/100", EcgResearchDisplay.stableWindowQualityLabel(81))
    }

    @Test
    fun normalizedTimelineShowsRelativeAmplitudeAndIndexPeaksWithoutPhysicalUnits() {
        val lines = EcgResearchDisplay.evidenceLines(
            waveform = listOf(-2f, 1f, 3f),
            sampleRateHz = null,
            timelineSource = "NORMALIZED_INDEX",
            rPeakIndices = listOf(10, 45, 80),
            rPeakIndexDistancesSamples = listOf(35, 35),
            calibratedMv = false
        )

        assertTrue(lines.any { it.contains("NORMALIZED_INDEX") })
        assertTrue(lines.any { it.contains("波形峰峰值：5.000 raw code") && it.contains("單位未驗證") })
        assertTrue(lines.any { it.contains("10, 45, 80") })
        assertTrue(lines.any { it.contains("35, 35 samples（非 ms）") })
        assertTrue(lines.any { it.contains("研究資料完整度指數：") && it.contains("非訊號準確度／診斷信心") })
        assertTrue(lines.none { it.contains("mV") || it.contains("bpm") })
    }

    @Test
    fun estimatedTimelineAndResearchMvKeepTheirProvenanceLabel() {
        val lines = EcgResearchDisplay.evidenceLines(
            waveform = listOf(-0.2f, 0.3f),
            sampleRateHz = 125f,
            timelineSource = "ESTIMATED",
            rPeakIndices = emptyList(),
            rPeakIndexDistancesSamples = emptyList(),
            calibratedMv = true
        )

        assertTrue(lines.any { it.contains("ESTIMATED_TIMELINE") && it.contains("125.0 點/秒") })
        assertTrue(lines.any { it.contains("研究資料完整度指數：") })
        assertTrue(lines.any { it.contains("0.50000 mV") && it.contains("外部參考研究映射") })
    }

    @Test
    fun gapsAreNotCountedAsDecodedSamplesOrAllowedThroughMvProfile() {
        val waveform = listOf(0f, 1f, Float.NaN, 2f)
        val lines = EcgResearchDisplay.evidenceLines(
            waveform = waveform,
            sampleRateHz = null,
            timelineSource = "NORMALIZED_INDEX",
            rPeakIndices = emptyList(),
            rPeakIndexDistancesSamples = emptyList(),
            calibratedMv = true
        )

        assertTrue(lines.first().contains("3 個有效樣本 · 4 個索引位置"))
        assertTrue(lines.any { it.contains("raw code") && it.contains("單位未驗證") })
        assertTrue(lines.any { it.contains("研究資料完整度指數：") })
        assertTrue(lines.none { it.contains("mV") })
        assertTrue(!EcgResearchDisplay.canApplyValidatedMvProfile(waveform))
        assertTrue(EcgResearchDisplay.canApplyValidatedMvProfile(listOf(0f, 1f, 2f)))
        assertEquals("已解碼 3 個有效研究樣本 · 保留 1 個缺口索引", EcgResearchDisplay.decodedSampleHeadline(waveform))
        assertEquals("3 個有效點 · 1 個缺口索引", EcgResearchDisplay.waveformPointHeadline(waveform))
    }

    @Test
    fun completenessIndexRequiresEnoughFiniteEvidenceAndSeparatesTimeSource() {
        val samples = List(32) { it.toFloat() }
        assertNull(EcgResearchDisplay.evidenceCompletenessScore(samples.take(31), "SENSOR", 80))
        assertNull(EcgResearchDisplay.evidenceCompletenessScore(samples, "SENSOR", null))
        val sensorTime = EcgResearchDisplay.evidenceCompletenessScore(samples, "SENSOR", 80)
        val estimatedTime = EcgResearchDisplay.evidenceCompletenessScore(samples, "ESTIMATED", 80)
        val indexTime = EcgResearchDisplay.evidenceCompletenessScore(samples, "NORMALIZED_INDEX", 80)
        val unknownTime = EcgResearchDisplay.evidenceCompletenessScore(samples, "FUTURE_VALUE", 80)
        assertNotNull(sensorTime)
        assertTrue(sensorTime!! > estimatedTime!!)
        assertTrue(estimatedTime > indexTime!!)
        assertTrue(indexTime > unknownTime!!)
        val gappedSamples = samples + List(8) { Float.NaN }
        val gappedScore = EcgResearchDisplay.evidenceCompletenessScore(gappedSamples, "SENSOR", 80)
        assertNotNull(gappedScore)
        assertTrue(gappedScore!! < sensorTime)
        assertNull(EcgResearchDisplay.evidenceCompletenessScore(List(32) { Float.NaN }, "SENSOR", 80))
    }

    @Test
    fun decoderTraceabilityRequiresKnownDecoderAndSourceRetentionState() {
        assertEquals(100, EcgResearchDisplay.decoderTraceabilityScore(
            "complete_for_observed_events", EcgHalEventResearchDecoder.ENCODING, true
        ))
        assertEquals(60, EcgResearchDisplay.decoderTraceabilityScore(
            "bounded_recent_events", EcgHalEventResearchDecoder.ENCODING, true
        ))
        assertNull(EcgResearchDisplay.decoderTraceabilityScore(
            "legacy_app_split_unverified", EcgHalEventResearchDecoder.ENCODING, true
        ))
        assertNull(EcgResearchDisplay.decoderTraceabilityScore(
            "complete_for_observed_events", "unknown_encoding", true
        ))
        assertNull(EcgResearchDisplay.decoderTraceabilityScore(
            "complete_for_observed_events", EcgHalEventResearchDecoder.ENCODING, false
        ))
    }

    @Test
    fun unknownTimelineAndSampleRateAreExplicitlyShown() {
        val lines = EcgResearchDisplay.evidenceLines(
            waveform = List(32) { it.toFloat() },
            sampleRateHz = null,
            timelineSource = "FUTURE_VALUE",
            rPeakIndices = emptyList(),
            rPeakIndexDistancesSamples = emptyList(),
            calibratedMv = false
        )
        assertTrue(lines.first().contains("UNKNOWN（時間來源未確認）"))
        assertTrue(lines.first().contains("點速率：未確認"))
        assertTrue(lines.any { it.contains("研究資料完整度指數：") })
    }

    @Test
    fun candidateDiagnosticsShowNumbersWithoutClaimingDecodedEcg() {
        val rows = listOf(
            (1..10).map(Int::toDouble) + listOf(0.0, 100.0, 0.0, 0.0, 0.0, 0.0),
            (2..11).map(Int::toDouble) + listOf(0.0, 110.0, 0.0, 0.0, 0.0, 0.0)
        ).map { it.joinToString(",") }
        val decoded = EcgHalEventResearchDecoder.decode(rows.joinToString(";"), emptyList())
        val lines = EcgResearchDisplay.candidateInspectionLines(
            decoded.candidateValueCount,
            decoded.layoutComparison,
            decoded.status,
            decoded.inspectedFieldValueCount
        )

        assertTrue(lines.any { it.contains("原始事件結構：2 列、16 欄") && it.contains("有限候選數值 20 個") && it.contains("不是已解碼 ECG 樣本") })
        assertTrue(lines.any { it.contains("原始事件列欄位數值 32 個") && it.contains("不等於已解碼樣本") })
        assertTrue(lines.any { it.contains("欄位映射：未驗證") && it.contains("未產生 bpm、RR、HRV 或 mV") })
        assertTrue(lines.any { it.contains("排列假設相鄰差絕對中位數：列優先") && it.contains("欄優先") && it.contains("不能確認映射") })
        assertTrue(lines.any { it.contains("第 12 欄 +10 假設：1 次") && it.contains("語義未驗證") })
        assertTrue(lines.any { it.contains("各欄描述統計") && it.contains("非 ECG 樣本／時間軸") })
        assertTrue(lines.any { it.contains("欄位 1：有限值 2") && it.contains("變動 1/1 次") })
        assertTrue(lines.any { it.contains("欄位 13：有限值 2") && it.contains("0 值 2") })
        assertEquals(16, lines.count { it.startsWith("欄位 ") })
        assertTrue(lines.none { it.contains("已解碼 ECG 樣本") && it.contains("欄位 1") })
    }

    @Test
    fun packetStructureConclusionIsShownSeparatelyFromZeroDecodedSamples() {
        val eventCount = 4
        val rows = List(eventCount) { event ->
            (0 until 10).map { field -> (event * 10 + field).toDouble() } +
                listOf(1.0, (100 + event * 10).toDouble(), 0.0, 0.0, 0.0, 0.0)
        }.map { it.joinToString(",") }
        val result = EcgHalEventResearchDecoder.decode(rows.joinToString(";"), List(eventCount) { 0L })
        val lines = EcgResearchDisplay.candidateInspectionLines(
            result.candidateValueCount,
            result.layoutComparison,
            result.status,
            result.inspectedFieldValueCount,
            result.eventCount
        )

        assertEquals(0, result.decodedSampleCount)
        assertTrue(lines.first().contains("原始事件結構：4 列、16 欄"))
        assertTrue(lines.any { it.contains("已觀察到可重現的封包欄位模式") })
        assertTrue(lines.any { it.contains("第 13–16 欄均為 0") })
        assertTrue(lines.any { it.contains("欄位映射：未驗證") && it.contains("未產生 bpm、RR、HRV 或 mV") })
        val summary = EcgResearchDisplay.packetStructureSummaryLines(result.eventCount, result.layoutComparison)
        assertTrue(summary.first().contains("4 列、16 欄") && summary.first().contains("解碼 ECG 樣本仍為 0"))
        assertTrue(summary.last().contains("封包欄位模式") && summary.last().contains("不輸出 bpm、RR、HRV、mV"))
    }

    @Test
    fun missingOrRejectedRowsDoNotReportZeroCandidateValues() {
        val lines = EcgResearchDisplay.candidateInspectionLines(null, null, "no_rows")
        val rejected = EcgResearchDisplay.candidateInspectionLines(null, null, "invalid_numeric_field")

        assertEquals(2, lines.size)
        assertTrue(lines.first().contains("未取得可檢查候選數值"))
        assertTrue(lines.last().contains("欄位映射：未驗證"))
        assertTrue(rejected.first().contains("invalid_numeric_field"))
        assertTrue(rejected.none { it.contains("候選值 0 個") })
    }

    @Test
    fun rawRowRetentionTextDoesNotTreatUnknownAsComplete() {
        assertTrue(EcgResearchDisplay.rawRowsRetentionLabel("legacy_app_split_unverified").contains("狀態未確認"))
        assertTrue(EcgResearchDisplay.rawRowsRetentionLabel("complete_for_observed_events").contains("完整保存"))
        assertTrue(EcgResearchDisplay.rawRowsRetentionLabel("rejected_size_limit").contains("整批原始列已拒收"))
        assertTrue(EcgResearchDisplay.rawRowsPanelTitle(false).contains("保存與檢查狀態"))
        assertTrue(EcgResearchDisplay.rawRowsPanelTitle(true).contains("最新一列預覽"))
    }
}
