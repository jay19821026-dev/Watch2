package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSnapshotOrderingTest {
    @Test
    fun explainsWhyLatePacketsAreRejected() {
        assertEquals("older_session", RawSnapshotOrdering.rejectionReason(10, 50, 1, true, 11, 40, 1, false))
        assertEquals("older_timestamp", RawSnapshotOrdering.rejectionReason(11, 39, 1, true, 11, 40, 1, false))
        assertEquals("preview_after_final", RawSnapshotOrdering.rejectionReason(11, 40, 1, false, 11, 40, 1, true))
        assertEquals(null, RawSnapshotOrdering.rejectionReason(11, 41, 1, true, 11, 40, 1, false))
    }

    @Test
    fun rejectsPreviousSessionEvenWhenItsPacketArrivesLate() {
        assertFalse(RawSnapshotOrdering.shouldAccept(100L, 400L, 1, true, 200L, 300L, 1, false))
        assertTrue(RawSnapshotOrdering.shouldAccept(200L, 300L, 1, false, 100L, 400L, 1, true))
    }

    @Test
    fun sameTimestampFinalSnapshotsRequireComparableIncreasingSequence() {
        assertFalse(RawSnapshotOrdering.shouldAccept(200L, 400L, 6, false, 200L, 400L, 5, true))
        assertTrue(RawSnapshotOrdering.shouldAccept(200L, 400L, 6, true, 200L, 400L, 5, true))
        assertEquals("older_sequence", RawSnapshotOrdering.rejectionReason(200L, 400L, 4, true, 200L, 400L, 5, true))
        assertEquals("same_timestamp_sequence_unavailable", RawSnapshotOrdering.rejectionReason(200L, 400L, 0, true, 200L, 400L, 0, true))
        assertEquals("same_timestamp_sequence_unavailable", RawSnapshotOrdering.rejectionReason(200L, 400L, 5, true, 200L, 400L, 0, true))
        assertEquals("same_timestamp_sequence_unavailable", RawSnapshotOrdering.rejectionReason(200L, 400L, 0, true, 200L, 400L, 5, true))
        assertEquals("duplicate_sequence", RawSnapshotOrdering.rejectionReason(200L, 400L, 5, true, 200L, 400L, 5, true))
        assertTrue(RawSnapshotOrdering.shouldAccept(200L, 400L, 6, true, 200L, 400L, 5, false))
    }

    @Test
    fun acceptsNewSessionAndRejectsLatePacketsFromPreviousSession() {
        assertTrue(RawSnapshotOrdering.shouldAccept(500L, 600L, 1, true, 400L, 550L, 1, true))
        assertFalse(RawSnapshotOrdering.shouldAccept(400L, 700L, 1, true, 500L, 600L, 1, true))
    }

    @Test
    fun acceptsNewerProbeWithinCurrentScanEvenWhenStartStatusWasEarlier() {
        assertTrue(RawSnapshotOrdering.shouldAccept(1_250L, 1_500L, 1, true, 900L, 1_400L, 1, true))
        assertFalse(RawSnapshotOrdering.shouldAccept(900L, 1_600L, 1, true, 1_250L, 1_500L, 1, true))
    }

    @Test
    fun healthServicesHeartRateSnapshotMustCarryCurrentSuiteSession() {
        assertEquals("older_session", RawSnapshotOrdering.rejectionReason(0L, 1_600L, 1, true, 1_250L, 1_500L, 1, true))
        assertTrue(RawSnapshotOrdering.shouldAccept(1_250L, 1_600L, 1, true, 900L, 1_500L, 1, true))
    }

    @Test
    fun distinctCurrentSuiteStartingInSameMillisecondIsANewMeasurementSession() {
        assertTrue(RawSnapshotOrdering.isNewMeasurementSession(
            500L, "suite-new", 500L, "suite-old", "suite-new"
        ))
        assertFalse(RawSnapshotOrdering.isNewMeasurementSession(
            500L, "suite-old", 500L, "suite-new", "suite-new"
        ))
        assertFalse(RawSnapshotOrdering.isNewMeasurementSession(
            500L, "suite-new", 500L, "suite-old", ""
        ))
        assertEquals("suite_mismatch", RawSnapshotOrdering.rejectionReason(
            500L, 700L, 1L, true, 500L, 600L, 1L, true,
            incomingSuiteId = "suite-old",
            previousSuiteId = "suite-new",
            latestSuiteId = "suite-new"
        ))
        assertEquals(null, RawSnapshotOrdering.rejectionReason(
            500L, 550L, 1L, true, 500L, 600L, 1L, true,
            incomingSuiteId = "suite-new",
            previousSuiteId = "suite-old",
            latestSuiteId = "suite-new"
        ))
    }

    @Test
    fun standaloneProbeSessionUsesElapsedRealtimeToDisambiguateSameMillisecond() {
        assertTrue(RawSnapshotOrdering.isNewMeasurementSession(
            500L, "", 500L, "", "", incomingSessionElapsedNs = 2_000L, previousSessionElapsedNs = 1_000L
        ))
        assertFalse(RawSnapshotOrdering.isNewMeasurementSession(
            500L, "", 500L, "", "", incomingSessionElapsedNs = 900L, previousSessionElapsedNs = 1_000L
        ))
        assertEquals("older_timestamp", RawSnapshotOrdering.rejectionReason(
            500L, 600L, 2L, true, 500L, 700L, 1L, true,
            incomingSessionElapsedNs = 900L, previousSessionElapsedNs = 1_000L
        ))
    }
}
