package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgRawRetentionPolicyTest {
    @Test fun evictsOnlyWhenValidRowExceedsRemainingCapacity() {
        assertFalse(EcgRawRetentionPolicy.shouldEvict(5, 4, true, 10))
        assertTrue(EcgRawRetentionPolicy.shouldEvict(6, 4, true, 10))
        assertFalse(EcgRawRetentionPolicy.shouldEvict(6, 11, true, 10))
        assertFalse(EcgRawRetentionPolicy.shouldEvict(0, 4, false, 10))
    }

    @Test fun longCaptureRetainsNewestRowsInsteadOfClearingEverything() {
        val retained = ArrayDeque<Int>()
        var chars = 0
        repeat(2969) { eventIndex ->
            while (EcgRawRetentionPolicy.shouldEvict(chars, 200, retained.isNotEmpty(), 32768)) {
                retained.removeFirst()
                chars -= 200 + if (retained.isEmpty()) 0 else 1
            }
            chars += 200 + if (retained.isEmpty()) 0 else 1
            retained.addLast(eventIndex)
            assertTrue(chars <= 32768)
            assertTrue(retained.isNotEmpty())
        }
        assertEquals(2968, retained.last())
        assertTrue(retained.first() > 0)
    }
}
