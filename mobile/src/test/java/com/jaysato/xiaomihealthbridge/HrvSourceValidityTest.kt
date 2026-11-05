package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HrvSourceValidityTest {
    @Test
    fun unavailableReasonDistinguishesNoSamplesFromStaleData() {
        val noSamples = HrvSourceValidity.unavailableReason(
            sourceKind = "ppg", measuredAt = 0L, now = 10_000L, status = "not_measured",
            qualityScore = 0, sampleRateHz = 0f, sampleCount = 0, rrCount = 0,
            channelMappingVerified = false
        )
        val stale = HrvSourceValidity.unavailableReason(
            sourceKind = "ppg", measuredAt = 1L, now = 11 * 60_000L, status = "valid_signal",
            qualityScore = 90, sampleRateHz = 100f, sampleCount = 500, rrCount = 8,
            channelMappingVerified = true
        )

        assertTrue(noSamples.contains("尚未收到"))
        assertTrue(stale.contains("已收到 500 筆"))
        assertTrue(stale.contains("不在目前 10 分鐘分析窗"))
    }

    @Test
    fun unavailableReasonNamesTheFailedSignalQualificationGates() {
        val reason = HrvSourceValidity.unavailableReason(
            sourceKind = "ppg", measuredAt = 9_000L, now = 10_000L,
            status = "research_signal_unscaled", qualityScore = 40, sampleRateHz = 50f,
            sampleCount = 128, rrCount = 2, channelMappingVerified = false
        )

        assertTrue(reason.contains("訊號狀態未通過"))
        assertTrue(reason.contains("通道映射未驗證"))
        assertTrue(reason.contains("品質分數未達工程門檻"))
        assertTrue(reason.contains("取樣率未達工程門檻"))
        assertTrue(reason.contains("有效 RR 間期不足"))
    }

    @Test
    fun unavailableReasonDoesNotPresentEcgAsGeneralHrvInput() {
        val reason = HrvSourceValidity.unavailableReason(
            sourceKind = "ecg", measuredAt = 9_000L, now = 10_000L,
            status = "research_signal_unscaled", qualityScore = 0, sampleRateHz = 0f,
            sampleCount = 100, rrCount = 0, channelMappingVerified = false
        )

        assertTrue(reason.contains("目前資料來自 ECG"))
        assertTrue(reason.contains("不以未校準 ECG 推算 HRV"))
    }

    @Test
    fun freshTimestampAloneCannotQualifyHrv() {
        assertFalse(
            HrvSourceValidity.isFreshAndQualified(
                sourceKind = "",
                measuredAt = 9_000L,
                now = 10_000L,
                status = "valid_signal",
                qualityScore = 100,
                sampleRateHz = 128f,
                sampleCount = 512,
                rrCount = 20
            )
        )
    }

    @Test
    fun opticalHrvNeedsValidStatusQualitySamplingAndIntervals() {
        val valid = HrvSourceValidity.isFreshAndQualified(
            sourceKind = "ppg",
            measuredAt = 9_000L,
            now = 10_000L,
            status = "valid_signal",
            qualityScore = 75,
            sampleRateHz = 100f,
            sampleCount = 128,
            rrCount = 8,
            channelMappingVerified = true,
            wearQualified = true
        )
        val poorQuality = HrvSourceValidity.isFreshAndQualified(
            sourceKind = "ppg",
            measuredAt = 9_000L,
            now = 10_000L,
            status = "valid_signal",
            qualityScore = 25,
            sampleRateHz = 100f,
            sampleCount = 128,
            rrCount = 8,
            wearQualified = true
        )

        assertTrue(valid)
        assertFalse(poorQuality)
    }

    @Test
    fun opticalHrvRejectsUnknownOrOffWristStateEvenWhenOtherInputsPass() {
        assertFalse(HrvSourceValidity.isFreshAndQualified(
            sourceKind = "ppg", measuredAt = 9_000L, now = 10_000L,
            status = "valid_signal", qualityScore = 90, sampleRateHz = 100f,
            sampleCount = 128, rrCount = 8, channelMappingVerified = true,
            wearQualified = null
        ))
        assertFalse(HrvSourceValidity.isFreshAndQualified(
            sourceKind = "ppg", measuredAt = 9_000L, now = 10_000L,
            status = "valid_signal", qualityScore = 90, sampleRateHz = 100f,
            sampleCount = 128, rrCount = 8, channelMappingVerified = true,
            wearQualified = false
        ))
        val reason = HrvSourceValidity.unavailableReason(
            sourceKind = "ppg", measuredAt = 9_000L, now = 10_000L,
            status = "valid_signal", qualityScore = 90, sampleRateHz = 100f,
            sampleCount = 128, rrCount = 8, channelMappingVerified = true
        )
        assertTrue(reason.contains("佩戴狀態未確認"))
    }

    @Test
    fun opticalHrvIsRejectedWhenChannelMappingIsUnverified() {
        assertFalse(
            HrvSourceValidity.isFreshAndQualified(
                sourceKind = "max_hr",
                measuredAt = 9_000L,
                now = 10_000L,
                status = "valid_signal",
                qualityScore = 100,
                sampleRateHz = 100f,
                sampleCount = 128,
                rrCount = 8
            )
        )
    }

    @Test
    fun ecgResearchHrvNeedsEnoughRrIntervalsAndRecentMeasurement() {
        val fourIntervals = HrvSourceValidity.isFreshAndQualified(
            sourceKind = "ecg",
            measuredAt = 9_000L,
            now = 10_000L,
            status = "research_signal_unscaled",
            qualityScore = 80,
            sampleRateHz = 0f,
            sampleCount = 128,
            rrCount = 4
        )
        val fiveIntervals = HrvSourceValidity.isFreshAndQualified(
            sourceKind = "ecg",
            measuredAt = 9_000L,
            now = 10_000L,
            status = "research_signal_unscaled",
            qualityScore = 80,
            sampleRateHz = 0f,
            sampleCount = 128,
            rrCount = 5,
            wearQualified = true,
            waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        )
        val stale = HrvSourceValidity.isFreshAndQualified(
            sourceKind = "ecg",
            measuredAt = 1L,
            now = 11 * 60 * 1_000L,
            status = "research_signal_unscaled",
            qualityScore = 80,
            sampleRateHz = 0f,
            sampleCount = 128,
            rrCount = 8,
            wearQualified = true,
            waveformEncoding = EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING
        )

        assertFalse(fourIntervals)
        assertTrue(fiveIntervals)
        assertFalse(stale)
    }
}
