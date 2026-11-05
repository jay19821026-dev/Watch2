package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorSuiteSessionPolicyTest {
    @Test
    fun suiteIdTakesPrecedenceOverProbeTimestamps() {
        assertTrue(SensorSuiteSessionPolicy.belongsToLatestSuite(100L, 200L, "suite-new", "suite-new"))
        assertFalse(SensorSuiteSessionPolicy.belongsToLatestSuite(300L, 200L, "suite-old", "suite-new"))
        assertFalse(SensorSuiteSessionPolicy.belongsToLatestSuite(300L, 200L, "", "suite-new"))
    }

    @Test
    fun legacyRowsFallBackToTimestampMembership() {
        assertTrue(SensorSuiteSessionPolicy.belongsToLatestSuite(201L, 200L))
        assertFalse(SensorSuiteSessionPolicy.belongsToLatestSuite(199L, 200L))
    }

    @Test
    fun explicitRunStateIsPreservedAndLegacyTerminalMessagesAreClassified() {
        assertTrue("partial" in SensorSuiteSessionPolicy.TERMINAL_STATES)
        assertEquals("timed_out", SensorSuiteSessionPolicy.terminalState("掃描已達上限", false))
        assertEquals("cancelled", SensorSuiteSessionPolicy.terminalState("掃描被新的操作取代", false))
        assertEquals("partial", SensorSuiteSessionPolicy.terminalState("全感測器盤點完成：部分通道", false))
    }

    @Test
    fun terminalEventsMustMatchTheActiveSuiteIdentity() {
        assertTrue(SensorSuiteSessionPolicy.terminalBelongsToCurrentSuite("new", "new", 101L, 100L, 100L))
        assertFalse(SensorSuiteSessionPolicy.terminalBelongsToCurrentSuite("new", "new", 99L, 100L, 100L))
        assertFalse(SensorSuiteSessionPolicy.terminalBelongsToCurrentSuite("new", "new", 99L, 0L, 100L))
        assertFalse(SensorSuiteSessionPolicy.terminalBelongsToCurrentSuite("old", "new", 200L, 90L, 100L))
        assertFalse(SensorSuiteSessionPolicy.terminalBelongsToCurrentSuite("", "new", 200L, 200L, 100L))
        assertTrue(SensorSuiteSessionPolicy.terminalBelongsToCurrentSuite("", "", 101L, 100L, 100L))
    }

    @Test
    fun terminalStartStatusCannotPersistAnEndBeforeItsSessionBegins() {
        assertEquals(
            "running",
            SensorSuiteSessionPolicy.runStateForSuiteStart("completed", 99L, 100L)
        )
        assertEquals(
            "completed",
            SensorSuiteSessionPolicy.runStateForSuiteStart("completed", 100L, 100L)
        )
        assertEquals(
            "running",
            SensorSuiteSessionPolicy.runStateForSuiteStart("running", 99L, 100L)
        )
    }

    @Test
    fun matchingSuiteTerminalIsIndependentOfGlobalControlStatusOrdering() {
        assertTrue(SensorSuiteSessionPolicy.shouldAcceptSuiteTerminal("active", "active", 150L, 100L, 100L, "running", 0L))
        assertFalse(SensorSuiteSessionPolicy.shouldAcceptSuiteTerminal("previous", "active", 250L, 90L, 100L, "running", 0L))
        assertFalse(SensorSuiteSessionPolicy.shouldAcceptSuiteTerminal("active", "active", 149L, 100L, 100L, "running", 150L))
        assertFalse(SensorSuiteSessionPolicy.shouldAcceptSuiteTerminal("active", "active", 250L, 100L, 100L, "completed", 150L))
    }

    @Test
    fun terminalForNewerSuiteCanArriveBeforeItsStartStatusWithoutReplacingItAsRunningLater() {
        assertTrue(SensorSuiteSessionPolicy.shouldAcceptSuiteTerminal(
            incomingSuiteId = "new",
            currentSuiteId = "old",
            eventTimestamp = 250L,
            incomingSuiteStartedAt = 200L,
            currentSuiteStartedAt = 100L,
            currentRunState = "running",
            previousTerminalTimestamp = 150L
        ))
        assertFalse(ControlStatusOrdering.shouldAccept(200L, 200L))
        assertFalse(SensorSuiteSessionPolicy.shouldAcceptSuiteTerminal(
            incomingSuiteId = "old",
            currentSuiteId = "new",
            eventTimestamp = 300L,
            incomingSuiteStartedAt = 100L,
            currentSuiteStartedAt = 200L,
            currentRunState = "running",
            previousTerminalTimestamp = 150L
        ))
    }

    @Test
    fun missingTerminalStateIsShownWhenAWatchRunExceedsItsDeadlineGrace() {
        assertEquals("running", SensorSuiteSessionPolicy.displayRunState("running", 100L, 100L))
        assertEquals("running", SensorSuiteSessionPolicy.displayRunState("running", 100L, 100L + 7 * 60_000L))
        assertEquals("terminal_status_missing", SensorSuiteSessionPolicy.displayRunState("running", 100L, 100L + 8 * 60_000L))
        assertEquals("completed", SensorSuiteSessionPolicy.displayRunState("completed", 100L, 100L + 9 * 60_000L))
        assertEquals("流程完成（不代表量測有效）", SensorSuiteSessionPolicy.runStateLabel("completed"))
        assertEquals("缺少手錶終態回報", SensorSuiteSessionPolicy.runStateLabel("terminal_status_missing"))
    }
}
