package com.jaysato.xiaomihealthbridge

object PpgEvidencePresentationPolicy {
    fun eventCountLabel(eventCount: Int?, known: Boolean?): String = when {
        known == false || eventCount == null -> "未知"
        else -> eventCount.coerceAtLeast(0).toString()
    }
}
