package com.jaysato.xiaomihealthbridge

object ResearchClinicalEvidence {
    data class Method(
        val title: String,
        val method: String,
        val minimumInput: String,
        val output: String,
        val citation: String,
        val boundary: String
    )

    fun forModality(kind: String): List<Method> = when (kind) {
        "ecg" -> listOf(
            Method(
                "QRS／R 峰偵測",
                "帶通概念、微分、平方與移動積分的低耗電 Pan–Tompkins 流程。",
                "欄位映射、取樣時間與增益已由裝置文件或同步參考設備驗證的連續波形；目前本裝置尚未滿足。",
                "條件未滿足前僅保留事件與欄位候選值；驗證後才可評估候選 R 峰與研究 RR／心率。",
                "Pan J, Tompkins WJ. IEEE TBME 1985. DOI 10.1109/TBME.1985.325532",
                "未取得原廠導聯與 raw→mV 校準，不分析 ST、QT、缺血或形態診斷。"
            ),
            Method(
                "ECG 訊號品質 SQI",
                "以有效心率、RR 合理性、波形穩定度與模板一致度判斷是否適合計算。",
                "建議至少 10 秒連續波形；體動與接觸不良必須降級。",
                "0–100 品質分數與可否解讀狀態。",
                "Orphanidou C et al. IEEE JBHI 2015. DOI 10.1109/JBHI.2014.2338351",
                "SQI 只能排除不可靠片段，不能證明沒有心律疾病。"
            ),
            Method(
                "短時 HRV",
                "由正常候選 RR 計算 mean RR、SDNN、RMSSD 與 pNN50。",
                "至少數個可信 RR；正式短時 HRV 通常需標準化靜止紀錄，較長紀錄更可靠。",
                "自主神經恢復趨勢的研究指標。",
                "ESC/NASPE Task Force. Circulation 1996;93:1043–1065.",
                "超短腕式紀錄不可直接套用五分鐘或二十四小時臨床常模。"
            ),
            Method(
                "不規則節律研究篩檢",
                "計算 RR 與 ΔRR 的變異係數；只在品質合格時顯示研究警示。",
                "需要連續且足量的可信 RR；目前短片段僅供提示。",
                "規則、可能不規則或建議重測。",
                "Tateno K, Glass L. Med Biol Eng Comput 2001. DOI 10.1007/BF02345439",
                "不是 AF 診斷；論文分類器、資料長度與本裝置尚未完成臨床驗證。"
            )
        )
        "ppg" -> listOf(
            Method(
                "PPG 訊號品質 SQI",
                "依脈搏週期、模板一致度、合理心率與波形穩定度判定可用性。",
                "連續、貼合且低體動的光學波形。",
                "品質分數、接觸提示與是否允許脈率分析。",
                "Orphanidou C et al. IEEE JBHI 2015. DOI 10.1109/JBHI.2014.2338351",
                "品質合格不代表可診斷疾病。"
            ),
            Method(
                "PPG 形態品質",
                "以偏態、灌流、非平穩性與脈波偵測一致度輔助辨識雜訊。",
                "建議至少數十秒穩定 PPG；運動中需同步加速度排除體動。",
                "可用／需重測與研究脈率。",
                "Elgendi M. et al. Bioengineering 2016/2017; PMID 28952584.",
                "未取得 Xiaomi LED、光電二極體與 SpO₂ 校準曲線，不輸出醫療血氧。"
            )
        )
        else -> emptyList()
    }
}
