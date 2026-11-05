package com.jaysato.xiaomihealthbridge.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSensorCountContractTest {
    @Test
    fun roundTripsIndependentRawEventAndPreviewCounts() {
        val encoded = RawSensorCountContract.encode(
            samples = 3_976,
            rawValueCount = 3_976,
            previewPointCount = 512,
            eventCount = 497
        )
        val decoded = RawSensorCountContract.decode(
            encoded["samples"], encoded["raw_value_count"], encoded["preview_point_count"],
            encoded["count_contract_version"], encoded["event_count"]
        )

        assertEquals(3_976, decoded.samples)
        assertEquals(3_976, decoded.rawValueCount)
        assertEquals(512, decoded.previewPointCount)
        assertEquals(497, decoded.eventCount)
        assertEquals(RawSensorCountContract.VERSION, decoded.contractVersion)
    }

    @Test
    fun missingAndExplicitZeroCountsRemainDistinct() {
        val missing = RawSensorCountContract.encode(0, null, 0, null)
        val explicitZero = RawSensorCountContract.encode(0, 0, 0, 0)
        val missingDecoded = RawSensorCountContract.decode(
            missing["samples"], missing["raw_value_count"], missing["preview_point_count"],
            missing["count_contract_version"], missing["event_count"]
        )
        val zeroDecoded = RawSensorCountContract.decode(
            explicitZero["samples"], explicitZero["raw_value_count"], explicitZero["preview_point_count"],
            explicitZero["count_contract_version"], explicitZero["event_count"]
        )

        assertFalse(missing.containsKey("raw_value_count"))
        assertFalse(missing.containsKey("event_count"))
        assertNull(missingDecoded.rawValueCount)
        assertNull(missingDecoded.eventCount)
        assertTrue(explicitZero.containsKey("raw_value_count"))
        assertEquals(0, zeroDecoded.rawValueCount)
        assertEquals(0, zeroDecoded.eventCount)
    }

    @Test
    fun negativeCountsAreNormalizedWithoutChangingAbsence() {
        val decoded = RawSensorCountContract.decode(-1, -2, -3, -4, -5)

        assertEquals(0, decoded.samples)
        assertEquals(0, decoded.rawValueCount)
        assertEquals(0, decoded.previewPointCount)
        assertEquals(0, decoded.contractVersion)
        assertEquals(0, decoded.eventCount)
        assertNull(RawSensorCountContract.decode(null, null, null, null, null).eventCount)
    }

    @Test
    fun futureContractEventCountIsPreservedButNotTrusted() {
        val counts = RawSensorCountContract.decode(32, 32, 16, 3, 8)

        assertEquals(8, counts.eventCount)
        assertNull(RawSensorCountContract.trustedEventCount(counts))
        assertEquals(8, RawSensorCountContract.trustedEventCount(counts.copy(contractVersion = 2)))
    }
}
