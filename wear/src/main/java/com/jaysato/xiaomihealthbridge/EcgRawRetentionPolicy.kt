package com.jaysato.xiaomihealthbridge

internal object EcgRawRetentionPolicy {
    fun shouldEvict(currentChars: Int, nextRowChars: Int, hasRows: Boolean, maxChars: Int): Boolean =
        hasRows && nextRowChars in 0..maxChars &&
            currentChars.toLong() + nextRowChars + 1L > maxChars
}
