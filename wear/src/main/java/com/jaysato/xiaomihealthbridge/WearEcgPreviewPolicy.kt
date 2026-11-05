package com.jaysato.xiaomihealthbridge

internal object WearEcgPreviewPolicy {
    fun decodedCoveragePercent(decodedSamples: Int, missingSamples: Int): Double? {
        if (decodedSamples < 0 || missingSamples < 0) return null
        val total = decodedSamples.toLong() + missingSamples
        if (total == 0L) return null
        return decodedSamples.toDouble() * 100.0 / total
    }

    fun isResearchDecoded(status: String): Boolean = false

    fun analysisWindow(values: List<Double>, maxPoints: Int): List<Double> {
        require(maxPoints > 0)
        return if (values.size <= maxPoints) values else values.takeLast(maxPoints)
    }

    fun downsampleForChart(values: List<Float>, maxPoints: Int): List<Float> {
        require(maxPoints > 1)
        if (values.size <= maxPoints) return values
        val lastIndex = values.lastIndex
        val sourceIndices = List(maxPoints) { index ->
            (index.toLong() * lastIndex / (maxPoints - 1)).toInt()
        }
        return sourceIndices.mapIndexed { index, sourceIndex ->
            val previousIndex = if (index == 0) -1 else sourceIndices[index - 1]
            val gapBetweenSamples = (previousIndex + 1..sourceIndex).any { !values[it].isFinite() }
            if (gapBetweenSamples) Float.NaN else values[sourceIndex]
        }
    }
}
