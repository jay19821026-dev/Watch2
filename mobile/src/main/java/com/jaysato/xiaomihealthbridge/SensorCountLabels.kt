package com.jaysato.xiaomihealthbridge

internal object SensorCountLabels {
    fun count(value: Int?): String = value?.takeIf { it >= 0 }?.toString() ?: "未知"

    fun rawValueCount(rawValueCount: Int?): String = count(rawValueCount)

    fun eventCount(eventCount: Int?): String = count(eventCount)
}
