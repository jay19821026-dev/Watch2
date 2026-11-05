package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSensorSuiteCoveragePolicyTest {
    @Test
    fun doesNotReplaceAnAttemptedZeroEventResultWithCoveragePlaceholder() {
        assertFalse(
            WearSensorSuiteCoveragePolicy.shouldEmit(
                "mi_spo2",
                setOf("mi_spo2")
            )
        )
    }

    @Test
    fun emitsCoverageResultWhenChannelWasNeverFinalized() {
        assertTrue(WearSensorSuiteCoveragePolicy.shouldEmit("lifeq_ott", emptySet()))
    }
}
