package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSensorSuiteCallbackPolicyTest {
    @Test
    fun onlyCallbacksFromTheCurrentActiveSuiteAreAccepted() {
        assertTrue(WearSensorSuiteCallbackPolicy.accepts(7, 7, 7))
        assertFalse(WearSensorSuiteCallbackPolicy.accepts(6, 7, 7))
        assertFalse(WearSensorSuiteCallbackPolicy.accepts(7, null, 7))
        assertFalse(WearSensorSuiteCallbackPolicy.accepts(7, 7, 8))
    }

    @Test
    fun cancelOrCompletionInvalidatesPreviouslyScheduledCallbacks() {
        val priorToken = 12
        val incrementedToken = priorToken + 1

        assertFalse(WearSensorSuiteCallbackPolicy.accepts(priorToken, null, incrementedToken))
        assertFalse(WearSensorSuiteCallbackPolicy.accepts(priorToken, priorToken, incrementedToken))
    }
}
