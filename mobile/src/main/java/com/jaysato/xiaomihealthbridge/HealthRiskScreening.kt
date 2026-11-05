package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.content.SharedPreferences

object HealthRiskScreening {
    data class Result(val condition: String, val state: String, val explanation: String, val nextStep: String)

    fun evaluate(context: Context, now: Long = System.currentTimeMillis()): List<Result> {
        val raw = RawSensorStore.open(context)
        val cuff = context.getSharedPreferences(OpticalBpActivity.CUFF_PREFS, Context.MODE_PRIVATE)
        return listOf(
            bloodPressure(cuff, now),
            rhythm(raw, now),
            vascularResearch(raw, now),
            Result("睡眠呼吸中止風險", "目前不可判讀", "沒有裝置校準且連續整夜的血氧／呼吸事件資料；單次 SpO₂ 或體動不能用來估算 OSA。", "若有大聲打鼾、目擊呼吸中止或白天嗜睡，請諮詢醫療人員並考慮 PSG。"),
            Result("糖尿病／高血糖風險", "目前不可判讀", "手錶沒有經驗證的血糖或 HbA1c 測量；PPG、BioZ、活動量不能替代血液檢驗。", "依醫療人員建議以實驗室空腹血糖或 HbA1c 評估。")
        )
    }

    private fun vascularResearch(raw: SharedPreferences, now: Long): Result {
        val ppgTime = HealthAnalysisFreshnessPolicy.resolveStoredMeasurementTime(
            raw.getLong("ppg_time", 0L), raw.getLong("ppg_measurement_time", 0L)
        )
        val ppgFresh = HealthAnalysisFreshnessPolicy.isFreshSensor(
            ppgTime, raw.getString("ppg_timestamp_source", null), now
        )
        val result = VascularResearchEngine.fromPpg(
            qualityScore = raw.getInt("ppg_quality_score", 0),
            rawValueCount = raw.getInt("ppg_raw_value_count", 0),
            status = raw.getString("ppg_status", "").orEmpty(),
            waveform = PpgVascularInput.parseWaveform(
                raw.getString("ppg_display_waveform", null),
                raw.getString("ppg_waveform", null)
            ),
            sampleRateHz = raw.getFloat("ppg_sample_rate", 0f),
            channelMappingVerified = raw.getBoolean("ppg_channel_mapping_verified", false),
            wearQualified = raw.getBoolean("ppg_wear_qualified", false),
            timestampSource = raw.getString("ppg_timestamp_source", "UNKNOWN").orEmpty()
        )
        val qualityText = result.ppgQualityScore?.let { "$it/100" } ?: "未評分"
        val freshness = if (ppgFresh) "近期量測" else "非近期或時間來源未確認"
        val agiText = result.appNormalizedApgIndex?.let {
            "程式自訂 APG 形態指數 ${"%.3f".format(it)}（無單位；非標準 SDPPG AGI）"
        } ?: "程式自訂 APG 形態指數未計算（${result.reasonCode}）"
        return Result(
            "血管年齡研究",
            "$agiText · 血管年齡未估算 · PPG 品質 $qualityText · $freshness",
            "$agiText；有效脈搏 ${result.acceptedBeatCount} 段。這是程式自訂研究形態指數，不是標準 SDPPG AGI、血管年齡、PWV、疾病風險或診斷。PPG 訊號品質：$qualityText。資料時間：$freshness。來源：${result.source}。方法：${result.method}。限制：${result.limitations}。${result.trend}。",
            "此程式自訂形態指數不能換算血管年齡。此手錶尚無經驗證的 PWV／PTT 或血管年齡模型；如需血管評估，請使用經驗證的參考設備。若有腿痛、胸痛、呼吸困難或突發無力，請直接就醫。"
        )
    }

    private fun bloodPressure(cuff: SharedPreferences, now: Long): Result {
        val time = cuff.getLong("manual_cuff_time", 0L)
        val systolic = cuff.getFloat("manual_cuff_sbp", Float.NaN)
        val diastolic = cuff.getFloat("manual_cuff_dbp", Float.NaN)
        if (time <= 0L || now - time !in 0..SEVEN_DAYS_MILLIS || systolic !in 70f..250f || diastolic !in 35f..150f) {
            return Result("高血壓風險", "需要新鮮袖帶讀值", "只接受 7 天內手動輸入的袖帶紀錄；腕錶光學血壓估值不作疾病篩查。", "坐姿休息 5 分鐘後，用經驗證上臂式血壓計重複測量並記錄。")
        }
        val elevated = systolic >= 130f || diastolic >= 80f
        return if (elevated) {
            Result("高血壓風險", "單次讀值偏高（非診斷）", "最近袖帶紀錄 %.0f/%.0f mmHg；單次偏高不能診斷高血壓。".format(systolic, diastolic), "安靜休息後重測，連續多日仍偏高請與醫療人員討論。")
        } else {
            Result("高血壓風險", "此單次讀值未達偏高篩查線", "最近袖帶紀錄 %.0f/%.0f mmHg；單次正常讀值不能排除高血壓。".format(systolic, diastolic), "按醫療建議定期測量；若出現胸痛、喘或神經症狀請立即求助。")
        }
    }

    private fun rhythm(raw: SharedPreferences, now: Long): Result {
        val time = raw.getLong("ecg_time", 0L)
        val title = raw.getString("ecg_rhythm_title", null).orEmpty()
        val rr = raw.getInt("ecg_rhythm_rr_count", 0)
        val quality = raw.getInt("ecg_quality_score", 0)
        val sampleCount = raw.getInt("ecg_raw_value_count", 0)
        val sampleRate = raw.getFloat("ecg_sample_rate", 0f)
        val status = raw.getString("ecg_status", "").orEmpty()
        val wearQualified = if (raw.contains("ecg_wear_qualified")) raw.getBoolean("ecg_wear_qualified", false) else null
        val usable = isResearchRhythmEvidenceUsable(
            measuredAt = time,
            timestampSource = raw.getString("ecg_timestamp_source", null),
            now = now,
            qualityScore = quality,
            sampleCount = sampleCount,
            sampleRate = sampleRate,
            status = status,
            rrCount = rr,
            wearQualified = wearQualified,
            waveformEncoding = raw.getString("ecg_waveform_encoding", null)
        )
        val pattern = researchPattern(title)
        return when {
            !usable -> Result("心房顫動／心律不整", "目前不可判讀", "需要 24 小時內、品質分數至少 60、且有足夠 RR 間期的 ECG；目前腕錶波形未完成裝置級臨床驗證。", "若有持續心悸、胸痛、喘或暈厥，勿等待手錶判讀，請立即就醫。")
            pattern == ResearchPattern.IRREGULAR -> Result("心房顫動／心律不整", "研究訊號需醫療確認", "研究推斷心律不整；這不是 AF 診斷，也可能由雜訊、早搏或接觸不良造成。若有不舒服請盡速就醫。", "保持靜止重測；若重複出現，攜帶波形向醫療人員確認並以醫療 ECG 診斷。")
            pattern == ResearchPattern.REGULAR -> Result("心房顫動／心律不整", "此段 RR 間距規則", "單次腕式單導研究訊號不能排除陣發性心律不整。", "症狀優先於裝置結果；持續症狀請尋求醫療評估。")
            else -> Result("心房顫動／心律不整", "研究結果未定", "已收到符合輸入條件的資料，但尚無可辨識的 RR 規則性結果。", "保持靜止重測；持續不適請尋求醫療評估。")
        }
    }

    internal enum class ResearchPattern { REGULAR, IRREGULAR, UNKNOWN }

    internal fun researchPattern(title: String): ResearchPattern = when (title) {
        "RR 間距規則（研究；不排除心律異常）" -> ResearchPattern.REGULAR
        "RR 間距不規則（研究；非 AF 判讀）" -> ResearchPattern.IRREGULAR
        else -> ResearchPattern.UNKNOWN
    }

    internal fun isResearchRhythmEvidenceUsable(
        measuredAt: Long,
        timestampSource: String?,
        now: Long,
        qualityScore: Int,
        sampleCount: Int,
        sampleRate: Float,
        status: String,
        rrCount: Int,
        wearQualified: Boolean?,
        waveformEncoding: String?
    ): Boolean = HealthAnalysisFreshnessPolicy.isFreshSensor(measuredAt, timestampSource, now, DAY_MILLIS) &&
        qualityScore >= 60 && sampleCount >= 32 && sampleRate.isFinite() && sampleRate > 0f &&
        rrCount >= 5 && wearQualified == true &&
        status in setOf("research_signal_unscaled", "calibrated_external_reference", "has_data") &&
        EcgPayloadInterpretationPolicy.canInterpretAsWaveform(waveformEncoding)

    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private const val SEVEN_DAYS_MILLIS = 7 * DAY_MILLIS
}
