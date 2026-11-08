package com.jaysato.xiaomihealthbridge

/** Short, evidence-bound conclusions for the first screen of local analysis. */
internal object AnalysisLeadSummary {
    data class Result(
        val headline: String,
        val displayLines: List<String>,
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
        val readableReference = research.filter {
            ConsumerMetricPresentation.from(it).value !in setOf("重新量測", "待量測", "未支援")
        }
        val headline = when {
            recorded.isNotEmpty() -> "已有 ${recorded.size} 項健康紀錄${if (readableReference.isNotEmpty()) "，另有 ${readableReference.size} 項參考資料" else ""}"
            readableReference.isNotEmpty() -> "已有 ${readableReference.size} 項可查看的參考資料"
            research.isNotEmpty() -> "目前沒有可用健康結果，請重新量測"
            else -> "目前沒有新的可用量測"
        }
        val nextAction = when {
            recorded.isNotEmpty() || readableReference.isNotEmpty() -> "點開單項結果查看讀值與參考說明；其他項目可在手錶重新量測。"
            research.isNotEmpty() -> "請戴好手錶，依量測畫面操作後重試；可先查看已有的單項紀錄。"
            else -> "請在手錶啟動量測，保持與手機連線，並確認 Health Connect 權限。"
        }
        return Result(
            headline,
            (recorded + readableReference).take(4).map {
                val display = ConsumerMetricPresentation.from(it)
                "${display.title}：${display.value} · ${display.state}"
            },
            emptyList(),
            nextAction
        )
    }
}
