package com.jaysato.xiaomihealthbridge

internal data class RawPacketChannelSummary(
    val totalCount: Int,
    val finiteCount: Int,
    val lastFiniteValue: Double?,
    val finiteMean: Double?
) {
    val missingCount: Int get() = totalCount - finiteCount
}

internal object RawPacketChannelSummaryPolicy {
    fun summarize(values: List<Double>): RawPacketChannelSummary {
        val finite = values.filter(Double::isFinite)
        return RawPacketChannelSummary(
            totalCount = values.size,
            finiteCount = finite.size,
            lastFiniteValue = finite.lastOrNull(),
            finiteMean = finite.takeIf { it.isNotEmpty() }?.average()?.takeIf(Double::isFinite)
        )
    }
}
