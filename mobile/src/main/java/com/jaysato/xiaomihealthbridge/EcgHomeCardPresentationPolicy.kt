package com.jaysato.xiaomihealthbridge

internal object EcgHomeCardPresentationPolicy {
    fun supportingText(
        resultValue: String?,
        availability: SensorResultAvailability? = null
    ): String = when (availability) {
        SensorResultAvailability.RAW_CAPTURED -> "已擷取訊號，尚無心律分析結果"
        SensorResultAvailability.QUALITY_INSUFFICIENT -> "已擷取訊號，尚無心律分析結果"
        SensorResultAvailability.SYNC_FAILED -> availability.detail
        SensorResultAvailability.RESULT_AVAILABLE -> "最近一次研究心律結果"
        null -> if (isCalculatedResearchResult(resultValue)) {
            "最近一次研究心律結果"
        } else {
            "在手錶量測後查看結果"
        }
    }

    internal fun isCalculatedResearchResult(value: String?): Boolean =
        value?.contains("bpm", ignoreCase = true) == true || value?.contains("次/分") == true
}
