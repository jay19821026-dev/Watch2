package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellExportDeadlineTest {
    @Test
    fun recreationUsesRemainingTimeInsteadOfRestartingFullDelay() {
        val deadline = 17_100L
        assertEquals(14_600L, ShellExportDeadline.remainingDelay(deadline, 2_500L))
    }

    @Test
    fun expiredDeadlineRunsImmediatelyAfterRecreation() {
        assertEquals(0L, ShellExportDeadline.remainingDelay(17_100L, 18_000L))
    }
}
