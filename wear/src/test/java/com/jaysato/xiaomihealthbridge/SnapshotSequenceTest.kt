package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotSequenceTest {
    @Test
    fun sequenceIsStrictlyIncreasingEvenWhenClockRepeatsOrMovesBackwards() {
        val readings = ArrayDeque(listOf(100L, 100L, 99L, 105L))
        val sequence = SnapshotSequence { readings.removeFirst() }

        assertEquals(100L, sequence.next())
        assertEquals(101L, sequence.next())
        assertEquals(102L, sequence.next())
        assertEquals(105L, sequence.next())
    }
}
