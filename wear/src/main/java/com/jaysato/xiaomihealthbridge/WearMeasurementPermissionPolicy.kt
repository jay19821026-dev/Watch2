package com.jaysato.xiaomihealthbridge

internal object WearMeasurementPermissionPolicy {
    fun heartRatePermission(sdkVersion: Int): String =
        if (sdkVersion >= 36) READ_HEART_RATE else BODY_SENSORS

    fun backgroundHeartRatePermission(sdkVersion: Int): String =
        if (sdkVersion >= 36) READ_HEALTH_DATA_IN_BACKGROUND else BODY_SENSORS_BACKGROUND

    fun healthForegroundRuntimePermissions(sdkVersion: Int): List<String> =
        listOf(heartRatePermission(sdkVersion), ACTIVITY_RECOGNITION)

    fun canStartHealthForegroundService(
        sdkVersion: Int,
        isPermissionGranted: (String) -> Boolean
    ): Boolean = healthForegroundRuntimePermissions(sdkVersion).any(isPermissionGranted)

    fun hasHealthSensorPermission(
        sdkVersion: Int,
        isPermissionGranted: (String) -> Boolean
    ): Boolean = isPermissionGranted(heartRatePermission(sdkVersion))

    fun canRunSensorSuite(
        sdkVersion: Int,
        isPermissionGranted: (String) -> Boolean
    ): Boolean = sdkVersion < 34 || canStartHealthForegroundService(sdkVersion, isPermissionGranted)

    private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
    private const val READ_HEALTH_DATA_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    private const val BODY_SENSORS = "android.permission.BODY_SENSORS"
    private const val BODY_SENSORS_BACKGROUND = "android.permission.BODY_SENSORS_BACKGROUND"
    private const val ACTIVITY_RECOGNITION = "android.permission.ACTIVITY_RECOGNITION"
}
