package com.jaysato.xiaomihealthbridge

internal object ResearchSignalEvidenceText {
    fun summarize(
        kind: String,
        sampleCount: Int,
        status: String,
        chartPoints: Int,
        unmapped: Boolean,
        researchDecoded: Boolean = false
    ): String {
        val signal = if (sampleCount > 0 || chartPoints > 0) "已收到原始證據" else "目前沒有可用原始證據"
        val mapping = if (researchDecoded && kind == "ecg") "近期子集已研究解碼，裝置級映射／校準未驗證"
            else if (unmapped) "欄位映射未確認" else "欄位映射狀態依裝置資料"
        val chart = if (chartPoints <= 1) "尚無足夠點數繪圖"
            else if (researchDecoded && kind == "ecg") "圖表顯示研究解碼波形，非醫療心電圖"
            else if (unmapped) "圖表保留未解碼原始趨勢"
            else "圖表顯示原始編碼趨勢，生理單位依來源驗證"
        return "規則式摘要：$signal；狀態=$status；$mapping；$chart。" +
            "僅整理證據與重測建議，不補造數值、時間、單位或醫療判定。"
    }
}
