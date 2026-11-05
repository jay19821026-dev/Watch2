package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataItemDurabilityCommitTest {
    @Test fun failedCommitDoesNotProcessOrAcknowledge() {
        val actions = mutableListOf<String>()
        assertFalse(DataItemDurabilityCommit.commitThenAcknowledge(
            { actions.add("commit"); false }, { actions.add("process") }, { actions.add("ack") }))
        assertEquals(listOf("commit"), actions)
    }

    @Test fun commitExceptionDoesNotAcknowledge() {
        var processed = false
        var acknowledged = false
        assertTrue(runCatching {
            DataItemDurabilityCommit.commitThenAcknowledge(
                { error("storage unavailable") }, { processed = true }, { acknowledged = true })
        }.isFailure)
        assertFalse(processed || acknowledged)
    }

    @Test fun downstreamFailureRetainsTransportItem() {
        var acknowledged = false
        assertTrue(runCatching {
            DataItemDurabilityCommit.commitThenAcknowledge(
                { true }, { error("downstream failed") }, { acknowledged = true })
        }.isFailure)
        assertFalse(acknowledged)
    }

    @Test fun retryMustRecommitBeforeAcknowledging() {
        var attempts = 0
        val actions = mutableListOf<String>()
        val commit = { attempts++; actions.add("commit-$attempts"); attempts == 2 }
        assertFalse(DataItemDurabilityCommit.commitThenAcknowledge(commit, { actions.add("process") }, { actions.add("ack") }))
        assertTrue(DataItemDurabilityCommit.commitThenAcknowledge(commit, { actions.add("process") }, { actions.add("ack") }))
        assertEquals(listOf("commit-1", "commit-2", "process", "ack"), actions)
    }
}
