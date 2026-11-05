package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearControlCommandAuthPolicyTest {
    @Test
    fun preservesWearableListenerMessageDelivery() {
        assertTrue(WearControlCommandAuth.isWearableListenerStart(WearControlCommandAuth.ACTION_WEARABLE_MESSAGE_RECEIVED))
    }

    @Test
    fun doesNotTreatAppCommandsAsFrameworkDelivery() {
        assertFalse(WearControlCommandAuth.isWearableListenerStart(WearControlService.ACTION_SENSOR_SUITE))
        assertFalse(WearControlCommandAuth.isWearableListenerStart(null))
    }

    @Test
    fun sensorSuiteMessageRequiresForegroundServiceHandoff() {
        assertTrue(WearControlCommandAuth.requiresForegroundHandoff(WearControlService.PATH_SENSOR_SUITE))
        assertFalse(WearControlCommandAuth.requiresForegroundHandoff(WearControlService.PATH_PING))
    }
}
