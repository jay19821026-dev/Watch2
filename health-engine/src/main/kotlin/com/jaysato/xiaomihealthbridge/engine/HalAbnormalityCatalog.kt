package com.jaysato.xiaomihealthbridge.engine

enum class HalAbnormality(val code: String, val action: String) {
    NO_EVENTS("no_events", "保留歷史證據；重新啟動前景量測"),
    NOT_MEASURED("not_measured", "標記本輪未量測，不改寫成零"),
    NEEDS_OEM_MODE("needs_oem_mode", "顯示需要原廠量測頁；不可繞過鎖定"),
    PERMISSION_DENIED("permission_denied", "顯示授權入口並保留既有快照"),
    UNMAPPED("research_signal_unscaled", "執行格式驗證後輸出相對／索引域波形"),
    UNCALIBRATED("uncalibrated", "輸出品質與相對指標，不轉換物理單位"),
    UNKNOWN_TIMESTAMP("timestamp_missing", "使用索引時間軸並標記 estimated_timeline"),
    ROW_SHAPE_MISMATCH("row_shape_mismatch", "拒絕該列並保留原始事件與拒絕原因"),
    METADATA_LAYOUT_MISMATCH("metadata_layout_mismatch", "不猜欄位排列，等待已驗證格式"),
    SAMPLE_COUNTER_MISMATCH("sample_counter_mismatch", "保留事件但停止連續波形合併"),
    REJECTED_SIZE("rejected_size_limit", "拒絕超限封包，避免截斷資料冒充完整"),
    UI_SUPPRESSED("ui_suppressed", "顯示研究結果與狀態，不丟棄底層證據")
}

data class HalAbnormalityReport(
    val matched: List<HalAbnormality>,
    val unknownCodes: List<String>,
    val actions: List<String>
)

object HalAbnormalityCatalog {
    fun classify(codes: Collection<String>): HalAbnormalityReport {
        val normalized = codes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        val matched = normalized.mapNotNull { code -> HalAbnormality.entries.firstOrNull { it.code == code } }
        val unknown = normalized.filter { code -> matched.none { it.code == code } }
        return HalAbnormalityReport(
            matched = matched,
            unknownCodes = unknown,
            actions = matched.map { it.action }.distinct()
        )
    }
}
