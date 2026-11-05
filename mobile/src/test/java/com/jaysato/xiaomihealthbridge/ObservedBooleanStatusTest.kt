package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservedBooleanStatusTest {
    @Test
    fun preservesExplicitBooleanReports() {
        assertEquals("true", ObservedBooleanStatus.label(true))
        assertEquals("false", ObservedBooleanStatus.label(false))
    }

    @Test
    fun absentOrMalformedReportsAreNotInactive() {
        listOf(null, "false", "true", 0, 1, Any()).forEach { value ->
            assertEquals("未知", ObservedBooleanStatus.label(value))
        }
    }
}
