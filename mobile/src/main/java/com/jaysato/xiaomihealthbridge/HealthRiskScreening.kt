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
        return when {
            result.reasonCode == "PPG_GAP_PRESENT" -> Result(
                "脈波檢測", "本次無效，請重測",
                "這次光學脈波有中斷，無法提供可用的脈波形態結果。",
                "戴緊手錶、保持手腕靜止，再量一次。"
            )
            !ppgFresh || result.appNormalizedApgIndex == null -> Result(
                "脈波檢測", "本次未取得結果",
                "這次沒有近期且完整的光學脈波。",
                "戴緊手錶、保持靜止後重新量測。"
            )
            else -> Result(
                "脈波檢測", "本次脈波可檢視",
                "手錶已保存這段脈波變化；不能由此判斷血管年齡。",
                "可查看波形；若想了解血管健康，請使用經驗證的檢查方式。"
            )
        }
    }

    private fun bloodPressure(cuff: SharedPreferences, now: Long): Result {
        val time = cuff.getLong("manual_cuff_time", 0L)
        val systolic = cuff.getFloat("manual_cuff_sbp", Float.NaN)
        val diastolic = cuff.getFloat("manual_cuff_dbp", Float.NaN)
        if (time <= 0L || now - time !in 0..SEVEN_DAYS_MILLIS || systolic !in 70f..250f || diastolic !in 35f..150f) {
            return Result("高血壓風險", "需要新鮮袖帶讀值", "只接受 7 天內手動輸入的袖帶紀錄；本 App 不由腕錶光學資料推算血壓。", "坐姿休息 5 分鐘後，用經驗證上臂式血壓計重複測量並記錄。")
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
            !usable -> Result("心房顫動／心律不整", "目前不可判讀", "這次沒有可用的心律結果；請在手錶重新量測。", "若有持續心悸、胸痛、喘或暈厥，請就醫。")
            pattern == ResearchPattern.IRREGULAR -> Result("心房顫動／心律不整", "心律跡象需留意", "這段心律變化不規則；手錶結果不能確診心房顫動。", "保持靜止重測；若重複出現或身體不適，請就醫確認。")
            pattern == ResearchPattern.REGULAR -> Result("心房顫動／心律不整", "這段心律較規則", "這次沒有看到明顯不規則跡象；單次量測不能排除心律問題。", "若仍有心悸或其他不適，請就醫確認。")
            else -> Result("心房顫動／心律不整", "研究結果未定", "這次沒有可辨識的心律分類。", "保持靜止重測；持續不適請就醫。")
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
