package com.jaysato.xiaomihealthbridge

internal object RawDiagnosticExportRetention {
    const val MAX_AGE_MILLIS = 15 * 60 * 1000L

    fun isExpired(lastModified: Long, now: Long): Boolean =
        lastModified <= 0L || now < lastModified || now - lastModified >= MAX_AGE_MILLIS
}
