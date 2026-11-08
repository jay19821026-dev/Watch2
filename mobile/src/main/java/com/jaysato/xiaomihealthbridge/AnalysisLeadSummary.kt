package com.jaysato.xiaomihealthbridge

/** Short, evidence-bound conclusions for the first screen of local analysis. */
internal object AnalysisLeadSummary {
    data class Result(
        val headline: String,
        val recordedLines: List<String>,
        val researchLines: List<String>,
        val nextAction: String
    )

    fun from(items: List<HealthConclusionEngine.Item>): Result {
        val recorded = items.filter {
            it.available && it.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RECORDED_PHYSIOLOGICAL &&
                it.level != HealthConclusionEngine.Level.UNAVAILABLE
        }
        val research = items.filter {
            it.available && it.evidenceCategory == HealthConclusionEngine.EvidenceCategory.RESEARCH_SIGNAL
        }
        val headline = when {
            recorded.isNotEmpty() -> "已有 ${recorded.size} 項可顯示的健康紀錄"
            research.isNotEmpty() -> "目前取得原始訊號，尚無可比較的生理結果"
            else -> "目前沒有新的可用量測"
        }
        val nextAction = when {
            recorded.isNotEmpty() -> "點開單項結果查看來源與量測時間；其他項目可在手錶重新量測。"
            research.isNotEmpty() -> "請在手錶重新量測並保持佩戴與電極接觸；原始波形不能換算成未校準的百分比或心律診斷。"
            else -> "請在手錶啟動量測，保持與手機連線，並確認 Health Connect 權限。"
        }
        return Result(
            headline,
            recorded.take(4).map { "${it.title}：${it.valueText}" },
            research.take(3).map { "${it.title}：${it.valueText}" },
            nextAction
        )
    }
}
