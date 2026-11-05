package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearMeasurementPermissionPolicyTest {
    @Test
    fun usesHealthPermissionOnAndroid16AndNewer() {
        assertEquals(
            "android.permission.health.READ_HEART_RATE",
            WearMeasurementPermissionPolicy.heartRatePermission(36)
        )
        assertEquals(
            "android.permission.health.READ_HEART_RATE",
            WearMeasurementPermissionPolicy.heartRatePermission(37)
        )
    }

    @Test
    fun usesBodySensorsPermissionOnOlderAndroid() {
        assertEquals(
            "android.permission.BODY_SENSORS",
            WearMeasurementPermissionPolicy.heartRatePermission(35)
        )
    }

    @Test
    fun backgroundMonitoringUsesSeparateVersionSpecificPermission() {
        assertEquals(
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
            WearMeasurementPermissionPolicy.backgroundHeartRatePermission(36)
        )
        assertEquals(
            "android.permission.BODY_SENSORS_BACKGROUND",
            WearMeasurementPermissionPolicy.backgroundHeartRatePermission(35)
        )
    }

    @Test
    fun healthForegroundServiceRequiresAtLeastOneRuntimePrerequisite() {
        assertEquals(
            listOf("android.permission.BODY_SENSORS", "android.permission.ACTIVITY_RECOGNITION"),
            WearMeasurementPermissionPolicy.healthForegroundRuntimePermissions(34)
        )
        assertEquals(false, WearMeasurementPermissionPolicy.canStartHealthForegroundService(34) { false })
        assertEquals(
            true,
            WearMeasurementPermissionPolicy.canStartHealthForegroundService(34) {
                it == "android.permission.BODY_SENSORS"
            }
        )
    }

    @Test
    fun heartRatePermissionIsCheckedIndependentlyFromHealthServiceStartup() {
        assertEquals(true, WearMeasurementPermissionPolicy.hasHealthSensorPermission(34) {
            it == "android.permission.BODY_SENSORS"
        })
        assertEquals(false, WearMeasurementPermissionPolicy.hasHealthSensorPermission(34) {
            it == "android.permission.ACTIVITY_RECOGNITION"
        })
        assertEquals(true, WearMeasurementPermissionPolicy.hasHealthSensorPermission(36) {
            it == "android.permission.health.READ_HEART_RATE"
        })
    }

    @Test
    fun sensorSuiteCanStartWhenAnyAndroidHealthForegroundPrerequisiteIsGranted() {
        assertTrue(WearMeasurementPermissionPolicy.canRunSensorSuite(36) {
            it == "android.permission.ACTIVITY_RECOGNITION"
        })
        assertTrue(WearMeasurementPermissionPolicy.canRunSensorSuite(36) {
            it == "android.permission.health.READ_HEART_RATE"
        })
        assertFalse(WearMeasurementPermissionPolicy.canRunSensorSuite(36) { false })
        assertTrue(WearMeasurementPermissionPolicy.canRunSensorSuite(33) { false })
    }
}
