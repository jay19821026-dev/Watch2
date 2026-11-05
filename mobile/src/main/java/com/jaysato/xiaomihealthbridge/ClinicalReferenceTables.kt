package com.jaysato.xiaomihealthbridge

/**
 * Published clinical / research reference bands for wearable signal comparison.
 * Sources are research literature and device white papers — not diagnostic cutoffs.
 *
 * References (non-exhaustive):
 * - PPG/ECG SQI: Orphanidou et al., IEEE JBHI 2015 (doi:10.1109/JBHI.2014.2338351)
 *   Template correlation thresholds: ECG 0.66, PPG 0.86 for “good” segments
 * - Ambulatory ECG: IEC 60601-2-47 / AAMI — ≥500 samples/s per channel; ≤5 μV/LSB guidance
 * - SpO₂: Cleveland Clinic / WHO training — healthy adult sea-level ~95–100%; <92% seek care; <90% emergency
 * - SpO₂ device validation often uses mean absolute difference ≤3% vs reference (telehealth validation studies)
 * - Resting HR: adult ~60–100 bpm (Cleveland Clinic); some guides cite pulse 50–99 with SpO₂ 95–100 as acceptable
 * - Wrist skin temp ~33–37 °C (Polar Nightly Skin Temperature); core fever typically ≥38.0 °C oral
 * - BIA: Z²=R²+Xc²; BIVA hydration via R/H·Xc/H ellipses (Piccoli et al.); segmental wrist ≠ whole-body
 */
object ClinicalReferenceTables {
    /** Orphanidou et al. average template correlation for “good” ECG SQI. */
    const val ECG_SQI_CORR_THRESHOLD = 0.66

    /** Orphanidou et al. average template correlation for “good” PPG SQI. */
    const val PPG_SQI_CORR_THRESHOLD = 0.86

    data class Band(
        val id: String,
        val label: String,
        val min: Double,
        val max: Double,
        val unit: String,
        val source: String,
        val note: String
    ) {
        fun contains(value: Double): Boolean = value.isFinite() && value in min..max
        fun compare(value: Double?): String {
            if (value == null || !value.isFinite()) return "$label：無讀值（參考 $min–$max $unit）"
            return when {
                contains(value) -> "$label：${fmt(value)} $unit · 落在公開參考 $min–$max $unit"
                value < min -> "$label：${fmt(value)} $unit · 低於公開參考下限 $min $unit"
                else -> "$label：${fmt(value)} $unit · 高於公開參考上限 $max $unit"
            }
        }
    }

    /** Adult resting pulse / heart rate commonly cited clinical resting band. */
    val RESTING_HR_BPM = Band(
        id = "resting_hr",
        label = "安靜心率／脈率",
        min = 60.0,
        max = 100.0,
        unit = "bpm",
        source = "成人安靜心率常用臨床區間（非逐拍診斷）",
        note = "運動、焦慮、藥物、心律不整會使讀值離開此帶"
    )

    /** Plausible wearable instantaneous HR window before rejecting as noise. */
    val PLAUSIBLE_HR_BPM = Band(
        id = "plausible_hr",
        label = "生理可置信心率",
        min = 40.0,
        max = 180.0,
        unit = "bpm",
        source = "穿戴式瞬時心率合理窗（研究濾噪）",
        note = "超出此窗多半為偽峰或接觸不良"
    )

    /** PPG sample-rate guidance for pulse-rate extraction (not morphology). */
    val PPG_RATE_HZ = Band(
        id = "ppg_fs",
        label = "PPG 取樣率",
        min = 25.0,
        max = 500.0,
        unit = "Hz",
        source = "穿戴 PPG 脈率提取常用 ≥25 Hz",
        note = "過低不利 RR／脈率穩定度"
    )

    /**
     * Ambulatory ECG systems per IEC 60601-2-47 commonly require ≥500 samples/s.
     * Wrist consumer ECG often samples far below; treat as research waveform only.
     */
    val AMBULATORY_ECG_RATE_HZ = Band(
        id = "ecg_fs",
        label = "動態心電取樣率",
        min = 500.0,
        max = 2000.0,
        unit = "Hz",
        source = "IEC 60601-2-47 動態心電系統取樣指引",
        note = "腕式消費級常遠低於此；不可當診斷 ECG"
    )

    /** Practical research floor for readable wrist ECG morphology / rate estimates. */
    val WRIST_ECG_USABLE_RATE_HZ = Band(
        id = "wrist_ecg_fs",
        label = "腕式 ECG 可用取樣率",
        min = 100.0,
        max = 1000.0,
        unit = "Hz",
        source = "腕式單導研究實務下限（非 IEC 合規）",
        note = "<100 Hz 心率估計誤差顯著升高"
    )

    /** Whole-body 50 kHz wrist–ankle resistance typical adult band (device-dependent). */
    val WHOLE_BODY_R_OHM = Band(
        id = "bia_r",
        label = "全身 BIA 電阻 R",
        min = 300.0,
        max = 800.0,
        unit = "Ω",
        source = "50 kHz 四肢 BIA 常見成人 R 帶（裝置相依）",
        note = "需身高正規化才能做 BIVA；此處僅粗對照"
    )

    /** Whole-body reactance typical order. */
    val WHOLE_BODY_X_OHM = Band(
        id = "bia_x",
        label = "全身 BIA 電抗 Xc",
        min = 30.0,
        max = 80.0,
        unit = "Ω",
        source = "50 kHz 四肢 BIA 常見成人 Xc 帶（裝置相依）",
        note = "相位角通常約 4–8°"
    )

    /** Local wrist / segmental impedance is often much higher than whole-body. */
    val SEGMENTAL_WRIST_Z_OHM = Band(
        id = "seg_z",
        label = "腕部局部阻抗 |Z|",
        min = 500.0,
        max = 12000.0,
        unit = "Ω",
        source = "腕式 BIOZ 局部通道經驗帶（非全身 BIA）",
        note = "不可直接換算體脂／體水％"
    )

    /** Polar / WHOOP cited healthy adult wrist skin temperature. */
    val WRIST_SKIN_TEMP_C = Band(
        id = "skin_temp",
        label = "腕部體表溫度",
        min = 33.0,
        max = 37.0,
        unit = "°C",
        source = "Polar／WHOOP 等公開白皮書：健康成人腕表體表約 33–37°C",
        note = "≠ 核心體溫；環境與貼合影響大"
    )

    /** Oral/clinical fever threshold for comparison text only. */
    val ORAL_FEVER_C = Band(
        id = "oral_fever",
        label = "口腔發燒門檻（對照用）",
        min = 38.0,
        max = 42.0,
        unit = "°C",
        source = "臨床常用口腔發燒 ≥38.0°C",
        note = "腕表讀值不可用來判定發燒"
    )

    /** WHO / clinical pulse oximetry: healthy adult SpO₂ at sea level typically ≥95%. */
    val SPO2_NORMAL_PCT = Band(
        id = "spo2_normal",
        label = "血氧飽和度 SpO₂",
        min = 95.0,
        max = 100.0,
        unit = "%",
        source = "WHO 脈搏血氧訓練手冊：健康成人海平面約 95–100%",
        note = "海拔、貧血、膚色／灌注會影響；腕式光學 SpO₂ 非臨床血氣"
    )

    /** ACC/AHA 2017 adult SBP normal band (also requires DBP <80 for "normal"). */
    val AHA_SBP_NORMAL_MMHG = Band(
        id = "aha_sbp_normal",
        label = "ACC/AHA 收縮壓正常帶",
        min = 90.0,
        max = 119.0,
        unit = "mmHg",
        source = "ACC/AHA 2017 高血壓指引",
        note = "光學估計不可替代袖帶血壓計"
    )

    val AHA_DBP_NORMAL_MMHG = Band(
        id = "aha_dbp_normal",
        label = "ACC/AHA 舒張壓正常帶",
        min = 60.0,
        max = 79.0,
        unit = "mmHg",
        source = "ACC/AHA 2017 高血壓指引",
        note = "光學估計不可替代袖帶血壓計"
    )

    /** Persistent SpO₂ <90% is treated as hypoxemia emergency threshold in WHO training materials. */
    val SPO2_HYPOXEMIA_PCT = Band(
        id = "spo2_hypox",
        label = "低血氧緊急門檻（對照）",
        min = 0.0,
        max = 90.0,
        unit = "%",
        source = "WHO：SpO₂ <90% 視為臨床緊急情境（需就醫評估）",
        note = "穿戴讀值僅供參考，不可代替急診判斷"
    )

    /** Investigation often prompted below ~92% on room air at sea level (digital medicine / guidelines). */
    val SPO2_REVIEW_PCT = Band(
        id = "spo2_review",
        label = "血氧需進一步評估帶",
        min = 90.0,
        max = 94.0,
        unit = "%",
        source = "文獻常用：海平面室空氣 SpO₂ 持續 <92% 宜進一步評估",
        note = "非診斷切點；慢性肺疾患者目標可能不同"
    )

    /** Short-term resting SDNN order from ELSA-Brasil healthy adults (~40 ms mean). */
    val HRV_SDNN_MS = Band(
        id = "hrv_sdnn",
        label = "短時安靜 SDNN",
        min = 20.0,
        max = 80.0,
        unit = "ms",
        source = "ELSA-Brasil 等短時安靜 HRV：成人 SDNN 常見約數十 ms，隨年齡下降",
        note = "需標準化 5 分鐘 ECG；腕式光學 HRV 不可直接套用"
    )

    /** Short-term RMSSD broad adult research span. */
    val HRV_RMSSD_MS = Band(
        id = "hrv_rmssd",
        label = "短時安靜 RMSSD",
        min = 15.0,
        max = 70.0,
        unit = "ms",
        source = "短時安靜 RMSSD 研究常見約 15–70 ms（年齡／性別相依）",
        note = "宜對照個人基線；非疾病診斷"
    )

    /** Adult sleep duration commonly recommended ~7–9 h. */
    val SLEEP_HOURS = Band(
        id = "sleep_h",
        label = "成人睡眠時數",
        min = 7.0,
        max = 9.0,
        unit = "h",
        source = "成人常見建議睡眠約 7–9 小時（非 PSG 分期）",
        note = "HC 總時長≠深睡／REM 分期"
    )

    /** WHO-ish daily steps activity encouragement band (approximate public-health messaging). */
    val DAILY_STEPS = Band(
        id = "steps",
        label = "每日步數",
        min = 7000.0,
        max = 10000.0,
        unit = "步",
        source = "公共衛生常引用約 7,000–10,000 步／日活動量級",
        note = "非臨床診斷；年長者目標可較低"
    )

    /** Adult BMI normal band (WHO). */
    val BMI = Band(
        id = "bmi",
        label = "BMI",
        min = 18.5,
        max = 24.9,
        unit = "kg/m²",
        source = "WHO 成人 BMI 正常範圍約 18.5–24.9",
        note = "亞洲部分指引另有較低切點；非單一健康指標"
    )

    /** Typical adult gait step length order. */
    val STEP_LENGTH_M = Band(
        id = "step_len",
        label = "平均步長",
        min = 0.55,
        max = 0.85,
        unit = "m/步",
        source = "成人步行常見步長約 0.6–0.8 m 量級",
        note = "受身高、速度、地形影響"
    )

    /** Weekly moderate–vigorous activity minutes often cited (≈150 min MVPA). */
    val WEEKLY_MET_MINUTES = Band(
        id = "met_min",
        label = "每週 MET 分鐘",
        min = 500.0,
        max = 1000.0,
        unit = "MET·min",
        source = "約對應成人每週中等強度活動建議量級（粗對照）",
        note = "由心率估 MET 會有誤差"
    )

    fun spo2Compare(percent: Double?): List<String> = buildList {
        add(SPO2_NORMAL_PCT.compare(percent))
        if (percent == null || !percent.isFinite()) return@buildList
        when {
            percent < SPO2_HYPOXEMIA_PCT.max ->
                add("對照：低於 ${SPO2_HYPOXEMIA_PCT.max.toInt()}% 低血氧緊急門檻（WHO 訓練材料）；請就醫，腕式讀值不可替代急診")
            percent < 95.0 ->
                add("對照：低於健康成人常見下限 95%；若持續偏低請用指夾血氧／就醫確認")
            else ->
                add("對照：落在健康成人海平面常見 95–100% 帶（仍受海拔／灌注影響）")
        }
    }

    fun hrvCompare(sdnnMs: Double?, rmssdMs: Double?): List<String> = buildList {
        sdnnMs?.let { add(HRV_SDNN_MS.compare(it)) }
        rmssdMs?.let { add(HRV_RMSSD_MS.compare(it)) }
        add("短時 HRV 參考來自安靜 ECG 研究；腕式／運動中不可直接診斷自主神經疾病")
    }

    fun sleepCompare(hours: Double?): List<String> = listOf(SLEEP_HOURS.compare(hours))

    fun stepsCompare(steps: Double?): List<String> = listOf(DAILY_STEPS.compare(steps))

    fun bmiCompare(bmi: Double?): List<String> = listOf(BMI.compare(bmi))

    fun gaitCompare(metersPerStep: Double?): List<String> = listOf(STEP_LENGTH_M.compare(metersPerStep))

    fun metMinutesCompare(metMin: Double?): List<String> = listOf(WEEKLY_MET_MINUTES.compare(metMin))

    /**
     * Unified clinical comparison for Health Connect / dashboard metrics.
     * @param metric keys aligned with MetricDetailActivity / Insights
     */
    fun compareMetric(metric: String, value: Double?, extras: Map<String, Double?> = emptyMap()): List<String> =
        when (metric) {
            "heart_rate", "whs_heart_rate", "heart_rate_raw", "max_hr" -> listOf(
                PLAUSIBLE_HR_BPM.compare(value),
                RESTING_HR_BPM.compare(value) + "（若為安靜狀態）"
            )
            "resting_heart_rate" -> listOf(RESTING_HR_BPM.compare(value))
            "oxygen" -> spo2Compare(value)
            "spo2_raw", "mi_spo2" -> listOf("原始 SpO₂ 通道單位未驗證，不能以此數值套用血氧百分比門檻")
            "temperature", "body_temperature_raw", "thermometer" -> skinTempCompare(value)
            "sleep" -> sleepCompare(value)
            "steps" -> stepsCompare(value)
            "distance" -> listOf("距離：${value?.let { "%.2f".format(it) } ?: "—"} km（無單一臨床切點；搭配步數／時間解讀）")
            "calories" -> listOf("活動熱量：${value?.let { "%.0f".format(it) } ?: "—"} kcal（依體重／強度；非診斷）")
            "weight" -> buildList {
                add("體重：${value?.let { "%.1f".format(it) } ?: "—"} kg")
                extras["bmi"]?.let { addAll(bmiCompare(it)) }
            }
            "hrv", "rmssd" -> hrvCompare(extras["sdnn"], value)
            "sdnn" -> hrvCompare(value, extras["rmssd"])
            "gait", "step_length" -> gaitCompare(value)
            "met_minutes" -> metMinutesCompare(value)
            "board_temperature" -> listOf("主板溫度：裝置熱狀態參考，非體溫")
            else -> listOf("尚無專用臨床帶：$metric＝${value ?: "—"}（僅原始／研究參考）")
        }

    enum class BiozMode { WHOLE_BODY_LIKE, SEGMENTAL_WRIST, UNKNOWN }

    fun classifyBioz(zOhm: Double?): BiozMode {
        if (zOhm == null || !zOhm.isFinite()) return BiozMode.UNKNOWN
        return when {
            zOhm in WHOLE_BODY_R_OHM.min..1000.0 -> BiozMode.WHOLE_BODY_LIKE
            zOhm in SEGMENTAL_WRIST_Z_OHM.min..SEGMENTAL_WRIST_Z_OHM.max -> BiozMode.SEGMENTAL_WRIST
            else -> BiozMode.UNKNOWN
        }
    }

    fun phaseAngleDeg(r: Double?, x: Double?): Double? {
        if (r == null || x == null || !r.isFinite() || !x.isFinite() || abs(r) < 1e-6) return null
        return Math.toDegrees(kotlin.math.atan2(x, r))
    }

    fun hrVsResting(bpm: Double?): String = RESTING_HR_BPM.compare(bpm)

    fun skinTempCompare(celsius: Double?): List<String> = buildList {
        add(WRIST_SKIN_TEMP_C.compare(celsius))
        if (celsius != null && celsius.isFinite()) {
            add(
                if (celsius >= ORAL_FEVER_C.min) {
                    "對照：已達／超過口腔發燒門檻 ${ORAL_FEVER_C.min}°C，但腕表體表≠核心體溫，請用合格體溫計"
                } else {
                    "對照：低於口腔發燒門檻 ${ORAL_FEVER_C.min}°C（仍不可用腕表排除發燒）"
                }
            )
        }
    }

    fun ecgRateCompare(sampleRateHz: Float?): String {
        if (sampleRateHz == null) {
            return "ECG 取樣率未提供；無法對照 IEC／腕式研究帶"
        }
        val fs = sampleRateHz.toDouble()
        return when {
            !fs.isFinite() || fs <= 0 -> "回報的 ECG 取樣率無效；無法對照 IEC／腕式研究帶"
            AMBULATORY_ECG_RATE_HZ.contains(fs) -> AMBULATORY_ECG_RATE_HZ.compare(fs)
            WRIST_ECG_USABLE_RATE_HZ.contains(fs) ->
                "取樣率 ${fmt(fs)} Hz · 達腕式研究可用帶，但低於 IEC 60601-2-47 動態心電 ≥500 Hz"
            else ->
                "取樣率 ${fmt(fs)} Hz · 低於腕式研究可用下限 ${WRIST_ECG_USABLE_RATE_HZ.min} Hz；心率估計宜審慎"
        }
    }

    fun ppgRateCompare(sampleRateHz: Float?): String {
        val researchBandNote = "一般脈率研究參考，非 Watch 2 Pro 規格或校準門檻"
        if (sampleRateHz == null) {
            return "PPG 取樣率未提供；無法對照一般研究參考 ${PPG_RATE_HZ.min}–${PPG_RATE_HZ.max} ${PPG_RATE_HZ.unit}（$researchBandNote）"
        }
        if (!sampleRateHz.isFinite() || sampleRateHz <= 0f) {
            val reportedValue = if (sampleRateHz == 0f) "0 Hz" else "非有限或非正數"
            return "PPG 回報取樣率無效（$reportedValue）；不與一般研究參考帶比較（$researchBandNote）"
        }
        return "${PPG_RATE_HZ.compare(sampleRateHz.toDouble())}（$researchBandNote）"
    }

    fun biozCompare(z: Double?, r: Double?, x: Double?): List<String> {
        val mode = classifyBioz(z)
        return buildList {
            when (mode) {
                BiozMode.WHOLE_BODY_LIKE -> {
                    add("通道型態：接近全身 BIA 量級（仍缺身高／電極模型）")
                    add(WHOLE_BODY_R_OHM.compare(r ?: z))
                    x?.let { add(WHOLE_BODY_X_OHM.compare(it)) }
                }
                BiozMode.SEGMENTAL_WRIST -> {
                    add("通道型態：腕部局部／節段 BIOZ（非全身 BIVA）")
                    add(SEGMENTAL_WRIST_Z_OHM.compare(z))
                    add("不可用全身脫水／體脂公式解讀此 |Z|")
                }
                BiozMode.UNKNOWN -> add(SEGMENTAL_WRIST_Z_OHM.compare(z))
            }
            phaseAngleDeg(r, x)?.let { pha ->
                add("相位角 ${fmt(pha)}°（全身常見約 4–8°；腕段不可直接套用）")
            }
        }
    }

    private fun abs(v: Double) = kotlin.math.abs(v)
    private fun fmt(v: Double): String = "%.2f".format(v)
}
