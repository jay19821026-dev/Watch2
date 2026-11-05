package com.jaysato.xiaomihealthbridge

internal object EcgHomeCardPresentationPolicy {
    fun supportingText(
        resultValue: String?,
        availability: SensorResultAvailability? = null
    ): String = when (availability) {
        SensorResultAvailability.RAW_CAPTURED -> availability.detail
        SensorResultAvailability.QUALITY_INSUFFICIENT -> availability.detail
        SensorResultAvailability.SYNC_FAILED -> availability.detail
        SensorResultAvailability.RESULT_AVAILABLE -> "最近一次研究心律結果"
        null -> if (isCalculatedResearchResult(resultValue)) {
            "最近一次研究心律結果"
        } else {
            "顯示可計算的研究結果；資料不足時可能無法判定"
        }
    }

    internal fun isCalculatedResearchResult(value: String?): Boolean =
        value?.contains("bpm", ignoreCase = true) == true || value?.contains("次/分") == true
}
