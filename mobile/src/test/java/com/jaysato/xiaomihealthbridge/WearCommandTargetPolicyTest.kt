package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCommandTargetPolicyTest {
    @Test
    fun commandsRequireExactlyOneConnectedWatch() {
        assertNull(WearCommandTargetPolicy.uniqueTarget(emptyList<String>()))
        assertEquals("target", WearCommandTargetPolicy.uniqueTarget(listOf("target")))
        assertNull(WearCommandTargetPolicy.uniqueTarget(listOf("watch-a", "watch-b")))
        assertTrue(WearCommandTargetPolicy.failureMessage(0).contains("找不到"))
        assertTrue(WearCommandTargetPolicy.failureMessage(2).contains("多台"))
    }
}
