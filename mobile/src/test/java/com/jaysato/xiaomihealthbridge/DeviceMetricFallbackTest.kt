package com.jaysato.xiaomihealthbridge

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class DeviceMetricFallbackTest {
    @Test fun stablePressureProducesNearZeroFloorChange() {
        val values = List(20) { 1011.12 } + List(20) { 1011.11 }
        assertEquals(0.0, DeviceMetricFallback.floorChange(values)!!, 0.1)
    }

    @Test fun rejectsInsufficientPressureEvidence() {
        assertNull(DeviceMetricFallback.floorChange(listOf(1011.0, 1010.9)))
    }

    @Test fun ignoresInvalidPressureOutlier() {
        val values = listOf(139.0) + List(20) { 1011.12 } + List(20) { 1010.76 }
        val floors = DeviceMetricFallback.floorChange(values)!!
        assertEquals(1.0, floors, 0.3)
    }

    @Test fun unverifiedWatchTemperatureIsNeverPresentedAsCelsius() {
        val feature = preferences(mapOf(
            "temperature" to 36.6f,
            "temperature_timestamp" to 9_000L,
            "temperature_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "temperature_unit_status" to "unverified"
        ))

        assertNull(DeviceMetricFallback.temperatureC(feature, 10_000L))
    }

    @Test fun verifiedCelsiusTemperatureRequiresFreshTrustedTimestamp() {
        val fresh = preferences(mapOf(
            "temperature" to 36.6f,
            "temperature_timestamp" to 9_000L,
            "temperature_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "temperature_unit_status" to DeviceMetricFallback.VERIFIED_CELSIUS_UNIT_STATUS
        ))
        val stale = preferences(mapOf(
            "temperature" to 36.6f,
            "temperature_timestamp" to 1L,
            "temperature_timestamp_source" to "android_sensor_event_elapsed_realtime",
            "temperature_unit_status" to DeviceMetricFallback.VERIFIED_CELSIUS_UNIT_STATUS
        ))

        assertEquals(36.6, DeviceMetricFallback.temperatureC(fresh, 10_000L)!!, 0.001)
        assertNull(DeviceMetricFallback.temperatureC(stale, 100_000_000L))
    }

    private fun preferences(values: Map<String, Any>): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)
    ) { _, method, args ->
        val key = args?.firstOrNull() as? String
        val value = key?.let(values::get)
        when (method.name) {
            "getFloat" -> value as? Float ?: args?.getOrNull(1) as? Float ?: Float.NaN
            "getLong" -> value as? Long ?: args?.getOrNull(1) as? Long ?: 0L
            "getString" -> value as? String ?: args?.getOrNull(1) as? String
            else -> null
        }
    } as SharedPreferences
}
