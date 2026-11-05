package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSnapshotMigrationTest {
    private class Fixture : RawSnapshotMigration.Storage {
        var legacy: Map<String, *> = mapOf("raw" to 1, "float" to Float.fromBits(0x7fc00001))
        var encrypted: Map<String, *>? = null
        var committed = false
        var legacyDeleted = false
        var failure: String? = null
        override fun isCommitted() = committed
        override fun encryptedExists() = encrypted != null
        override fun readLegacy() = legacy
        override fun readEncrypted(): Map<String, *> {
            check(failure != "read")
            return checkNotNull(encrypted)
        }
        override fun writeEncrypted(values: Map<String, *>) {
            encrypted = values
            if (failure == "source_changed") legacy = mapOf("raw" to 10)
            check(failure != "write")
        }
        override fun commitMarker() {
            check(failure != "marker")
            if (failure != "marker_not_saved") committed = true
        }
        override fun deleteLegacy(): Boolean {
            check(committed)
            check(failure != "cleanup_exception")
            if (failure == "cleanup") return false
            legacyDeleted = true
            return true
        }
    }

    @Test fun copiesVerifiesCommitsThenDeletes() {
        val fixture = Fixture()
        val result = RawSnapshotMigration.open(fixture)
        assertTrue(fixture.committed && fixture.legacyDeleted && result.legacyCleanupComplete)
        assertEquals(1, result.values["raw"])
        assertEquals(0x7fc00001, (result.values["float"] as Float).toRawBits())
    }

    @Test fun incompleteWriteOrMarkerCanResumeWithoutDeletingEvidence() {
        listOf("write", "marker", "marker_not_saved").forEach { phase ->
            val fixture = Fixture().apply { failure = phase }
            assertTrue(runCatching { RawSnapshotMigration.open(fixture) }.isFailure)
            assertFalse(fixture.legacyDeleted)
            fixture.failure = null
            assertTrue(RawSnapshotMigration.open(fixture).legacyCleanupComplete)
        }
    }

    @Test fun mismatchedDestinationIsNotOverwrittenOrApproved() {
        val fixture = Fixture().apply { encrypted = mapOf("raw" to 2) }
        assertTrue(runCatching { RawSnapshotMigration.open(fixture) }.isFailure)
        assertFalse(fixture.committed || fixture.legacyDeleted)
        assertEquals(2, fixture.encrypted?.get("raw"))
    }

    @Test fun missingOrUnreadableCommittedDestinationPreservesLegacy() {
        listOf(true, false).forEach { missing ->
            val fixture = Fixture().apply {
                committed = true
                if (!missing) { encrypted = legacy; failure = "read" }
            }
            assertTrue(runCatching { RawSnapshotMigration.open(fixture) }.isFailure)
            assertFalse(fixture.legacyDeleted)
        }
    }

    @Test fun cleanupFailureIsExplicitAndRetryable() {
        val fixture = Fixture().apply { failure = "cleanup" }
        assertFalse(RawSnapshotMigration.open(fixture).legacyCleanupComplete)
        fixture.failure = null
        assertTrue(RawSnapshotMigration.open(fixture).legacyCleanupComplete)
    }

    @Test fun cleanupExceptionRetainsCommittedEvidenceAndCanRetry() {
        val fixture = Fixture().apply { failure = "cleanup_exception" }
        assertTrue(runCatching { RawSnapshotMigration.open(fixture) }.isFailure)
        assertTrue(fixture.committed)
        assertFalse(fixture.legacyDeleted)
        assertEquals(1, fixture.encrypted?.get("raw"))
        fixture.failure = null
        assertTrue(RawSnapshotMigration.open(fixture).legacyCleanupComplete)
    }

    @Test fun committedDestinationDoesNotReimportStaleLegacy() {
        val fixture = Fixture().apply { committed = true; encrypted = mapOf("raw" to 9) }
        assertEquals(9, RawSnapshotMigration.open(fixture).values["raw"])
    }

    @Test fun sourceUpdatedDuringCopyIsNotDeletedOrCommitted() {
        val fixture = Fixture().apply { failure = "source_changed" }
        assertTrue(runCatching { RawSnapshotMigration.open(fixture) }.isFailure)
        assertFalse(fixture.committed || fixture.legacyDeleted)
        assertEquals(10, fixture.legacy["raw"])
        assertEquals(1, fixture.encrypted?.get("raw"))
    }
}
