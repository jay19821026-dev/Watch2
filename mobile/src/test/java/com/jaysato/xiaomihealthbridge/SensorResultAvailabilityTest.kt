package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorResultAvailabilityTest {
    @Test fun persistedRawWithoutValidatedResultIsVisible() {
        assertEquals(
            SensorResultAvailability.RAW_CAPTURED,
            SensorResultAvailability.classify(true, false, false, false)
        )
    }

    @Test fun rejectedRawIsQualityInsufficient() {
        assertEquals(
            SensorResultAvailability.QUALITY_INSUFFICIENT,
            SensorResultAvailability.classify(true, false, true, false)
        )
    }

    @Test fun eligibleResultWinsOverRawState() {
        assertEquals(
            SensorResultAvailability.RESULT_AVAILABLE,
            SensorResultAvailability.classify(true, true, false, false)
        )
    }

    @Test fun missingOrRejectedTransferIsSyncFailure() {
        assertEquals(
            SensorResultAvailability.SYNC_FAILED,
            SensorResultAvailability.classify(false, false, false, true)
        )
    }

    @Test fun neverMeasuredIsNotMisreportedAsSyncFailure() {
        assertEquals(null, SensorResultAvailability.classify(false, false, false, false))
    }
}
