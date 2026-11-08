package com.jaysato.xiaomihealthbridge

/** Keeps sensor transport diagnostics out of the ordinary sync status screen. */
internal object ControlStatusDisplayPolicy {
    fun describe(message: String, success: Boolean): String {
        val raw = message.trim()
        if (raw.isEmpty()) return "手錶尚未回報量測狀態。"
        val metric = when {
            raw.contains("ECG", ignoreCase = true) -> "心電圖"
            raw.contains("SpO₂", ignoreCase = true) || raw.contains("spo2", ignoreCase = true) -> "血氧"
            raw.contains("PPG", ignoreCase = true) || raw.contains("光學", ignoreCase = true) -> "脈搏"
            raw.contains("BioZ", ignoreCase = true) -> "體成分"
            raw.contains("溫度") -> "腕部溫度"
            raw.contains("心率") -> "心率"
            else -> "手錶"
        }
        if (raw.contains("正在量測") || raw.contains("正在擷取")) {
            return "$metric 正在量測；請依手錶畫面操作。"
        }
        if (raw.contains("個事件") || raw.contains("原始欄位") || raw.contains("原始封包") ||
            raw.contains("筆 ·") || raw.contains("通道 · 範圍") || raw.contains("等待過久仍無數值") ||
            raw.contains("acquisitionStopReason") || raw.contains("time_limit_reached")) {
            return if (success) "$metric 量測已結束；請到結果頁查看可用讀值。"
                else "$metric 這次沒有取得可顯示的讀值；請確認佩戴後重測。"
        }
        if (raw.contains("感測器啟動失敗") || raw.contains("感測器建立失敗")) {
            return "$metric 無法開始量測；請稍後重試。"
        }
        return raw
    }
}
