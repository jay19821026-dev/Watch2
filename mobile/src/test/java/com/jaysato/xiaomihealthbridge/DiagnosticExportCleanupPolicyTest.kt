package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportCleanupPolicyTest {
    @Test
    fun cleanupIsRestrictedToKnownExportNamesAndScopes() {
        assertTrue(DiagnosticExportCleanupPolicy.isAllowed(DiagnosticExportCleanupPolicy.CACHE_SCOPE, "engineering-diagnostics-123.json"))
        assertTrue(DiagnosticExportCleanupPolicy.isAllowed(DiagnosticExportCleanupPolicy.CACHE_SCOPE, "watch-raw-sensors-123.json"))
        assertTrue(DiagnosticExportCleanupPolicy.isAllowed(DiagnosticExportCleanupPolicy.DEBUG_SCOPE, "healthbridge-debug.json"))
        assertFalse(DiagnosticExportCleanupPolicy.isAllowed(DiagnosticExportCleanupPolicy.DEBUG_SCOPE, "other.json"))
        assertFalse(DiagnosticExportCleanupPolicy.isAllowed(DiagnosticExportCleanupPolicy.CACHE_SCOPE, "../healthbridge-debug.json"))
        assertFalse(DiagnosticExportCleanupPolicy.isAllowed("arbitrary_path", "healthbridge-debug.json"))
    }
}
