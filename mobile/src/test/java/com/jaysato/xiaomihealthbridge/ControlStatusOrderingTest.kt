package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlStatusOrderingTest {
    @Test
    fun acceptsOnlyNewerNonzeroStatusTimestamps() {
        assertTrue(ControlStatusOrdering.shouldAccept(10L, 0L))
        assertTrue(ControlStatusOrdering.shouldAccept(11L, 10L))
        assertFalse(ControlStatusOrdering.shouldAccept(10L, 10L))
        assertFalse(ControlStatusOrdering.shouldAccept(9L, 10L))
        assertFalse(ControlStatusOrdering.shouldAccept(0L, 0L))
    }
}
