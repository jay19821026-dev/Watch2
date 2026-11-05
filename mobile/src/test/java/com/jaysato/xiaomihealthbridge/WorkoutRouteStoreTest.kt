package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class WorkoutRouteStoreTest {
    @Test
    fun routeTimestampIsFormattedInTheRequestedZone() {
        assertEquals(
            "2026/09/29 08:00",
            WorkoutRouteDisplayPolicy.recordedStart(1_790_640_000_000L, ZoneId.of("Asia/Taipei"))
        )
    }

    @Test
    fun validRouteReturnsPersistenceFailureToCaller() {
        val result = WorkoutRouteStore.save(
            start = 1_000L,
            end = 3_000L,
            encodedPoints = arrayOf("1100,25.0,121.0,10", "2100,25.0001,121.0001,11")
        ) { false }

        assertFalse(result)
    }

    @Test
    fun validRoutePersistsChronologicalValidatedPoints() {
        var persisted: String? = null

        val result = WorkoutRouteStore.save(
            start = 1_000L,
            end = 3_000L,
            encodedPoints = arrayOf("2100,25.0001,121.0001,11", "1100,25.0,121.0,10")
        ) {
            persisted = it
            true
        }

        assertTrue(result)
        assertEquals("1100,25.0,121.0,10.0\n2100,25.0001,121.0001,11.0", persisted)
    }

    @Test
    fun routeWithoutTwoValidPointsIsAnAcceptedNoOp() {
        var persistCalled = false

        val result = WorkoutRouteStore.save(
            start = 1_000L,
            end = 3_000L,
            encodedPoints = arrayOf("1100,25.0,121.0", "bad-point")
        ) {
            persistCalled = true
            true
        }

        assertTrue(result)
        assertFalse(persistCalled)
    }
}
