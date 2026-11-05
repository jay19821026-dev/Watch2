package com.jaysato.xiaomihealthbridge

import kotlin.math.ceil

internal object TrendSampleEncoder {
    const val MAX_POINTS = 120

    fun encode(values: List<Double>): String = encode(values.asSequence(), values.count(Double::isFinite))

    fun encode(values: Sequence<Double>, finiteCount: Int): String {
        if (finiteCount <= 0) return ""
        val stride = ceil(finiteCount.toDouble() / MAX_POINTS).toInt().coerceAtLeast(1)
        val selected = ArrayList<Double>(minOf(finiteCount, MAX_POINTS))
        var index = 0
        var lastValue: Double? = null
        values.forEach { value ->
            if (value.isFinite()) {
                if (index % stride == 0 && selected.size < MAX_POINTS) selected += value
                lastValue = value
                index++
            }
        }
        if (selected.isNotEmpty() && lastValue != selected.last()) {
            if (selected.size == MAX_POINTS) selected[selected.lastIndex] = lastValue!!
            else selected += lastValue!!
        }
        return selected.joinToString(",")
    }
}
