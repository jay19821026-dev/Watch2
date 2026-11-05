package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgMeasurementLeaseTest {
    @Test
    fun onlyOneCaptureOwnerCanAcquireAtATime() {
        val scan = Any()
        val screen = Any()

        assertTrue(EcgMeasurementLease.acquire(scan))
        assertFalse(EcgMeasurementLease.acquire(screen))
        EcgMeasurementLease.release(screen)
        assertFalse(EcgMeasurementLease.acquire(screen))
        EcgMeasurementLease.release(scan)
        assertTrue(EcgMeasurementLease.acquire(screen))
        EcgMeasurementLease.release(screen)
    }

    @Test
    fun sameOwnerCanAcquireAgainAndReleaseIsOwnerScoped() {
        val owner = Any()
        val other = Any()

        assertTrue(EcgMeasurementLease.acquire(owner))
        assertTrue(EcgMeasurementLease.acquire(owner))
        EcgMeasurementLease.release(other)
        assertFalse(EcgMeasurementLease.acquire(other))
        EcgMeasurementLease.release(owner)
        assertTrue(EcgMeasurementLease.acquire(other))
        EcgMeasurementLease.release(other)
    }
}
