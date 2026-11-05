package com.jaysato.xiaomihealthbridge

object ShellExportDeadline {
    fun remainingDelay(deadlineElapsedRealtime: Long, nowElapsedRealtime: Long): Long =
        (deadlineElapsedRealtime - nowElapsedRealtime).coerceAtLeast(0L)
}
