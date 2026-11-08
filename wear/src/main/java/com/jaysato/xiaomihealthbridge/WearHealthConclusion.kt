package com.jaysato.xiaomihealthbridge

/**
 * Short non-diagnostic conclusions on watch after measurement,
 * using the same clinical bands as the phone engine where applicable.
 */
object WearHealthConclusion {
    fun forHeartRateBpm(bpm: Double?): String {
        if (bpm == null || !bpm.isFinite()) return "這次沒有量到心率，請重新量測。"
        return when {
            bpm in 60.0..100.0 -> "在成人靜息心率參考範圍內（60–100 bpm）。"
            bpm in 40.0..59.0 -> "低於成人靜息參考範圍；休息後再量一次。"
            bpm in 101.0..180.0 -> "高於成人靜息參考範圍；若剛活動過，休息後再量。"
            else -> "這筆心率超出常見量測範圍，請重新量測。"
        }
    }

    fun forTemperatureRawValue(value: Double?): String =
        if (value != null && value.isFinite()) "已收到裝置原始值；單位未驗證，不作體溫比較"
        else "尚未收到有限的溫度原始值"

    fun forEcg(samples: Int, rrCount: Int, bpm: Double?): String {
        if (samples <= 0) return "結論：尚無 ECG"
        val hr = forHeartRateBpm(bpm)
        return if (rrCount > 0) "結論：研究 RR $rrCount · $hr（非醫療 AF）"
        else "結論：有波形·RR 不足·請重測"
    }

    fun forHrv(rmssdMs: Double?): String {
        if (rmssdMs == null || !rmssdMs.isFinite()) return "請重新量測心率變化"
        return when {
            rmssdMs in 15.0..70.0 -> "HRV ${"%.0f".format(rmssdMs)} ms · 在程式研究參考範圍內"
            rmssdMs < 15.0 -> "HRV ${"%.0f".format(rmssdMs)} ms · 低於程式研究參考範圍"
            else -> "HRV ${"%.0f".format(rmssdMs)} ms · 高於程式研究參考範圍"
        }
    }

    fun forPressure(hpa: Double?, altM: Double?): String {
        if (hpa == null && altM == null) return "結論：尚無氣壓"
        return "結論：氣壓資料已取得；高度僅為標準大氣公式估算，非實際海拔／爬升"
    }

    fun forBioz(samples: Int, worn: Boolean?): String = when {
        samples <= 0 -> "結論：尚無 BioZ"
        worn == false -> "結論：離腕時的 BioZ 原始事件僅供研究；不輸出體脂％"
        worn == null -> "結論：佩戴狀態未確認；BioZ 原始事件僅供研究，不輸出體脂％"
        else -> "結論：已佩戴且有 BioZ 原始事件；電極接觸與單位未驗證，不輸出體脂％"
    }

    fun forPpg(samples: Int, pulseBpm: Double?): String {
        if (samples <= 0) return "結論：尚無 PPG"
        if (pulseBpm == null || !pulseBpm.isFinite()) {
            return "結論：已保留 PPG 原始波形；取樣時基／通道映射未驗證，不輸出脈率、HRV、PI 或 SpO₂。"
        }
        return "結論：光學波形已取 · ${forHeartRateBpm(pulseBpm)}"
    }

}
