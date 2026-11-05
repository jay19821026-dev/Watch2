package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class RawSnapshotSampleRatePersistencePolicyTest {
    @Test
    fun presentRateFieldOverwritesEvenWhenItsReportedValueIsUnknownZero() {
        assertEquals(
            RawSnapshotSampleRatePersistencePolicy.Action.WRITE,
            RawSnapshotSampleRatePersistencePolicy.action(hasSampleRateField = true, newMeasurementSession = false)
        )
    }

    @Test
    fun missingRateOnNewSessionClearsPriorSessionValue() {
        assertEquals(
            RawSnapshotSampleRatePersistencePolicy.Action.CLEAR,
            RawSnapshotSampleRatePersistencePolicy.action(hasSampleRateField = false, newMeasurementSession = true)
        )
    }

    @Test
    fun missingRateOnSameSessionReplayPreservesPreviouslyStoredEvidence() {
        assertEquals(
            RawSnapshotSampleRatePersistencePolicy.Action.KEEP,
            RawSnapshotSampleRatePersistencePolicy.action(hasSampleRateField = false, newMeasurementSession = false)
        )
    }
}
