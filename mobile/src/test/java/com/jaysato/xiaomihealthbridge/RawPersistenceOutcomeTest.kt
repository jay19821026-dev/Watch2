package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawPersistenceOutcomeTest {
    @Test fun olderFailureCannotReplaceNewerSuccessfulOutcome() {
        val outcomes = RawPersistenceOutcome()
        val first = outcomes.begin()
        val newest = outcomes.begin()
        outcomes.complete(newest, null)
        outcomes.complete(first, "IOException")
        assertNull(outcomes.failure)
    }

    @Test fun pendingRetryDoesNotHidePriorFailure() {
        val outcomes = RawPersistenceOutcome()
        outcomes.complete(outcomes.begin(), "IOException")
        val retry = outcomes.begin()
        assertEquals("IOException", outcomes.failure)
        outcomes.complete(retry, "SecurityException")
        assertEquals("SecurityException", outcomes.failure)
    }

    @Test fun olderSuccessfulWriteCannotEraseNewerRejection() {
        val outcomes = RawPersistenceOutcome()
        val first = outcomes.begin()
        val rejected = outcomes.begin()
        outcomes.complete(rejected, "RejectedExecutionException")
        outcomes.complete(first, null)
        assertEquals("RejectedExecutionException", outcomes.failure)
        outcomes.complete(outcomes.begin(), null)
        assertNull(outcomes.failure)
    }
}
