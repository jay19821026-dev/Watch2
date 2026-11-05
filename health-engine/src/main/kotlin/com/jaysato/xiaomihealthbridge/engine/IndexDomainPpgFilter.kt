package com.jaysato.xiaomihealthbridge.engine

data class IndexDomainPpgFilterResult(
    val values: List<Double?>,
    val origins: List<DataOrigin>,
    val filteredPointCount: Int,
    val algorithmVersion: String
)

object IndexDomainPpgFilter {
    const val VERSION = "ppg-index-median-3-v1"

    fun apply(rawValues: List<Double?>): IndexDomainPpgFilterResult {
        require(rawValues.size <= 100_000)
        val output = rawValues.toMutableList()
        val origins = rawValues.map { value ->
            if (value != null && value.isFinite() && value != PpgCompositionQualityEngine.GAP_SENTINEL) {
                DataOrigin.MEASURED
            } else {
                DataOrigin.UNKNOWN
            }
        }.toMutableList()
        var filteredPointCount = 0

        for (index in 1 until rawValues.lastIndex) {
            val window = rawValues.subList(index - 1, index + 2)
            if (window.any { it == null || !it.isFinite() || it == PpgCompositionQualityEngine.GAP_SENTINEL }) continue
            val sorted = window.map { it!! }.sorted()
            val median = sorted[1]
            if (median != rawValues[index]) {
                output[index] = median
                origins[index] = DataOrigin.FILTERED
                filteredPointCount++
            }
        }

        return IndexDomainPpgFilterResult(output, origins, filteredPointCount, VERSION)
    }
}
