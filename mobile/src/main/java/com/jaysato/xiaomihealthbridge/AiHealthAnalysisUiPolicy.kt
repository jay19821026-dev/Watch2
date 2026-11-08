package com.jaysato.xiaomihealthbridge

internal object AiHealthAnalysisUiPolicy {
    private val technicalPanelTitles = setOf(
        "日常狀態參考",
        "本機規則檢查面向",
        "資料觀察",
        "Health Connect｜近 7 日活動觀測",
        "ECG＋BioZ 輔助分析"
    )

    fun isTechnicalDetailsPanel(title: String?): Boolean = title in technicalPanelTitles
}
