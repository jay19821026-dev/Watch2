package com.jaysato.xiaomihealthbridge

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchCompositeInputPolicyTest {
    private val now = Instant.parse("2026-10-06T12:00:00Z")

    @Test fun permitsRecentCrossOriginInputsWhenTimesArePaired() {
        assertTrue(
            ResearchCompositeInputPolicy.canPair(
                now.minus(8, ChronoUnit.HOURS),
                now.minus(7, ChronoUnit.HOURS),
                "com.jaysato.xiaomihealthbridge",
                "com.xiaomi.wearable",
                now
            )
        )
    }

    @Test fun rejectsMissingOriginFutureAndUnpairedInputs() {
        assertFalse(ResearchCompositeInputPolicy.canPair(now.minusSeconds(60), now, null, "sleep", now))
        assertFalse(ResearchCompositeInputPolicy.canPair(now.plusSeconds(1), now, "hrv", "sleep", now))
        assertFalse(
            ResearchCompositeInputPolicy.canPair(
                now.minus(35, ChronoUnit.HOURS),
                now,
                "hrv",
                "sleep",
                now
            )
        )
    }
}
