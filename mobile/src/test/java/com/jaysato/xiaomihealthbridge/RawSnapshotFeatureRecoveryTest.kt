package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawSnapshotFeatureRecoveryTest {
    @Test
    fun recoveredHeartRateKeepsItsTimestampProvenance() {
        val source = HealthAnalysisFreshnessPolicy.HEALTH_SERVICES_DATAPOINT_TIMESTAMP_SOURCE
        val recovered = RawSnapshotFeatureRecovery.fromSnapshot(
            kind = "whs_heart_rate",
            isFinal = true,
            formulaPulseBpm = null,
            averageBpm = 74.0,
            timestamp = 1_000L,
            timestampSource = source
        )

        assertEquals(source, recovered?.timestampSource)
    }

    @Test
    fun onlyHealthServicesHeartRateCanRecoverHealthServicesFeature() {
        val recovered = RawSnapshotFeatureRecovery.fromSnapshot(
            kind = "whs_heart_rate",
            isFinal = true,
            formulaPulseBpm = null,
            averageBpm = 74.0,
            timestamp = 1_000L
        )

        assertEquals(HeartRateFeatureSnapshot(74.0, 1_000L), recovered)
        assertNull(RawSnapshotFeatureRecovery.fromSnapshot("ppg", true, 74.0, null, 1_000L))
        assertNull(RawSnapshotFeatureRecovery.fromSnapshot("spo2_raw", true, 74.0, null, 1_000L))
        assertNull(RawSnapshotFeatureRecovery.fromSnapshot("bioz", true, 74.0, null, 1_000L))
    }

    @Test
    fun recoveryFailureIsDistinctFromNonApplicableAndMustRetainReceipt() {
        assertEquals(
            RawSnapshotFeatureRecoveryOutcome.RecoveryFailed,
            RawSnapshotFeatureRecovery.recover("whs_heart_rate", true, null, null, 1_000L)
        )
        assertEquals(
            RawSnapshotFeatureRecoveryOutcome.RecoveryFailed,
            RawSnapshotFeatureRecovery.recover("whs_heart_rate", false, 74.0, null, 1_000L)
        )
        assertEquals(
            RawSnapshotFeatureRecoveryOutcome.NotApplicable,
            RawSnapshotFeatureRecovery.recover("spo2_raw", true, null, null, 1_000L)
        )
        assertEquals(
            RawSnapshotFeatureRecoveryOutcome.Recovered(HeartRateFeatureSnapshot(74.0, 1_000L)),
            RawSnapshotFeatureRecovery.recover("whs_heart_rate", true, 74.0, null, 1_000L)
        )
    }

    @Test
    fun pendingReceiptLedgerTracksOnlyInterruptedFeatureWork() {
        val first = RawSnapshotReceiptLedger.append("", "uri-a")
        val both = RawSnapshotReceiptLedger.append(first, "uri-b")
        val afterFirstCompletes = RawSnapshotReceiptLedger.remove(both, "uri-a")

        assertEquals("uri-a\nuri-b", both)
        assertEquals("uri-b", afterFirstCompletes)
        assertEquals(false, RawSnapshotReceiptLedger.contains(afterFirstCompletes, "uri-a"))
        assertEquals(true, RawSnapshotReceiptLedger.contains(afterFirstCompletes, "uri-b"))
    }

    @Test
    fun pendingLedgerCapacityNeverEvictsUnresolvedReceipts() {
        val fullLedger = (1..512).joinToString("\n") { "uri-$it" }

        assertNull(RawSnapshotReceiptLedger.appendPending(fullLedger, "uri-513"))
        assertEquals(true, RawSnapshotReceiptLedger.contains(fullLedger, "uri-1"))
        assertEquals(fullLedger, RawSnapshotReceiptLedger.appendPending(fullLedger, "uri-1"))
        assertEquals(fullLedger, RawSnapshotReceiptLedger.reservePending(fullLedger, "legacy-uri", false))
        assertNull(RawSnapshotReceiptLedger.reservePending(fullLedger, "new-uri", true))
    }

    @Test
    fun unresolvedPendingReceiptStillReplaysAfterReceiptHistoryEvictsIt() {
        val pendingLedger = RawSnapshotReceiptLedger.appendPending("", "uri-old")
        val recentReceipts = (1..512).joinToString("\n") { "uri-$it" }

        assertEquals(false, RawSnapshotReceiptLedger.contains(recentReceipts, "uri-old"))
        assertEquals(true, RawSnapshotReceiptLedger.requiresReplay(recentReceipts, pendingLedger.orEmpty(), "uri-old"))
    }

    @Test
    fun replayPlanForSameTimestampStillRequiresDistinctDurabilityAttempt() {
        val firstAttempt = HeartRateFeatureCommitPlanner.plan(
            bpm = 74.0,
            timestamp = 1_000L,
            previousTimestamp = 1_000L,
            now = 2_000L,
            attemptId = "attempt-a"
        )
        val retry = HeartRateFeatureCommitPlanner.plan(
            bpm = 74.0,
            timestamp = 1_000L,
            previousTimestamp = 1_000L,
            now = 2_000L,
            attemptId = "attempt-b"
        )

        assertNotEquals(null, firstAttempt)
        assertNull(firstAttempt?.measurement)
        assertEquals("attempt-a", firstAttempt?.attemptId)
        assertEquals("attempt-b", retry?.attemptId)
        assertNotEquals(firstAttempt?.attemptId, retry?.attemptId)
    }

    @Test
    fun newerTimestampPlanContainsFeatureMutation() {
        val plan = HeartRateFeatureCommitPlanner.plan(
            bpm = 74.0,
            timestamp = 2_000L,
            previousTimestamp = 1_000L,
            now = 2_000L,
            attemptId = "attempt-a"
        )

        assertEquals(HeartRateFeatureSnapshot(74.0, 2_000L), plan?.measurement)
    }

    @Test
    fun replayOfOlderOrEqualTimestampCannotOverwriteLatestFeature() {
        val older = HeartRateFeatureCommitPlanner.plan(
            bpm = 74.0,
            timestamp = 1_000L,
            previousTimestamp = 2_000L,
            now = 2_000L,
            attemptId = "older"
        )
        val equal = HeartRateFeatureCommitPlanner.plan(
            bpm = 74.0,
            timestamp = 2_000L,
            previousTimestamp = 2_000L,
            now = 2_000L,
            attemptId = "equal"
        )

        assertNull(older?.measurement)
        assertNull(equal?.measurement)
    }

    @Test
    fun receiptHitReplayMustNotMoveLatestHeartRateBackToOlderSnapshot() {
        val latest = HeartRateFeatureSnapshot(81.0, 2_000L, 1_500L, 11L)
        val replayedReceipt = HeartRateFeatureSnapshot(72.0, 1_000L, 1_500L, 10L)

        assertEquals(
            false,
            HeartRateFeatureCommitPlanner.isStrictlyNewer(
                timestamp = replayedReceipt.timestamp,
                sessionStartedAtMs = replayedReceipt.sessionStartedAtMs,
                producerSequence = replayedReceipt.producerSequence,
                previousTimestamp = latest.timestamp,
                previousSessionStartedAtMs = latest.sessionStartedAtMs,
                previousProducerSequence = latest.producerSequence,
                now = 3_000L
            )
        )
    }

    @Test
    fun receiptHitReplayAllowsStrictlyNewerSequenceAtSameTimestamp() {
        assertEquals(
            true,
            HeartRateFeatureCommitPlanner.isStrictlyNewer(
                timestamp = 2_000L,
                sessionStartedAtMs = 1_500L,
                producerSequence = 11L,
                previousTimestamp = 2_000L,
                previousSessionStartedAtMs = 1_500L,
                previousProducerSequence = 10L,
                now = 3_000L
            )
        )
    }

    @Test
    fun pendingReceiptReplayMustCommitEvenWhenMeasurementIsNotNewer() {
        assertEquals(true, HeartRateFeatureCommitPlanner.shouldPersistReceiptReplay(false, true))
        assertEquals(true, HeartRateFeatureCommitPlanner.shouldPersistReceiptReplay(true, false))
        assertEquals(false, HeartRateFeatureCommitPlanner.shouldPersistReceiptReplay(false, false))
    }

    @Test
    fun receiptReplayCanDurablyRetryPendingRemovalAfterMemoryWasAlreadyCleared() {
        val pending = "receipt-a\nreceipt-b"
        val memoryAfterFailedCommit = RawSnapshotReceiptLedger.remove(pending, "receipt-a")
        val replayRemoval = RawSnapshotReceiptLedger.remove(memoryAfterFailedCommit, "receipt-a")

        assertEquals("receipt-b", replayRemoval)
        assertEquals(true, RawSnapshotReceiptLedger.contains("receipt-a", "receipt-a"))
        assertEquals(true, RawSnapshotReceiptLedger.requiresReplay("receipt-a", replayRemoval, "receipt-a"))
    }

    @Test
    fun rejectedPayloadSkipsReplayAndCanResolveOnlyItsPendingReceipt() {
        assertEquals(
            false,
            RawSnapshotReceiptLedger.shouldReplay("receipt-a", "receipt-a", "receipt-a", false, true)
        )
        assertEquals(
            true,
            RawSnapshotReceiptLedger.shouldReplay("receipt-a", "receipt-a", "receipt-a", false, false)
        )
        assertEquals("receipt-b", RawSnapshotReceiptLedger.pendingAfterRejectedReceipt("receipt-a\nreceipt-b", "receipt-a"))
        assertEquals(null, RawSnapshotReceiptLedger.pendingAfterRejectedReceipt("receipt-b", "receipt-a"))
    }

    @Test
    fun replayCrashAfterDurableCheckpointRetainsItemAndRestartCanAcknowledgeIt() {
        val receipt = "uri-a"
        val durableReceipts = mutableSetOf(receipt)
        var durablePending = mutableSetOf(receipt)
        var deleteRequests = 0

        try {
            DataItemDurabilityCommit.commitThenAcknowledge(
                commitCheckpoint = {
                    durablePending.remove(receipt)
                    true
                },
                afterCommit = { error("simulated process death after durable checkpoint") },
                acknowledgeDataItem = { deleteRequests++ }
            )
        } catch (_: IllegalStateException) {
        }

        assertEquals(true, durableReceipts.contains(receipt))
        assertEquals(false, durablePending.contains(receipt))
        assertEquals(0, deleteRequests)

        val restartedAcknowledgement = DataItemDurabilityCommit.commitThenAcknowledge(
            commitCheckpoint = {
                durablePending.remove(receipt)
                true
            },
            afterCommit = {},
            acknowledgeDataItem = { deleteRequests++ }
        )

        assertEquals(true, restartedAcknowledgement)
        assertEquals(true, durableReceipts.contains(receipt))
        assertEquals(false, durablePending.contains(receipt))
        assertEquals(1, deleteRequests)
    }

    @Test
    fun replayCommitFailureNeverAcknowledgesDataItem() {
        var deleteRequests = 0

        val completed = DataItemDurabilityCommit.commitThenAcknowledge(
            commitCheckpoint = { false },
            afterCommit = { error("must not run after failed commit") },
            acknowledgeDataItem = { deleteRequests++ }
        )

        assertEquals(false, completed)
        assertEquals(0, deleteRequests)
    }

    @Test
    fun receiptReplayAfterPendingRemovalRemainsDeduplicatedAndCannotRewindLatestHeartRate() {
        val receipt = "uri-a"
        val receipts = RawSnapshotReceiptLedger.append("", receipt)
        val pending = RawSnapshotReceiptLedger.appendPending("", receipt).orEmpty()
        val pendingAfterReplay = RawSnapshotReceiptLedger.remove(pending, receipt)

        assertEquals(true, RawSnapshotReceiptLedger.shouldReplay(receipts, pendingAfterReplay, receipt, false, false))

        val replayPlan = HeartRateFeatureCommitPlanner.plan(
            bpm = 62.0,
            timestamp = 1_000L,
            previousTimestamp = 2_000L,
            now = 3_000L,
            attemptId = "stale-replay",
            sessionStartedAtMs = 500L,
            producerSequence = 1L,
            previousSessionStartedAtMs = 1_500L,
            previousProducerSequence = 9L
        )

        assertNotEquals(null, replayPlan)
        assertNull(replayPlan?.measurement)
    }

    @Test
    fun sameTimestampSnapshotAdvancesOnlyWithNewerSequenceOrSession() {
        val nextSequence = HeartRateFeatureCommitPlanner.plan(
            bpm = 75.0,
            timestamp = 2_000L,
            previousTimestamp = 2_000L,
            now = 2_000L,
            attemptId = "next",
            sessionStartedAtMs = 1_500L,
            producerSequence = 11L,
            previousSessionStartedAtMs = 1_500L,
            previousProducerSequence = 10L
        )
        val oldSequence = HeartRateFeatureCommitPlanner.plan(
            bpm = 73.0,
            timestamp = 2_000L,
            previousTimestamp = 2_000L,
            now = 2_000L,
            attemptId = "old",
            sessionStartedAtMs = 1_500L,
            producerSequence = 9L,
            previousSessionStartedAtMs = 1_500L,
            previousProducerSequence = 10L
        )

        assertEquals(HeartRateFeatureSnapshot(75.0, 2_000L, 1_500L, 11L), nextSequence?.measurement)
        assertNull(oldSequence?.measurement)
    }

    @Test
    fun replayDoesNotCreateFeatureFromUnsupportedOrInvalidValues() {
        assertNull(
            RawSnapshotFeatureRecovery.fromSnapshot("whs_heart_rate", false, 74.0, null, 1_000L)
        )
        assertNull(
            RawSnapshotFeatureRecovery.fromSnapshot("whs_heart_rate", true, null, null, 1_000L)
        )
        assertNull(
            RawSnapshotFeatureRecovery.fromSnapshot("whs_heart_rate", true, Double.NaN, null, 1_000L)
        )
        assertNull(
            HeartRateFeatureCommitPlanner.plan(74.0, 2_000L, 1_000L, 2_000L, "")
        )
    }

    @Test
    fun invalidFormulaPulseFallsBackToValidHealthServicesAverage() {
        assertEquals(
            HeartRateFeatureSnapshot(72.0, 1_000L),
            RawSnapshotFeatureRecovery.fromSnapshot("whs_heart_rate", true, Double.NaN, 72.0, 1_000L)
        )
        assertEquals(
            HeartRateFeatureSnapshot(72.0, 1_000L),
            RawSnapshotFeatureRecovery.fromSnapshot("whs_heart_rate", true, 250.0, 72.0, 1_000L)
        )
    }
}
