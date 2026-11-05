package com.jaysato.xiaomihealthbridge

internal object HrvSourceValidity {
    private val opticalSources = setOf("ppg", "max_hr", "max_heart_rate", "lifeq_ott")

    fun unavailableReason(
        sourceKind: String,
        measuredAt: Long,
        timestampSource: String? = "android_sensor_event_elapsed_realtime",
        now: Long,
        status: String,
        qualityScore: Int,
        sampleRateHz: Float,
        sampleCount: Int,
        rrCount: Int,
        channelMappingVerified: Boolean,
        wearQualified: Boolean? = null,
        waveformEncoding: String? = null
    ): String {
        if (sourceKind == "ecg") return "目前資料來自 ECG；此頁不以未校準 ECG 推算 HRV。"
        if (sampleCount <= 0) {
            return if (measuredAt > 0L) "有量測時間但沒有可用樣本；請重新量測並確認手錶回報事件。"
            else "尚未收到可用的 HRV／PPG 樣本；請啟動一次手錶量測。"
        }
        if (!HealthAnalysisFreshnessPolicy.isFreshSensor(measuredAt, timestampSource, now)) {
            return "已收到 $sampleCount 筆樣本，但量測時間不在目前 10 分鐘分析窗內；歷史資料保留但不參與即時判讀。"
        }
        val unmet = buildList {
            if (wearQualified != true) add("佩戴狀態未確認")
            if (sourceKind !in opticalSources) add("來源未支援 HRV 換算")
            if (status !in setOf("valid_signal", "has_data")) add("訊號狀態未通過")
            if (!channelMappingVerified) add("通道映射未驗證")
            if (qualityScore < 70) add("品質分數未達工程門檻")
            if (sampleRateHz <= 64f) add("取樣率未達工程門檻")
            if (rrCount < MIN_HRV_RR_COUNT) add("有效 RR 間期不足（${rrCount.coerceAtLeast(0)}，至少 $MIN_HRV_RR_COUNT）")
        }
        return "已收到 $sampleCount 筆近期原始樣本，但${unmet.joinToString("、")}；不輸出 HRV 數值。"
    }

    fun isFreshAndQualified(
        sourceKind: String,
        measuredAt: Long,
        timestampSource: String? = "android_sensor_event_elapsed_realtime",
        now: Long,
        status: String,
        qualityScore: Int,
        sampleRateHz: Float,
        sampleCount: Int,
        rrCount: Int,
        channelMappingVerified: Boolean = false,
        wearQualified: Boolean? = null,
        waveformEncoding: String? = null
    ): Boolean {
        if (!HealthAnalysisFreshnessPolicy.isFreshSensor(measuredAt, timestampSource, now) || sampleCount < MIN_SAMPLE_COUNT) return false
        return when (sourceKind) {
            "ecg" -> wearQualified == true &&
                EcgPayloadInterpretationPolicy.canInterpretAsWaveform(waveformEncoding) &&
                qualityScore >= 60 && rrCount >= MIN_HRV_RR_COUNT
            in opticalSources -> channelMappingVerified && status in setOf("valid_signal", "has_data") && qualityScore >= 70 &&
                sampleRateHz > 64f && rrCount >= MIN_HRV_RR_COUNT && wearQualified == true
            else -> false
        }
    }

    private const val MIN_SAMPLE_COUNT = 32
    private const val MIN_HRV_RR_COUNT = 5
}
