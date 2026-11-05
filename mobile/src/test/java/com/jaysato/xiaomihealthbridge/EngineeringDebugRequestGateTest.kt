package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringDebugRequestGateTest {
    @Test
    fun acceptsOnlyRequestsOwnedByTheLiveActivity() {
        assertTrue(isEngineeringDebugRequestCurrent(4, 4, false, false, false))
        assertFalse(isEngineeringDebugRequestCurrent(3, 4, false, false, false))
        assertFalse(isEngineeringDebugRequestCurrent(4, 4, true, false, false))
        assertFalse(isEngineeringDebugRequestCurrent(4, 4, false, true, false))
        assertFalse(isEngineeringDebugRequestCurrent(4, 4, false, false, true))
    }

    @Test
    fun restoresOnlyTheWatchResponseCorrelatedToTheSavedRequest() {
        assertTrue(isMatchingWatchStatusResponse("request-1", "request-1"))
        assertFalse(isMatchingWatchStatusResponse("request-1", "request-2"))
        assertFalse(isMatchingWatchStatusResponse("", "request-1"))
        assertFalse(isMatchingWatchStatusResponse("request-1", ""))
    }
}
