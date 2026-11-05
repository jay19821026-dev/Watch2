package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawDiagnosticExportRetentionTest {
    @Test
    fun onlyRecentNonFutureFilesRemainAvailable() {
        val now = 2_000_000L

        assertFalse(RawDiagnosticExportRetention.isExpired(now - 1L, now))
        assertTrue(RawDiagnosticExportRetention.isExpired(now - RawDiagnosticExportRetention.MAX_AGE_MILLIS, now))
        assertTrue(RawDiagnosticExportRetention.isExpired(0L, now))
        assertTrue(RawDiagnosticExportRetention.isExpired(now + 1L, now))
    }
}
